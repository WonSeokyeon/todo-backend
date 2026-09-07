package com.example.todoapp.service;

import com.example.todoapp.domain.StorageType;
import com.example.todoapp.exception.BusinessException;
import com.example.todoapp.exception.ErrorCode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 기반 첨부 저장소 (Phase 13).
 *
 * <p>계약은 Phase 12에서 이미 확정했다 — StorageService 인터페이스만 지키면 AttachmentService·
 * 컨트롤러·프론트 코드는 한 줄도 바뀌지 않는다. 브라우저가 presigned URL로 S3와 직접 주고받으므로
 * 서버는 파일 바이트를 절대 거치지 않는다(LocalStorageService의 store/loadAsResource에 대응하는
 * 메서드가 이 클래스에는 없는 이유).
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration uploadUrlExpiry;
    private final Duration viewUrlExpiry;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.upload-url-expiry-minutes:10}") long uploadExpiryMinutes,
            @Value("${app.storage.view-url-expiry-minutes:30}") long viewExpiryMinutes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.uploadUrlExpiry = Duration.ofMinutes(uploadExpiryMinutes);
        this.viewUrlExpiry = Duration.ofMinutes(viewExpiryMinutes);
    }

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }

    @Override
    public String createUploadUrl(Long attachmentId, String storageKey, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(uploadUrlExpiry)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    @Override
    public String createViewUrl(Long attachmentId, String storageKey) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(viewUrlExpiry)
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    @Override
    public long verifyUploaded(String storageKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .build())
                    .contentLength();
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.ATTACHMENT_INVALID_STATE, "업로드된 파일이 없습니다.");
        }
    }

    @Override
    public void delete(String storageKey) {
        // S3 DeleteObject는 키가 없어도 예외를 던지지 않는다 — 정리 배치 재실행에도 안전하다.
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build());
    }
}
