package com.adaa.automation.utils;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Everything a failed run leaves behind for a person to look at: a screenshot, a
 * Playwright trace, and whatever the browser complained about.
 *
 * <p>These are written only for failures. A trace per passing test would be tens of
 * megabytes of noise in every CI artifact.
 */
public final class Artifacts {

    /** Uploaded by the GitHub Actions workflow when a test fails. */
    public static final Path DIRECTORY = Paths.get("target", "automation-artifacts");

    private Artifacts() {
    }

    public static void screenshot(Page page, String name) {
        if (page == null || page.isClosed()) {
            return;
        }
        run(() -> {
            Files.createDirectories(DIRECTORY);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(DIRECTORY.resolve(name + ".png"))
                    .setFullPage(true));
        }, "screenshot");
    }

    /** Writes the trace recorded for this test. Opened with {@code npx playwright show-trace}. */
    public static void trace(BrowserContext context, String name) {
        if (context == null) {
            return;
        }
        run(() -> {
            Files.createDirectories(DIRECTORY);
            context.tracing().stop(new Tracing.StopOptions()
                    .setPath(DIRECTORY.resolve(name + ".trace.zip")));
        }, "trace");
    }

    /** Discards the trace for a test that passed. */
    public static void discardTrace(BrowserContext context) {
        if (context == null) {
            return;
        }
        run(() -> context.tracing().stop(), "trace");
    }

    /** Browser console output and uncaught page errors, when there were any. */
    public static void log(String name, String suffix, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        run(() -> {
            Files.createDirectories(DIRECTORY);
            Files.write(DIRECTORY.resolve(name + "." + suffix + ".log"),
                    lines, StandardCharsets.UTF_8);
        }, suffix);
    }

    /** A file name that is safe on every platform and still identifies the test. */
    public static String safeName(String className, String methodName) {
        String simpleClass = className.substring(className.lastIndexOf('.') + 1);
        return (simpleClass + "." + methodName).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * Artifact capture runs while a test is already failing. If capture itself fails -
     * a closed page, a full disk - that must not replace the real failure with a
     * confusing one, so it is reported and swallowed.
     */
    private static void run(ThrowingAction action, String what) {
        try {
            action.run();
        } catch (IOException | RuntimeException problem) {
            System.err.println("could not write " + what + " artifact: " + problem.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws IOException;
    }
}
