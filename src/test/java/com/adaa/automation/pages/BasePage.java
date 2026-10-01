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

    public String currentUrl() {
        return page.url();
    }

    /** The document direction, which is "rtl" on the Arabic pages. */
    public String documentDirection() {
        return page.locator("html").getAttribute("dir");
    }

    public String documentLanguage() {
        return page.locator("html").getAttribute("lang");
    }
}
