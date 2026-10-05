package com.adaa.automation.fixtures;

import com.adaa.automation.config.Config;
import com.adaa.automation.utils.Artifacts;
import com.adaa.automation.utils.PlaywrightManager;
import com.adaa.automation.utils.TestListener;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Listeners;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base for every test: one browser for the run, a fresh context and page for each test,
 * and a screenshot, trace and console log left behind whenever one fails.
 *
 * <p>Tests that act as a signed-in user extend {@link AuthenticatedTest} instead.
 */
@Listeners(TestListener.class)
public abstract class BaseTest {

    protected BrowserContext context;
    protected Page page;

    private boolean tracing;

    private final List<String> consoleMessages = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pageErrors = Collections.synchronizedList(new ArrayList<>());

    @BeforeSuite(alwaysRun = true)
    public void startBrowser() {
        // Without an address there is nothing to test. Skipping says so plainly; passing
        // against nothing would be worse than failing.
        if (!Config.hasBaseUrl()) {
            throw new SkipException(Config.missingBaseUrlReason());
        }
        PlaywrightManager.browser();
    }

    @AfterSuite(alwaysRun = true)
    public void stopBrowser() {
        PlaywrightManager.close();
    }

    @BeforeMethod(alwaysRun = true)
    public void openContext() {
        consoleMessages.clear();
        pageErrors.clear();

        context = PlaywrightManager.newContext(storageState());
        // Before the first page, so it applies to it; before tracing, so nothing it is
        // given is recorded.
        prepareContext(context);
        page = context.newPage();

        page.onConsoleMessage(message ->
                consoleMessages.add("[" + message.type() + "] " + message.text()));
        page.onPageError(error -> pageErrors.add(error));

        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                // Do not embed the suite's own source into the trace.
                .setSources(false));
        tracing = true;
    }

    /**
     * Stops recording this test's trace, for a test about to type the real credentials.
     *
     * <p>A trace keeps the value of every fill and the body of every request - the sign-in
     * request carries the password - and a failed test's trace is uploaded as a CI
     * artifact. Such a test still leaves a screenshot and the browser's console behind.
     */
    protected void stopTracingBeforeTypingCredentials() {
        if (tracing) {
            Artifacts.discardTrace(context);
            tracing = false;
        }
    }

    @AfterMethod(alwaysRun = true)
    public void closeContext(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE) {
                String name = Artifacts.safeName(
                        result.getTestClass().getName(), result.getName());

                Artifacts.screenshot(page, name);
                if (tracing) {
                    Artifacts.trace(context, name);
                }
                Artifacts.log(name, "console", consoleMessages);
                Artifacts.log(name, "pageerrors", pageErrors);
            } else if (tracing) {
                Artifacts.discardTrace(context);
            }
        } finally {
            tracing = false;
            if (context != null) {
                context.close();
                context = null;
                page = null;
            }
        }
    }

    /**
     * The signed-in session to seed each context with, or null for a signed-out one.
     * Overridden by {@link AuthenticatedTest}.
     */
    protected Path storageState() {
        return null;
    }

    /**
     * Anything a context needs before its first page opens. Nothing, for a signed-out one.
     * Overridden by {@link AuthenticatedTest}.
     */
    protected void prepareContext(BrowserContext context) {
    }
}
