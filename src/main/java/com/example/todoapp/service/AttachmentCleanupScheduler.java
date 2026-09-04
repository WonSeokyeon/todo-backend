package com.example.todoapp.service;

import com.example.todoapp.domain.Attachment;
import com.example.todoapp.domain.AttachmentRepository;
import com.example.todoapp.domain.AttachmentStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첨부 파일 정리 배치 (CLAUDE.md 4장).
 *
 * <p>두 가지를 처리한다.
 * <ul>
 *   <li>업로드하다 만 고아 파일 — TEMP 상태로 오래 남은 것</li>
 *   <li>Soft Delete 후 유예 기간이 지난 것 — 파일만 지우고 행은 남긴다</li>
 * </ul>
 *
 * <p>행을 물리 삭제하지 않는 이유는 부모 CLAUDE.md 절대규칙 5("삭제는 모두 Soft Delete,
 * 물리 삭제 금지") 때문이다. 디스크를 차지하는 것은 파일이지 행이 아니므로,
 * 파일만 지우면 비용 문제는 해결되고 감사 기록은 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupScheduler {

    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;

    @Value("${app.attachment.temp-retention-hours}")
    private long tempRetentionHours;

    @Value("${app.attachment.deleted-retention-days}")
    private long deletedRetentionDays;

    /** 기본값은 매일 새벽 4시. 운영 부하가 적은 시간대로 잡는다. */
    @Scheduled(cron = "${app.attachment.cleanup-cron}")
    public void cleanUp() {
        int orphans = cleanUpOrphans();
        int expired = cleanUpDeletedFiles();
        if (orphans > 0 || expired > 0) {
            log.info("첨부 정리 완료 — 고아 {}건, 파일 삭제 {}건", orphans, expired);
        }
    }

    /**
     * presign만 하고 할 일을 저장하지 않은 첨부를 정리한다.
     * 파일을 지우고 행은 Soft Delete한다.
     */
    @Transactional
    public int cleanUpOrphans() {
        LocalDateTime threshold = now().minusHours(tempRetentionHours);
        List<Attachment> orphans = attachmentRepository
                .findByStatusAndDeletedAtIsNullAndCreatedAtBefore(AttachmentStatus.TEMP, threshold);

        for (Attachment attachment : orphans) {
            storageService.delete(attachment.getStorageKey());
            attachment.softDelete();
        }
        return orphans.size();
    }

    /**
     * Soft Delete 후 유예 기간이 지난 첨부의 물리 파일을 지운다. 행은 그대로 둔다.
     *
     * <p>유예를 두는 이유는 향후 휴지통(PRD 9장) 복원 여지 때문이다. 삭제 즉시 바이트를
     * 버리면 행을 되살려도 이미지가 깨진다.
     */
    @Transactional(readOnly = true)
    public int cleanUpDeletedFiles() {
        LocalDateTime threshold = now().minusDays(deletedRetentionDays);
        List<Attachment> expired = attachmentRepository.findByDeletedAtNotNullAndDeletedAtBefore(threshold);

        // storageService.delete는 파일이 없어도 조용히 넘어가므로 재실행해도 안전하다.
        expired.forEach(attachment -> storageService.delete(attachment.getStorageKey()));
        return expired.size();
    }

    private LocalDateTime now() {
        // 저장된 타임스탬프가 UTC이므로 비교 기준도 UTC여야 한다 (CLAUDE.md 4장).
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
