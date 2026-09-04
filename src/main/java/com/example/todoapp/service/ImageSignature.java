package com.example.todoapp.service;

import java.util.Arrays;

/**
 * 이미지 파일의 매직바이트를 확인한다.
 *
 * <p>클라이언트가 보낸 Content-Type은 신뢰할 수 없다. 화이트리스트 검사만으로는
 * 실행 파일에 image/png라고 이름표만 붙여 올리는 것을 막지 못하므로,
 * 실제 바이트가 선언한 형식과 맞는지 확인한다 (CLAUDE.md 5장).
 */
final class ImageSignature {

    /** 형식 판별에 필요한 최소 바이트 수. WebP의 RIFF....WEBP를 읽으려면 12바이트가 필요하다. */
    static final int HEADER_SIZE = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38}; // "GIF8"
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50}; // "WEBP" (8~11바이트)

    private ImageSignature() {
    }

    /**
     * 헤더 바이트가 선언된 Content-Type과 일치하는지 확인한다.
     * 허용 목록에 없는 타입이면 false를 돌려준다.
     */
    static boolean matches(String contentType, byte[] header) {
        if (contentType == null || header == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, JPEG);
            case "image/png" -> startsWith(header, PNG);
            case "image/gif" -> startsWith(header, GIF);
            case "image/webp" -> startsWith(header, RIFF) && hasWebpMarker(header);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] header, byte[] signature) {
        if (header.length < signature.length) {
            return false;
        }
        return Arrays.equals(header, 0, signature.length, signature, 0, signature.length);
    }

    /** WebP는 RIFF 컨테이너라 4~7바이트가 파일 크기고, 8~11바이트에 "WEBP"가 온다. */
    private static boolean hasWebpMarker(byte[] header) {
        if (header.length < 12) {
            return false;
        }
        return Arrays.equals(header, 8, 12, WEBP, 0, 4);
    }
}
