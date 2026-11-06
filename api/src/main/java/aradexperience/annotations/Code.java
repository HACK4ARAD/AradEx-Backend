package aradexperience.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cod AradExperience (bilet / ofertă / acțiune comunitară): `AX-T12-7KQ2MZ`, `AX-O3-…`, `AX-C5-…`.
 * Adnotare compusă (regex), fără validator propriu — exemplul pentru pachetul `annotations`.
 * Alfabetul exclude 0/O/1/I ca să poată fi dictat la telefon (vezi util/Codes în app).
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "^AX-[TOC]\\d+-[A-HJ-NP-Z2-9]{6}$", message = "invalid AradExperience code")
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Code {
    String message() default "invalid AradExperience code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
