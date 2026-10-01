package com.adaa.automation.utils;

import com.adaa.automation.config.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;

/**
 * Owns the browser for the whole run.
 *
 * <p>One Playwright instance and one browser per JVM, because launching a browser is the
 * expensive part. A fresh {@link BrowserContext} per test is what keeps tests isolated:
 * cookies, local storage and the signed-in session all belong to the context, so no test
 * can see another's state and the order they run in does not matter.
 */
public final class PlaywrightManager {

    private static Playwright playwright;
    private static Browser browser;

    private PlaywrightManager() {
    }

    /** The shared browser, launched on first use. */
    public static synchronized Browser browser() {
        if (browser == null) {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(Config.headless())
                    .setSlowMo(Config.slowMoMs()));
        }
        return browser;
    }

    /**
     * A new, empty browser context pointed at the configured environment.
     *
     * @param storageState a saved signed-in session to seed the context with, or null for
     *                     a signed-out one
     */
    public static BrowserContext newContext(Path storageState) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setBaseURL(Config.baseUrl())
                // Test environments commonly serve a self-signed certificate.
                .setIgnoreHTTPSErrors(true)
                .setViewportSize(1440, 900)
                .setLocale(Config.locale());

        if (storageState != null) {
            options.setStorageStatePath(storageState);
        }

        BrowserContext context = browser().newContext(options);
        context.setDefaultTimeout(Config.timeoutMs());
        return context;
    }

    public static synchronized void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
