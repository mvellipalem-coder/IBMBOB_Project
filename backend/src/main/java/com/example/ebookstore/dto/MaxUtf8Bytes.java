package com.example.ebookstore.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The text may use at most {@link #value()} bytes when encoded as UTF-8.
 *
 * <p>Why: BCrypt only reads the first 72 <em>bytes</em> of a password. A password of 72 characters can be longer
 * than 72 bytes when it contains non-ASCII characters, so a character count is not enough.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    String message() default "is too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value();
}
