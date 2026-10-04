package com.example.support.dto;

import com.example.support.domain.CustomerIds;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed constraint: a reusable name for "{@code @Pattern(regexp = "C-\\d{1,18}")}". Works on request-body
 * fields, path variables and query parameters. {@code null} is valid; add {@code @NotNull} where required.
 */
@Documented
@Pattern(regexp = CustomerIds.REGEX)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCustomerId {

    String message() default "must be a customer id such as C-100";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
