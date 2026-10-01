package com.adaa.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * The sign-in screen. Every other page is behind it.
 */
public final class LoginPage extends BasePage {

    private static final String PATH = "/UsersManagement/Index";

    public LoginPage(Page page) {
        super(page);
    }

    public Locator form() {
        return page.locator("#formAuthentication");
    }

    public Locator email() {
        return page.locator("#email");
    }

    public Locator password() {
        return page.locator("#password");
    }

    public Locator submit() {
        return page.locator("#submitButton");
    }

    public Locator togglePassword() {
        return page.locator("#togglePassword");
    }

    public Locator languageDropdown() {
        return page.locator("#languageDropdown");
    }

    public void open() {
        navigateTo(PATH);
        // The form is wired up by client-side validation after load; clicking before that
        // happens swallows the submit, so wait for the page to go quiet first.
        settle(15_000);
        submit().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    /** Fills the form and submits it, without waiting for an outcome. */
    public void submitCredentials(String user, String secret) {
        email().fill(user);
        password().fill(secret);
        submit().click();
    }

    /**
     * Signs in and waits until the application has left the sign-in screen.
     *
     * @throws com.microsoft.playwright.TimeoutError when the sign-in does not succeed
     */
    public void signIn(String user, String secret) {
        open();
        submitCredentials(user, secret);
        page.waitForURL(url -> !isLoginUrl(url), new Page.WaitForURLOptions().setTimeout(30_000));
    }

    /**
     * Attempts a sign-in and reports whether the application accepted it, instead of
     * throwing. Used to assert that bad credentials are refused.
     */
    public boolean trySignIn(String user, String secret) {
        open();
        submitCredentials(user, secret);
        try {
            page.waitForURL(url -> !isLoginUrl(url), new Page.WaitForURLOptions().setTimeout(10_000));
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    public boolean isOnLoginPage() {
        return isLoginUrl(page.url());
    }

    private static boolean isLoginUrl(String url) {
        return url.toLowerCase().contains("/usersmanagement/");
    }
}
