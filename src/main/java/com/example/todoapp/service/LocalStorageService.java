package com.example.todoapp.service;

import com.example.todoapp.config.AttachmentTokenPurpose;
import com.example.todoapp.config.JwtTokenProvider;
import com.example.todoapp.domain.StorageType;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 로컬 디스크 기반 첨부 저장소 (Phase 12).
 *
 * <p>업로드·조회 URL 모두 서명 토큰을 쿼리에 붙인 백엔드 엔드포인트를 가리킨다.
 * Authorization 헤더를 쓰지 않는 이유는 Phase 13의 S3 presigned URL과 호출 방식을
 * 맞추기 위해서다 — 프론트가 로컬인지 S3인지 몰라도 되게 한다 (CLAUDE.md 5장).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

    private final JwtTokenProvider jwtTokenProvider;
    private final Path baseDir;
    private final String baseUrl;

    public LocalStorageService(
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.storage.local.base-dir}") String baseDir,
            @Value("${app.storage.local.base-url}") String baseUrl) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        // 뒤에 슬래시가 있으면 URL이 //api/v1... 이 된다.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 기동 시 업로드 디렉토리를 만들어 둔다. 없으면 첫 업로드가 실패한다. */
    @PostConstruct
    void prepareBaseDir() {
        try {
            Files.createDirectories(baseDir);
            log.info("첨부 로컬 저장 디렉토리: {}", baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("첨부 저장 디렉토리를 만들 수 없습니다: " + baseDir, e);
        }
    }

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    @Override
    public String createUploadUrl(Long attachmentId, String storageKey, String contentType) {
        String token = jwtTokenProvider.generateAttachmentToken(attachmentId, AttachmentTokenPurpose.UPLOAD);
        return "%s/api/v1/attachments/%d/upload?token=%s".formatted(baseUrl, attachmentId, encode(token));
    }

    @Override
    public String createViewUrl(Long attachmentId, String storageKey) {
        String token = jwtTokenProvider.generateAttachmentToken(attachmentId, AttachmentTokenPurpose.VIEW);
        return "%s/api/v1/attachments/%d/raw?token=%s".formatted(baseUrl, attachmentId, encode(token));
    }

    @Override
    public long verifyUploaded(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_INVALID_STATE, "업로드된 파일이 없습니다.");
        }
        try {
            return Files.size(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일 크기를 읽을 수 없습니다.");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException e) {
            // 정리 배치가 재실행될 수 있으므로 실패를 전파하지 않는다. 로그만 남긴다.
            log.warn("첨부 파일 삭제 실패: {}", storageKey, e);
        }
    }

    /**
     * 요청 본문 스트림을 파일로 기록한다. 로컬 전용이라 인터페이스에는 없다
     * (S3는 브라우저가 presigned URL로 직접 PUT하므로 서버를 거치지 않는다).
     *
     * <p>크기 제한을 스트림 복사 중에도 센다. Content-Length는 클라이언트가 보낸 값이라
     * 실제 본문 길이와 다를 수 있기 때문이다. 초과하면 쓰다 만 파일을 지운다.
     *
     * @return 실제로 기록된 바이트 수
     */
    public long store(String storageKey, String declaredContentType, InputStream in, long maxBytes) {
        Path target = resolveSafe(storageKey);
        if (Files.exists(target)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_INVALID_STATE, "이미 업로드된 첨부입니다.");
        }

        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "저장 경로를 만들 수 없습니다.");
        }

        try {
            // 헤더를 먼저 읽어 형식을 확인한다. 통과한 뒤에야 파일을 만든다.
            byte[] header = in.readNBytes(ImageSignature.HEADER_SIZE);
            if (!ImageSignature.matches(declaredContentType, header)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "이미지 파일이 아니거나 형식이 선언한 것과 다릅니다.");
            }
            return writeStream(target, header, in, maxBytes);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일을 저장할 수 없습니다.");
        } catch (BusinessException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /** 조회 엔드포인트가 스트림으로 내려보낼 리소스를 만든다. 로컬 전용이다. */
    public Resource loadAsResource(String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new FileSystemResource(target);
    }

    private long writeStream(Path target, byte[] header, InputStream in, long maxBytes) throws IOException {
        long written = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            out.write(header);
            written += header.length;

            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > maxBytes) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 너무 큽니다.");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    /**
     * storageKey를 baseDir 하위의 실제 경로로 바꾼다.
     *
     * <p>정규화 후 baseDir 하위인지 반드시 확인한다 — ../ 를 넣은 키를 그대로 resolve하면
     * 서버의 임의 파일을 읽거나 덮어쓸 수 있다.
     */
    private Path resolveSafe(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 저장 경로입니다.");
        }
        Path target = baseDir.resolve(storageKey).normalize();
        if (!target.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 저장 경로입니다.");
        }
        return target;
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            log.warn("쓰다 만 파일 정리 실패: {}", target);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
