package com.adaa.automation.fixtures;

import com.adaa.automation.config.Config;
import com.adaa.automation.pages.LoginPage;
import com.adaa.automation.utils.PlaywrightManager;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.testng.SkipException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
 * <p>The saved state is two files: cookies and local storage, which Playwright saves itself,
 * and the application's session storage, which it does not. The application keeps its
 * client-side session - the token, the user's permissions, the selected entity and
 * strategic cycle - in session storage only, and a page opened without it refuses the user
 * even though the server session cookie is valid. Both files are written under
 * {@code target/}, which is ignored by git and cleaned by {@code mvn clean}. Neither file's
 * contents are ever logged: they hold the session token.
 */
public final class AuthSession {

    private static final Path STATE = Paths.get("target", ".auth", "storage-state.json");
    private static final Path SESSION_STORAGE = Paths.get("target", ".auth", "session-storage.json");

    /**
     * The session storage keys the application writes when a user signs in. Anything else
     * there - table state, the last menu item, an access-denied origin - belongs to the page
     * that wrote it, and carrying it into another test would leak state between tests.
     */
    private static final List<String> SESSION_KEYS = List.of(
            "token",
            "refreshToken",
            "isMonitoringUser",
            "isInstitutionalUser",
            "userName",
            "userEntityPermissions",
            "selectedOrgUnit",
            "selectedEntity",
            "selectedStrategyCycle",
            "preferredLanguage",
            "direction",
            "lang",
            "userFullNameAr",
            "userFullNameEn");

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

    /**
     * Seeds every tab of a context with the saved session storage.
     *
     * <p>Must be called before the context's first page is opened, so the script runs ahead
     * of the application's own scripts on the very first load, and before tracing starts, so
     * the token in the script is not recorded into a trace.
     *
     * <p>The script runs on every document, so it guards itself three ways:
     * <ul>
     *   <li>only on the application's own origin - never on about:blank or anywhere else;</li>
     *   <li>only once per tab, marked on {@code window.name}. Session storage itself cannot
     *       hold the marker: the application's sign-out clears it, and a marker there would
     *       sign the user straight back in on the next page;</li>
     *   <li>never over a key that already has a value, because the application changes some
     *       of these itself - it refreshes the token and switches the selected entity.</li>
     * </ul>
     */
    public static void restoreSessionStorage(BrowserContext context) {
        storageState();
        String saved;
        try {
            saved = Files.readString(SESSION_STORAGE, StandardCharsets.UTF_8);
        } catch (IOException problem) {
            throw new UncheckedIOException("could not read the saved session storage", problem);
        }

        String script = """
                (() => {
                    const ORIGIN = %s;
                    const MARKER = '__adaaAutomationSessionRestored';
                    const SAVED = %s;

                    if (location.origin !== ORIGIN) return;
                    if (window.top !== window) return;
                    if (String(window.name).includes(MARKER)) return;
                    window.name = String(window.name) + MARKER;

                    try {
                        for (const [key, value] of Object.entries(SAVED)) {
                            if (sessionStorage.getItem(key) === null) {
                                sessionStorage.setItem(key, value);
                            }
                        }
                    } catch (e) {
                        // Storage unavailable: the page will refuse the user, and the test's
                        // own access-denied check reports that.
                    }
                })();
                """.formatted(jsString(applicationOrigin()), saved);

        context.addInitScript(script);
    }

    /** scheme://host[:port] of the configured base URL, as {@code location.origin} reports it. */
    private static String applicationOrigin() {
        URI base = URI.create(Config.baseUrl());
        String origin = base.getScheme().toLowerCase() + "://" + base.getHost().toLowerCase();
        int port = base.getPort();
        boolean defaultPort = port == -1
                || ("http".equalsIgnoreCase(base.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(base.getScheme()) && port == 443);
        return defaultPort ? origin : origin + ":" + port;
    }

    private static String jsString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static void establish() {
        // A context of its own, with tracing off: the one place a password is typed must
        // not be recorded anywhere.
        BrowserContext context = PlaywrightManager.newContext(null);
        try {
            Page page = context.newPage();
            new LoginPage(page).signIn(Config.username(), Config.password());

            Files.createDirectories(STATE.getParent());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(STATE));
            Files.writeString(SESSION_STORAGE, readSessionStorage(page), StandardCharsets.UTF_8);
        } catch (IOException problem) {
            throw new UncheckedIOException("could not save the signed-in session", problem);
        } finally {
            context.close();
        }
    }

    /**
     * The sign-in keys from the page's session storage, serialised by the browser itself.
     *
     * <p>Waits for the token first: the landing page may still be loading when the sign-in
     * returns. Fails if it never appears, naming the key but never its value.
     */
    private static String readSessionStorage(Page page) {
        try {
            page.waitForFunction("() => sessionStorage.getItem('token') !== null");
        } catch (RuntimeException missing) {
            throw new IllegalStateException("signed in, but the application wrote no 'token' to "
                    + "session storage; the saved session would be refused on every page");
        }
        page.waitForLoadState(LoadState.LOAD);

        Object json = page.evaluate(
                "keys => JSON.stringify(Object.fromEntries(keys"
                        + ".filter(k => sessionStorage.getItem(k) !== null)"
                        + ".map(k => [k, sessionStorage.getItem(k)])))",
                SESSION_KEYS);
        return String.valueOf(json);
    }
}
