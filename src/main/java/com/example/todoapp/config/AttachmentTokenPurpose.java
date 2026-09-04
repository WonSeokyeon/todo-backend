package com.example.todoapp.config;

/**
 * 첨부 전용 단기 서명 토큰의 용도. JWT의 typ 클레임에 담긴다.
 *
 * <p>업로드와 조회를 구분하는 이유는 서로 대체될 수 없게 하기 위해서다.
 * 조회 토큰으로 업로드가 되면 남의 첨부 슬롯에 파일을 덮어쓸 수 있다.
 */
public enum AttachmentTokenPurpose {

    UPLOAD("att-upload"),
    VIEW("att-view");

    private final String claimValue;

    AttachmentTokenPurpose(String claimValue) {
        this.claimValue = claimValue;
    }

    public String getClaimValue() {
        return claimValue;
    }
}
