package com.adaa.automation.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates data that is unique to one test run.
 *
 * <p>Tests must never assume a record someone else created still exists, so every test
 * that needs a record makes its own. The timestamp keeps names sortable and recognisable
 * when a person goes looking in the application afterwards; the random suffix keeps two
 * runs in the same second apart.
 */
public final class TestData {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TestData() {
    }

    /** e.g. {@code auto-objective-20260101-143052-7Q2K}. */
    public static String uniqueName(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(STAMP) + "-" + randomSuffix();
    }

    private static String randomSuffix() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder suffix = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            suffix.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return suffix.toString();
    }
}
