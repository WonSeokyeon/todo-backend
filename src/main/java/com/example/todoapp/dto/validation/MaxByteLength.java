package com.example.todoapp.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UTF-8 인코딩 기준 바이트 길이를 검증한다. @Size는 문자 수를 세므로
 * 한글처럼 1자가 여러 바이트인 입력에서 BCrypt 72바이트 한계를 잡지 못한다 (CLAUDE.md 4장).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxByteLengthValidator.class)
public @interface MaxByteLength {

    int value();

    String message() default "값이 너무 깁니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
