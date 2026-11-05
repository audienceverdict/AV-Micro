package com.slokam.av.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {
    String message() default "must be an absolute HTTP/HTTPS URL without credentials";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
