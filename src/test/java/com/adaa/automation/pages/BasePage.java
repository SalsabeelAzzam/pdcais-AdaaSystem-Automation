package com.adaa.automation.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Shared plumbing for the page objects.
 *
 * <p>Every selector in this suite lives in a page object, never in a test. A test says
 * what a user does; how that is reached in the DOM is this layer's business, so a change
 * to the application's markup is a change in one file.
 *
 * <p>All navigation is relative - the browser context carries the base URL - so the same
 * tests run against local, QA, staging or a production-like environment unchanged.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /** Navigates to a path relative to the configured base URL. */
    protected void navigateTo(String path) {
        page.navigate(path, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    /**
     * Waits for the page to go quiet. Tolerates a page that never fully settles - some
     * screens poll in the background, and the caller's own wait is the real check.
     */
    protected void settle(double timeoutMs) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
        } catch (RuntimeException ignored) {
            // A page with background polling never reaches idle; carry on.
        }
    }

    /**
     * Runs an action that makes the page reload itself, and waits for the reloaded page.
     *
     * <p>Such actions often change the current page first and reload it a moment later, so
     * nothing visible proves the reload has happened. The current page is marked before the
     * action; the wait ends on a fully loaded page that no longer carries the mark.
     */
    protected void waitForReloadAfter(Runnable action) {
        markPageBeforeReload();
        action.run();
        waitForReloadedPage();
    }

    /** Marks the current page, for {@link #waitForReloadedPage()}. */
    protected void markPageBeforeReload() {
        page.evaluate("() => { window.__automationBeforeReload = true; }");
    }

    /** Waits for a fully loaded page that is not the one {@link #markPageBeforeReload()} marked. */
    protected void waitForReloadedPage() {
        page.waitForFunction(
                "() => !window.__automationBeforeReload && document.readyState === 'complete'",
                null,
                new Page.WaitForFunctionOptions().setTimeout(30_000));
    }

    public String currentUrl() {
        return page.url();
    }

    /**
     * The page direction, "rtl" on the Arabic pages. The application sets it on
     * {@code <body>}; {@code <html>} carries no direction at all.
     */
    public String documentDirection() {
        return page.locator("body").getAttribute("dir");
    }

    public String documentLanguage() {
        return page.locator("html").getAttribute("lang");
    }
}
