package com.example.todoapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * HtmlSanitizer 단위 테스트. 스프링 컨텍스트가 필요 없다.
 *
 * <p>이미지 첨부(Phase 12)의 핵심 회귀 방지선이다 — img가 화이트리스트에 없으면
 * 업로드는 성공해도 저장 시점에 태그가 통째로 제거되어 "이미지만 조용히 사라지는" 결함이 난다.
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Nested
    @DisplayName("이미지 첨부")
    class ImageAttachment {

        @Test
        @DisplayName("img 태그와 data-attachment-id는 살아남는다")
        void keepsImgWithAttachmentId() {
            String result = sanitizer.sanitize("<p>회의</p><img data-attachment-id=\"42\" alt=\"스크린샷\">");

            assertThat(result).contains("<img");
            assertThat(result).contains("data-attachment-id=\"42\"");
            assertThat(result).contains("alt=\"스크린샷\"");
        }

        @Test
        @DisplayName("src는 제거된다 — 만료 URL이 DB에 저장되지 않도록")
        void removesSrc() {
            String result = sanitizer.sanitize(
                    "<img data-attachment-id=\"42\" src=\"https://example.com/a.png?token=abc\">");

            assertThat(result).contains("data-attachment-id=\"42\"");
            assertThat(result).doesNotContain("src");
            assertThat(result).doesNotContain("token=abc");
        }

        @Test
        @DisplayName("base64 data URI도 src와 함께 제거된다")
        void removesBase64DataUri() {
            String result = sanitizer.sanitize(
                    "<img data-attachment-id=\"7\" src=\"data:image/png;base64,iVBORw0KGgo=\">");

            assertThat(result).doesNotContain("base64");
            assertThat(result).doesNotContain("data:image");
        }

        @Test
        @DisplayName("onerror 등 이벤트 핸들러 속성은 제거된다")
        void removesEventHandlers() {
            String result = sanitizer.sanitize(
                    "<img data-attachment-id=\"1\" onerror=\"alert(1)\" onload=\"alert(2)\">");

            assertThat(result).doesNotContain("onerror");
            assertThat(result).doesNotContain("onload");
            assertThat(result).doesNotContain("alert");
        }

        @Test
        @DisplayName("허용하지 않은 다른 data-* 속성은 제거된다")
        void removesOtherDataAttributes() {
            String result = sanitizer.sanitize("<img data-attachment-id=\"1\" data-evil=\"x\">");

            assertThat(result).contains("data-attachment-id=\"1\"");
            assertThat(result).doesNotContain("data-evil");
        }
    }

    @Nested
    @DisplayName("첨부 ID 수집")
    class CollectAttachmentIds {

        @Test
        @DisplayName("본문의 첨부 ID를 순서대로 모은다")
        void collectsInOrder() {
            Set<Long> ids = sanitizer.collectAttachmentIds(
                    "<img data-attachment-id=\"3\"><p>사이</p><img data-attachment-id=\"1\">");

            assertThat(ids).containsExactly(3L, 1L);
        }

        @Test
        @DisplayName("중복은 한 번만 담는다")
        void deduplicates() {
            Set<Long> ids = sanitizer.collectAttachmentIds(
                    "<img data-attachment-id=\"5\"><img data-attachment-id=\"5\">");

            assertThat(ids).containsExactly(5L);
        }

        @Test
        @DisplayName("숫자가 아닌 값은 건너뛴다")
        void skipsNonNumeric() {
            Set<Long> ids = sanitizer.collectAttachmentIds(
                    "<img data-attachment-id=\"abc\"><img data-attachment-id=\"9\">");

            assertThat(ids).containsExactly(9L);
        }

        @Test
        @DisplayName("본문이 비어 있으면 빈 집합을 반환한다")
        void handlesEmpty() {
            assertThat(sanitizer.collectAttachmentIds(null)).isEmpty();
            assertThat(sanitizer.collectAttachmentIds("")).isEmpty();
            assertThat(sanitizer.collectAttachmentIds("<p>이미지 없음</p>")).isEmpty();
        }
    }

    @Nested
    @DisplayName("기존 정화 규칙 회귀 방지")
    class ExistingRules {

        @Test
        @DisplayName("script 태그는 제거된다")
        void removesScript() {
            String result = sanitizer.sanitize("<p>안녕</p><script>alert(1)</script>");

            assertThat(result).contains("<p>안녕</p>");
            assertThat(result).doesNotContain("script");
        }

        @Test
        @DisplayName("a 태그에 rel·target이 강제 주입된다")
        void enforcesLinkAttributes() {
            String result = sanitizer.sanitize("<a href=\"https://example.com\">링크</a>");

            assertThat(result).contains("rel=\"noopener noreferrer\"");
            assertThat(result).contains("target=\"_blank\"");
        }

        @Test
        @DisplayName("javascript: 스킴 링크는 차단된다")
        void blocksJavascriptScheme() {
            String result = sanitizer.sanitize("<a href=\"javascript:alert(1)\">클릭</a>");

            assertThat(result).doesNotContain("javascript:");
        }

        @Test
        @DisplayName("화이트리스트에 없는 태그(u, s, h1, table)는 제거된다")
        void removesDisallowedTags() {
            String result = sanitizer.sanitize("<u>밑줄</u><s>취소</s><h1>제목</h1><table><tr><td>표</td></tr></table>");

            assertThat(result).doesNotContain("<u>");
            assertThat(result).doesNotContain("<s>");
            assertThat(result).doesNotContain("<h1>");
            assertThat(result).doesNotContain("<table>");
            // 텍스트 자체는 남는다 (Jsoup.clean의 기본 동작)
            assertThat(result).contains("밑줄");
        }

        @Test
        @DisplayName("pre 블록의 공백과 줄바꿈이 보존된다")
        void preservesPreFormatting() {
            String result = sanitizer.sanitize("<pre><code>line1\n    line2</code></pre>");

            assertThat(result).contains("line1\n    line2");
        }

        @Test
        @DisplayName("null·공백 입력은 그대로 반환한다")
        void passesThroughBlank() {
            assertThat(sanitizer.sanitize(null)).isNull();
            assertThat(sanitizer.sanitize("")).isEmpty();
        }
    }
}
