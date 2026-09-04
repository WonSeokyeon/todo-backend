package com.example.todoapp.service;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Tiptap 본문 HTML을 저장 전에 정화한다 (CLAUDE.md 6장 XSS 이중 방어의 서버 측).
 * 툴바·Tiptap 확장·프론트 DOMPurify와 항상 같은 태그 집합을 유지해야 한다.
 */
@Component
public class HtmlSanitizer {

    /** 첨부 이미지를 식별하는 유일한 속성. src는 저장하지 않으므로 이 값만 왕복한다. */
    public static final String ATTACHMENT_ID_ATTR = "data-attachment-id";

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "em", "h2", "h3", "ul", "ol", "li", "a", "code", "pre", "blockquote", "img")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .addEnforcedAttribute("a", "target", "_blank")
            // img는 data-attachment-id와 alt만 허용한다. src를 넣지 않는 것이 의도다 —
            // 만료되는 조회 URL이 DB에 박히는 것과 임의 외부 URL 삽입을 동시에 막는다(CLAUDE.md 6장).
            .addAttributes("img", ATTACHMENT_ID_ATTR, "alt");

    // 기본 pretty-print는 블록 요소를 재포맷해 pre 블록의 공백·줄바꿈을 망가뜨린다.
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, "", SAFELIST, OUTPUT_SETTINGS);
    }

    /**
     * 본문에 남아 있는 첨부 ID를 순서대로 수집한다.
     * 할 일 저장 시 어떤 첨부를 LINKED로 바꾸고 어떤 것을 정리할지 판단하는 데 쓴다.
     *
     * <p>이미 정화를 거친 HTML을 넘긴다. 숫자가 아닌 값은 조용히 건너뛴다 —
     * 사용자가 직접 편집한 HTML일 수 있으므로 예외를 던지지 않는다.
     */
    public Set<Long> collectAttachmentIds(String html) {
        Set<Long> ids = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return ids;
        }
        for (Element img : Jsoup.parseBodyFragment(html).select("img[" + ATTACHMENT_ID_ATTR + "]")) {
            try {
                ids.add(Long.parseLong(img.attr(ATTACHMENT_ID_ATTR).trim()));
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 값은 첨부로 취급하지 않는다.
            }
        }
        return ids;
    }
}
