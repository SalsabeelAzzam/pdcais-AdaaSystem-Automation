package com.adaa.automation.utils;

import com.adaa.automation.config.Config;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Prints a readable account of the run: what started, what each test cost, and a summary
 * at the end with the counts and the total duration.
 *
 * <p>Artifact capture deliberately does not live here. A listener's failure callback and
 * the {@code @AfterMethod} that closes the browser context race each other in a way that
 * is version-dependent, so the suite captures in {@code @AfterMethod}, where the page is
 * guaranteed to still be open.
 */
public final class TestListener implements ITestListener, ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        System.out.println("adaa-automation | " + Config.describe());
    }

    @Override
    public void onTestStart(ITestResult result) {
        System.out.println("  ... " + name(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        System.out.println("  PASS " + name(result) + seconds(result));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        System.out.println("  FAIL " + name(result) + seconds(result));
        Throwable cause = result.getThrowable();
        if (cause != null) {
            System.out.println("       " + firstLine(cause.getMessage()));
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        System.out.println("  SKIP " + name(result));
        Throwable cause = result.getThrowable();
        if (cause != null) {
            System.out.println("       " + firstLine(cause.getMessage()));
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        int passed = context.getPassedTests().size();
        int failed = context.getFailedTests().size();
        int skipped = context.getSkippedTests().size();
        long millis = context.getEndDate().getTime() - context.getStartDate().getTime();

        System.out.printf("%n%s: %d passed, %d failed, %d skipped in %.1fs%n",
                context.getName(), passed, failed, skipped, millis / 1000.0);
    }

    private static String name(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName() + "." + result.getName();
    }

    private static String seconds(ITestResult result) {
        return String.format(" (%.1fs)", (result.getEndMillis() - result.getStartMillis()) / 1000.0);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
