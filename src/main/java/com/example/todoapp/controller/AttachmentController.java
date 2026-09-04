package com.example.todoapp.controller;

import com.example.todoapp.config.AttachmentTokenPurpose;
import com.example.todoapp.config.JwtTokenProvider;
import com.example.todoapp.domain.User;
import com.example.todoapp.dto.ApiResponse;
import com.example.todoapp.dto.AttachmentPresignRequest;
import com.example.todoapp.dto.AttachmentPresignResponse;
import com.example.todoapp.dto.AttachmentResponse;
import com.example.todoapp.dto.AttachmentUrlResponse;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import com.example.todoapp.service.AttachmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 첨부 파일 API (CLAUDE.md 5장).
 *
 * <p>upload·raw 두 엔드포인트만 Bearer가 아니라 쿼리의 서명 토큰으로 인증한다.
 * SecurityConfig에서 permitAll로 열려 있고 여기서 직접 토큰을 검증한다 — /auth/refresh와 같은 패턴이다.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/presign")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<AttachmentPresignResponse>> presign(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AttachmentPresignRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.presign(user, request)));
    }

    /**
     * 파일 수신 (로컬 전용). 서명 토큰으로 인증한다.
     *
     * <p>본문을 스트림으로 처리한다 — 메모리에 전체를 올리지 않는다.
     */
    @PutMapping("/{id}/upload")
    public ResponseEntity<ApiResponse<Void>> upload(
            @PathVariable Long id,
            @RequestParam String token,
            HttpServletRequest request
    ) {
        requireAttachmentToken(token, AttachmentTokenPurpose.UPLOAD, id);

        try {
            attachmentService.storeUpload(
                    id,
                    request.getContentType(),
                    request.getInputStream(),
                    request.getContentLengthLong());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "업로드 본문을 읽을 수 없습니다.");
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/complete")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<AttachmentResponse>> complete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.complete(user.getId(), id)));
    }

    /** 조회 URL 일괄 발급. 본문의 이미지 수만큼 요청이 늘어나지 않게 한다. */
    @GetMapping("/urls")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<List<AttachmentUrlResponse>>> viewUrls(
            @AuthenticationPrincipal User user,
            @RequestParam List<Long> ids
    ) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.getViewUrls(user.getId(), ids)));
    }

    /**
     * 파일 스트림 (로컬 전용). 서명 토큰으로 인증한다.
     *
     * <p>바이너리라 ApiResponse로 감쌀 수 없다 — 공통 응답 포맷의 예외다(CLAUDE.md 5장).
     * img 태그가 직접 호출하므로 Authorization 헤더를 실을 수 없어 토큰을 쿼리로 받는다.
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<Resource> raw(
            @PathVariable Long id,
            @RequestParam String token
    ) {
        requireAttachmentToken(token, AttachmentTokenPurpose.VIEW, id);

        AttachmentService.ViewPayload payload = attachmentService.loadForView(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                // 토큰 만료 이내에서만 캐시한다. 다른 사용자와 공유되면 안 되므로 private다.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(payload.resource());
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        attachmentService.delete(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 서명 토큰을 검증하고 경로의 id와 일치하는지 확인한다.
     * 토큰이 가리키는 첨부와 URL의 첨부가 다르면 다른 첨부에 접근하려는 시도다.
     */
    private void requireAttachmentToken(String token, AttachmentTokenPurpose purpose, Long pathId) {
        Long tokenAttachmentId = jwtTokenProvider.parseAttachmentToken(token, purpose);
        if (!tokenAttachmentId.equals(pathId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
