package com.example.todoapp.dto;

import com.example.todoapp.domain.User;

public record UserResponse(Long id, String email, String nickname) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
