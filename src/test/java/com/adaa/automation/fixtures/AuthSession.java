package com.adaa.automation.fixtures;

import com.adaa.automation.config.Config;
import com.adaa.automation.network.ColorConfiguration;
import com.adaa.automation.pages.LoginPage;
import com.adaa.automation.utils.PlaywrightManager;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.testng.SkipException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Signs in once per run and keeps the resulting session on disk, so no test types a
 * password.
 *
 * <p>That is partly for speed - a sign-in per test would add a page load and a round trip
 * to every one of them - and partly for safety. A Playwright trace records the value
 * passed to every {@code fill()}, and a screenshot records whatever is on screen, so a
 * test that types a password into a traced context writes that password into an artifact
 * that then gets uploaded to CI. The single sign-in here happens in its own context with
 * no tracing and no screenshots, and every other test starts already signed in.
 *
 * <p>The saved state is Playwright's own storage state: cookies and local storage. The
 * application keeps its client-side session - the token, the user's permissions, the
 * selected entity and strategic cycle, the color configuration - in local storage, and a
 * page opened without it refuses the user even though the server session cookie is valid.
 * The file is written under {@code target/}, which is ignored by git and cleaned by
 * {@code mvn clean}. Its contents are never logged: it holds the session token.
 */
public final class AuthSession {

    private static final Path STATE = Paths.get("target", ".auth", "storage-state.json");

    private static boolean established;

    private AuthSession() {
    }

    /**
     * The saved signed-in session, creating it on first use.
     *
     * @throws SkipException when no account is configured - these tests cannot run
     *                       without one, and must not be reported as passing
     */
    public static synchronized Path storageState() {
        if (!Config.hasCredentials()) {
            throw new SkipException(Config.missingCredentialsReason());
        }
        if (!established) {
            establish();
            established = true;
        }
        return STATE;
    }

    private static void establish() {
        // A context of its own, with tracing off: the one place a password is typed must
        // not be recorded anywhere.
        BrowserContext context = PlaywrightManager.newContext(null);
        try {
            Page page = context.newPage();
            new LoginPage(page).signIn(Config.username(), Config.password());
            waitForClientSession(page);

            Files.createDirectories(STATE.getParent());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(STATE));
        } catch (IOException problem) {
            throw new UncheckedIOException("could not save the signed-in session", problem);
        } finally {
            context.close();
        }
    }

    /**
     * Waits until the application has written its client-side session, so the state saved
     * is a complete one.
     *
     * <p>The landing page may still be loading when the sign-in returns. Fails if the token
     * or the color configuration never appear, naming the key but never its value.
     */
    private static void waitForClientSession(Page page) {
        for (String key : new String[] {"token", ColorConfiguration.STORAGE_KEY}) {
            try {
                page.waitForFunction("k => localStorage.getItem(k) !== null", key);
            } catch (RuntimeException missing) {
                throw new IllegalStateException("signed in, but the application wrote no '" + key
                        + "' to local storage; the saved session would be refused on every page."
                        + " Landed on " + page.url());
            }
        }
        page.waitForLoadState(LoadState.LOAD);
    }
}
