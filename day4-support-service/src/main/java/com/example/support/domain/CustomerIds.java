package com.example.support.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The database key is a plain {@code BIGINT}; the API shows it as {@code C-<number>}, the format used by the
 * Day 1 ticket files. Keeping the conversion in one place lets the storage and API formats evolve separately.
 */
public final class CustomerIds {

    /** Used by {@code @ValidCustomerId}, so validation and parsing agree. */
    public static final String REGEX = "C-\\d{1,18}";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private CustomerIds() {
    }

    public static String format(Long id) {
        return id == null ? null : "C-" + id;
    }

    /** Empty when the text is not a well-formed customer id. */
    public static Optional<Long> parse(String publicId) {
        if (publicId == null || !PATTERN.matcher(publicId).matches()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(publicId.substring(2)));
    }
}
