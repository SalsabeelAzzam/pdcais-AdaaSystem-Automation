package com.adaa.automation.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

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

    /** The shape {@link #uniqueName} produces: a prefix, the stamp, and the suffix. */
    private static final Pattern GENERATED = Pattern.compile(
            "^auto-[a-z0-9-]+-\\d{8}-\\d{6}-[ABCDEFGHJKLMNPQRSTUVWXYZ2-9]{4}$");

    private TestData() {
    }

    /** e.g. {@code auto-objective-20260101-143052-7Q2K}. */
    public static String uniqueName(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(STAMP) + "-" + randomSuffix();
    }

    /**
     * A unique name of exactly {@code length} characters, for boundary tests. The prefix is
     * padded with 'x' so the result keeps the shape {@link #isGenerated} recognises, which is
     * what lets cleanup delete a record made with it.
     */
    public static String uniqueNameOfLength(String prefix, int length) {
        String tail = "-" + LocalDateTime.now().format(STAMP) + "-" + randomSuffix();
        int padding = length - prefix.length() - tail.length();
        if (padding < 1) {
            throw new IllegalArgumentException("a name of " + length + " characters cannot hold '"
                    + prefix + "' and a unique stamp");
        }
        return prefix + "-" + "x".repeat(padding - 1) + tail;
    }

    /**
     * Whether a name has the shape this suite generates for its own records, with an
     * {@code auto-} prefix. Cleanup deletes nothing that fails this check, so a record the
     * suite did not create can never be removed by it.
     */
    public static boolean isGenerated(String name) {
        return name != null && GENERATED.matcher(name).matches();
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
