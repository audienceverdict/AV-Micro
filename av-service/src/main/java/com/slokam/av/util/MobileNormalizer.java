package com.slokam.av.util;

import com.slokam.av.exception.custom.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MobileNormalizer {
    private final String country;
    private final int length;

    public MobileNormalizer(
            @Value("${auth.mobile.country-code:91}") String country,
            @Value("${auth.mobile.national-length:10}") int length) {
        this.country = country;
        this.length = length;
    }

    public String normalize(String raw) {
        String value = raw == null ? "" : raw.replaceAll("[\\s()-]", "");
        if (value.matches("[0-9]{" + length + "}")) value = "+" + country + value;
        if (!value.matches("\\+" + country + "[0-9]{" + length + "}"))
            throw new ApiException(400, "INVALID_MOBILE", "Enter a valid mobile number");
        return value;
    }
}
