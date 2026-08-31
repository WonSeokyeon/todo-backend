package com.example.todoapp.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, String> {

    private int maxBytes;

    @Override
    public void initialize(MaxByteLength constraintAnnotation) {
        this.maxBytes = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null은 @NotBlank 등 별도 제약이 처리한다
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
