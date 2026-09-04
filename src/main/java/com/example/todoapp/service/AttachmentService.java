package com.example.todoapp.service;

import com.example.todoapp.domain.Attachment;
import com.example.todoapp.domain.AttachmentRepository;
import com.example.todoapp.domain.Todo;
import com.example.todoapp.domain.User;
import com.example.todoapp.dto.AttachmentPresignRequest;
import com.example.todoapp.dto.AttachmentPresignResponse;
import com.example.todoapp.dto.AttachmentResponse;
import com.example.todoapp.dto.AttachmentUrlResponse;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첨부 파일의 검증·상태 전환·권한 확인을 담당한다 (CLAUDE.md 5장).
 *
 * <p>StorageService 인터페이스에만 의존하고 어떤 구현체인지 알지 못한다.
 * 다만 upload/raw 엔드포인트는 로컬 전용이라 LocalStorageService를 선택적으로 받는다 —
 * S3 모드에서는 브라우저가 presigned URL로 직접 주고받으므로 서버를 거치지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    /** Content-Type → 저장 시 사용할 확장자. 원본 파일명의 확장자는 믿지 않는다. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final ObjectProvider<LocalStorageService> localStorageProvider;

    @Value("${app.upload.max-file-size}")
    private long maxFileSize;

    @Value("${app.upload.allowed-content-types}")
    private String allowedContentTypes;

    /** 업로드 URL을 발급하고 TEMP 행을 만든다. 아직 파일 실체는 없다. */
    @Transactional
    public AttachmentPresignResponse presign(User user, AttachmentPresignRequest request) {
        String contentType = normalizeContentType(request.contentType());
        validateContentType(contentType);
        validateSize(request.fileSize());

        String storageKey = buildStorageKey(user.getId(), contentType);
        Attachment attachment = attachmentRepository.save(Attachment.createTemp(
                user, storageService.getType(), storageKey,
                request.filename(), contentType, request.fileSize()));

        String uploadUrl = storageService.createUploadUrl(attachment.getId(), storageKey, contentType);
        return new AttachmentPresignResponse(attachment.getId(), uploadUrl, storageKey);
    }

    /**
     * 로컬 업로드 수신. 서명 토큰으로 인증되므로 사용자 컨텍스트가 없다 —
     * 토큰이 presign 시점에 업로더에게만 발급됐다는 점이 권한의 근거다.
     */
    @Transactional(readOnly = true)
    public void storeUpload(Long attachmentId, String contentType, InputStream body, long declaredLength) {
        Attachment attachment = getActive(attachmentId);
        requireLocalStorage(attachment);

        // Content-Length 선검사 — 명백히 큰 요청은 본문을 읽기 전에 끊는다.
        if (declaredLength > maxFileSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 너무 큽니다.");
        }
        // 저장된 Content-Type을 기준으로 검증한다. 업로드 요청 헤더는 참고만 한다.
        localStorage().store(attachment.getStorageKey(), attachment.getContentType(), body, maxFileSize);
    }

    /** 업로드 확정 — 파일이 실제로 있는지 확인하고 실측 크기로 갱신한다. */
    @Transactional
    public AttachmentResponse complete(Long userId, Long attachmentId) {
        Attachment attachment = getOwned(userId, attachmentId);
        long actualSize = storageService.verifyUploaded(attachment.getStorageKey());

        if (actualSize > maxFileSize) {
            // 스트림 검사를 우회해 들어온 경우의 마지막 방어선.
            storageService.delete(attachment.getStorageKey());
            attachment.softDelete();
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 너무 큽니다.");
        }

        attachment.completeUpload(actualSize);
        String viewUrl = storageService.createViewUrl(attachment.getId(), attachment.getStorageKey());
        return AttachmentResponse.of(attachment, viewUrl);
    }

    /**
     * 조회 URL을 일괄 발급한다.
     * 타인 소유·삭제된 id는 조용히 빠진다 — 404를 내면 남의 첨부 존재 여부가 드러난다.
     */
    @Transactional(readOnly = true)
    public List<AttachmentUrlResponse> getViewUrls(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return attachmentRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .filter(attachment -> attachment.isOwnedBy(userId))
                .map(attachment -> new AttachmentUrlResponse(
                        attachment.getId(),
                        storageService.createViewUrl(attachment.getId(), attachment.getStorageKey())))
                .toList();
    }

    /** 조회 스트림. 서명 토큰으로 인증되므로 사용자 컨텍스트가 없다. */
    @Transactional(readOnly = true)
    public ViewPayload loadForView(Long attachmentId) {
        Attachment attachment = getActive(attachmentId);
        requireLocalStorage(attachment);
        return new ViewPayload(
                localStorage().loadAsResource(attachment.getStorageKey()),
                attachment.getContentType(),
                attachment.getOriginalFilename());
    }

    /**
     * Soft Delete. 물리 파일은 즉시 지우지 않는다 —
     * 유예 기간 뒤 정리 배치가 삭제한다(휴지통 복원 여지를 남긴다, CLAUDE.md 4장).
     */
    @Transactional
    public void delete(Long userId, Long attachmentId) {
        getOwned(userId, attachmentId).softDelete();
    }

    /** 소유권까지 확인한다. 타인 소유는 존재하지 않는 것과 동일하게 404다. */
    private Attachment getOwned(Long userId, Long attachmentId) {
        Attachment attachment = getActive(attachmentId);
        if (!attachment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return attachment;
    }

    private Attachment getActive(Long attachmentId) {
        return attachmentRepository.findByIdAndDeletedAtIsNull(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    }

    /**
     * 행에 기록된 저장소 종류가 현재 활성 구현체와 다르면 명시적으로 실패시킨다.
     * 조용히 넘어가면 엉뚱한 경로를 읽거나 쓰게 된다 (CLAUDE.md 4장).
     */
    private void requireLocalStorage(Attachment attachment) {
        if (attachment.getStorageType() != storageService.getType()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "현재 활성 저장소와 다른 곳에 저장된 첨부입니다.");
        }
        if (localStorageProvider.getIfAvailable() == null) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
    }

    private LocalStorageService localStorage() {
        LocalStorageService local = localStorageProvider.getIfAvailable();
        if (local == null) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return local;
    }

    private String normalizeContentType(String contentType) {
        // "image/png; charset=binary" 같은 파라미터를 떼고 소문자로 맞춘다.
        int separator = contentType.indexOf(';');
        String base = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return base.trim().toLowerCase();
    }

    private void validateContentType(String contentType) {
        boolean allowed = List.of(allowedContentTypes.split(",")).stream()
                .map(String::trim)
                .anyMatch(type -> type.equalsIgnoreCase(contentType));
        if (!allowed || !EXTENSIONS.containsKey(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 이미지 형식입니다. (JPEG, PNG, GIF, WebP만 가능)");
        }
    }

    private void validateSize(long fileSize) {
        if (fileSize > maxFileSize) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "파일이 너무 큽니다. (최대 %dMB)".formatted(maxFileSize / (1024 * 1024)));
        }
    }

    /** todos/{userId}/{yyyy}/{MM}/{uuid}.{ext} — 로컬·S3 동일 규칙 (CLAUDE.md 4장). */
    private String buildStorageKey(Long userId, String contentType) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "todos/%d/%s/%s/%s.%s".formatted(
                userId,
                today.format(YEAR),
                today.format(MONTH),
                UUID.randomUUID(),
                EXTENSIONS.get(contentType));
    }

    /** 파일 스트림 응답에 필요한 정보 묶음. */
    public record ViewPayload(Resource resource, String contentType, String filename) {
    }

    /**
     * 할 일 본문과 첨부의 연결 상태를 맞춘다. TodoService가 저장 직전에 호출한다.
     *
     * <p>본문에 남아 있는 첨부는 LINKED로 전환하고, 이 할 일에 연결돼 있었으나
     * 본문에서 사라진 첨부는 Soft Delete한다. 물리 파일은 지우지 않는다 —
     * 유예 기간 뒤 정리 배치가 삭제한다 (CLAUDE.md 4장).
     *
     * @param attachmentIds 정화가 끝난 본문에서 수집한 첨부 ID
     * @throws BusinessException 요청한 첨부 중 하나라도 없거나 타인 소유이면 404
     */
    @Transactional
    public void syncWithTodo(Long userId, Todo todo, Set<Long> attachmentIds) {
        if (!attachmentIds.isEmpty()) {
            List<Attachment> found = attachmentRepository.findByIdInAndDeletedAtIsNull(attachmentIds);
            // 없는 id가 섞여 있거나 남의 첨부가 섞여 있으면 통째로 거부한다.
            // 일부만 연결하면 본문에는 남아 있는데 조회되지 않는 이미지가 생긴다.
            boolean allOwned = found.size() == attachmentIds.size()
                    && found.stream().allMatch(attachment -> attachment.isOwnedBy(userId));
            if (!allOwned) {
                throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
            }
            found.forEach(attachment -> attachment.link(todo));
        }

        // 본문에서 사라진 첨부를 정리한다. 위에서 link한 것들은 attachmentIds에 있으므로 제외된다.
        attachmentRepository.findByTodoIdAndDeletedAtIsNull(todo.getId()).stream()
                .filter(attachment -> !attachmentIds.contains(attachment.getId()))
                .forEach(Attachment::softDelete);
    }
}
