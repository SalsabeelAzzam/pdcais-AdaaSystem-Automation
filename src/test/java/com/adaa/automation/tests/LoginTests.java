package com.adaa.automation.tests;

import com.adaa.automation.config.Config;
import com.adaa.automation.fixtures.BaseTest;
import com.adaa.automation.network.ColorConfiguration;
import com.adaa.automation.network.RequestLog;
import com.adaa.automation.pages.LoginPage;
import com.adaa.automation.utils.TestData;
import com.google.gson.JsonParser;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertEquals;
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
        requireCredentials();
        stopTracingBeforeTypingCredentials();

        LoginPage login = new LoginPage(page);
        login.signIn(Config.username(), Config.password());

        assertFalse(login.isOnLoginPage(),
                "a successful sign-in must leave the sign-in page");
    }

    /**
     * The sign-in page asks for the color configuration once the credentials are accepted,
     * waits for it, keeps it for the pages that follow, and only then moves on. The request
     * is observed as the browser makes it; the test never calls the endpoint itself.
     */
    @Test(groups = "smoke", description = "After a real sign-in the application loads its color configuration")
    public void colorConfigurationIsLoadedAfterLogin() {
        requireCredentials();
        stopTracingBeforeTypingCredentials();
        LoginPage login = new LoginPage(page);

        ColorConfiguration.Answer response;
        List<String> requests;
        try (RequestLog log = RequestLog.start(page)) {
            response = login.signIn(Config.username(), Config.password());
            requests = log.requests();
        }

        // Asked for once, and only after the credentials were sent.
        int signInAt = requests.indexOf("POST /AuthenticationServiceGW/Login");
        int colorsAt = requests.indexOf(ColorConfiguration.REQUEST);
        assertTrue(signInAt >= 0, "the sign-in request must be made; requests: " + requests);
        assertTrue(colorsAt > signInAt,
                "the color configuration must be requested after the sign-in; requests: " + requests);
        assertEquals(requests.stream().filter(ColorConfiguration.REQUEST::equals).count(), 1L,
                "the color configuration must be requested exactly once; requests: " + requests);

        // Answered successfully, with color bands.
        assertTrue(response.ok(), "the color configuration request failed: HTTP " + response.status());
        assertTrue(response.contentType().contains("application/json"),
                "the color configuration must be JSON, but was " + response.contentType());
        String body = response.body();
        List<ColorConfiguration.Band> bands = response.bands();
        assertFalse(bands.isEmpty(), "the color configuration must hold at least one band: " + body);
        for (ColorConfiguration.Band band : bands) {
            assertTrue(band.id() > 0, "every band needs an id: " + band);
            assertTrue(band.color() != null && band.color().matches("#[0-9A-Fa-f]{6}"),
                    "every band needs a #RRGGBB color: " + band);
            assertTrue(band.descEn() != null && !band.descEn().isBlank(), "every band needs an English name: " + band);
            assertTrue(band.descAr() != null && !band.descAr().isBlank(), "every band needs an Arabic name: " + band);
            assertTrue(band.minPercentage() != null && band.maxPercentage() != null
                            && band.minPercentage().compareTo(band.maxPercentage()) <= 0,
                    "every band needs a range from its minimum to its maximum: " + band);
        }

        // Kept for the pages that follow, and the sign-in completed.
        String kept = login.sessionStorageItem(ColorConfiguration.SESSION_KEY);
        assertTrue(kept != null, "the application must keep the color configuration in session storage");
        assertEquals(JsonParser.parseString(kept), JsonParser.parseString(body),
                "the application must keep exactly the color configuration it received");
        assertFalse(login.isOnLoginPage(), "the sign-in must move on once the colors are loaded");
    }

    private static void requireCredentials() {
        if (!Config.hasCredentials()) {
            throw new SkipException(Config.missingCredentialsReason());
        }
    }
}
