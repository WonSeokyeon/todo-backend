package com.example.todoapp.domain;

/**
 * 첨부 파일의 생명주기 상태.
 *
 * <p>에디터에서는 할 일을 저장하기 전에 이미지가 먼저 업로드되므로, 업로드 시점에는
 * todo_id를 알 수 없다. 그래서 TEMP로 만들어 두고 할 일 저장 시 본문에 실제로 남아 있는
 * 첨부만 LINKED로 전환하며 todo_id를 채운다 (CLAUDE.md 4장).
 */
public enum AttachmentStatus {

    /** 업로드는 됐지만 아직 어느 할 일에도 연결되지 않은 상태. 24시간 지나면 배치가 정리한다. */
    TEMP,

    /** 할 일 본문에 실제로 포함되어 todo_id가 채워진 상태. */
    LINKED
}
