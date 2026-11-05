package com.slokam.av.service;

import com.slokam.av.exception.custom.ApiException;
import org.springframework.data.domain.*;
import java.util.*;

final class CatalogValues {
    private CatalogValues() {}
    static String text(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    static List<String> distinct(Collection<String> values) {
        if (values == null) return new ArrayList<>();
        Map<String, String> result = new LinkedHashMap<>();
        values.stream().map(CatalogValues::text).filter(Objects::nonNull)
                .forEach(v -> result.putIfAbsent(v.toLowerCase(Locale.ROOT), v));
        return new ArrayList<>(result.values());
    }
    static Pageable page(int page, int size, Sort sort) {
        if (page < 0 || size < 1) throw new ApiException(400, "INVALID_PAGINATION", "page must be nonnegative and size must be positive");
        return PageRequest.of(page, Math.min(size, 100), sort);
    }
    static ApiException invalid(String message) { return new ApiException(400, "INVALID_CATALOG_DATA", message); }
}
