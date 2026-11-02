package com.example.todoapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트 빈 (Phase 13). app.storage.type=s3 일 때만 등록한다.
 *
 * <p>자격증명은 기본 체인(DefaultCredentialsProvider)에 맡긴다 — 로컬은 {@code aws configure}로 만든
 * {@code ~/.aws/credentials}를 찾는다. 키를 코드나
 * 설정 파일에 넣지 않는다 (CLAUDE.md 5장, 부모 CLAUDE.md 절대규칙 9).
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${app.storage.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${app.storage.s3.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
