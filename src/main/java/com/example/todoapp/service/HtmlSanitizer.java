package com.example.todoapp.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Tiptap 본문 HTML을 저장 전에 정화한다 (CLAUDE.md 6장 XSS 이중 방어의 서버 측).
 * 툴바·Tiptap 확장·프론트 DOMPurify와 항상 같은 태그 집합을 유지해야 한다.
 */
@Component
public class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "em", "h2", "h3", "ul", "ol", "li", "a", "code", "pre", "blockquote")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .addEnforcedAttribute("a", "target", "_blank");

    // 기본 pretty-print는 블록 요소를 재포맷해 pre 블록의 공백·줄바꿈을 망가뜨린다.
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, "", SAFELIST, OUTPUT_SETTINGS);
    }
}
