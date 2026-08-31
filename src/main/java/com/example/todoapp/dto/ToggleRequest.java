package com.example.todoapp.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleRequest(@NotNull Boolean completed) {
}
