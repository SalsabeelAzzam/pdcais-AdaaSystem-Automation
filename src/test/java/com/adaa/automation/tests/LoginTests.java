package com.adaa.automation.tests;

import com.adaa.automation.config.Config;
import com.adaa.automation.fixtures.BaseTest;
import com.adaa.automation.pages.LoginPage;
import com.adaa.automation.utils.TestData;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The way in. These run signed out, so they extend {@link BaseTest} rather than the
 * authenticated base.
 */
public class LoginTests extends BaseTest {

    @Test(groups = "smoke", description = "The sign-in page renders its form")
    public void loginPageRenders() {
        LoginPage login = new LoginPage(page);
        login.open();

        assertThat(login.form()).isVisible();
        assertThat(login.email()).isVisible();
        assertThat(login.password()).isVisible();
        assertThat(login.submit()).isEnabled();
    }

    @Test(groups = "smoke", description = "Credentials that are not real are refused")
    public void invalidCredentialsAreRejected() {
        LoginPage login = new LoginPage(page);

        // Generated rather than fixed, so this can never collide with a real account.
        String user = TestData.uniqueName("no-such-user") + "@example.invalid";
        boolean accepted = login.trySignIn(user, TestData.uniqueName("not-a-password"));

        assertFalse(accepted, "the application must not accept credentials that are not real");
        assertTrue(login.isOnLoginPage(),
                "a refused sign-in must leave the browser on the sign-in page, but it was at "
                        + login.currentUrl());
    }

    @Test(groups = "smoke", description = "The configured account can sign in")
    public void validCredentialsCanSignIn() {
        if (!Config.hasCredentials()) {
            throw new SkipException(Config.missingCredentialsReason());
        }

        LoginPage login = new LoginPage(page);
        login.signIn(Config.username(), Config.password());

        assertFalse(login.isOnLoginPage(),
                "a successful sign-in must leave the sign-in page");
    }
}
