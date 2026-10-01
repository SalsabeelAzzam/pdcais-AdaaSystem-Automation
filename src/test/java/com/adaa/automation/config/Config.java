package com.adaa.automation.config;

/**
 * Every value the suite needs from the outside world.
 *
 * <p>Resolution order for each setting is: JVM system property (so {@code -Dheadless=false}
 * works from the command line), then environment variable, then a default. Credentials have
 * no default - when they are absent the tests that need them skip with a reason rather than
 * failing, and nothing is ever hard-coded here.
 *
 * <p>Nothing in this class is specific to an environment. Pointing the suite at local, QA,
 * staging or a production-like environment is a matter of changing AUTOMATION_BASE_URL.
 */
public final class Config {

    private Config() {
    }

    public static final String BASE_URL = "AUTOMATION_BASE_URL";
    public static final String USERNAME = "AUTOMATION_USERNAME";
    public static final String PASSWORD = "AUTOMATION_PASSWORD";
    public static final String HEADLESS = "AUTOMATION_HEADLESS";
    public static final String TIMEOUT_MS = "AUTOMATION_TIMEOUT_MS";
    public static final String SLOWMO_MS = "AUTOMATION_SLOWMO_MS";
    public static final String LOCALE = "AUTOMATION_LOCALE";
    public static final String ENVIRONMENT = "AUTOMATION_ENV";

    /** Base address of the application under test, without a trailing slash. */
    public static String baseUrl() {
        String value = read(BASE_URL, "baseUrl", "");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static String username() {
        return read(USERNAME, "username", "");
    }

    public static String password() {
        return read(PASSWORD, "password", "");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(read(HEADLESS, "headless", "true"));
    }

    /** Per-action timeout. Generous by default: these are real pages over a real network. */
    public static double timeoutMs() {
        return number(read(TIMEOUT_MS, "timeoutMs", "15000"), 15_000);
    }

    /** Milliseconds inserted between actions. For watching a run, not for CI. */
    public static double slowMoMs() {
        return number(read(SLOWMO_MS, "slowMoMs", "0"), 0);
    }

    public static String locale() {
        return read(LOCALE, "locale", "en-US");
    }

    /** A label for reports only - it changes no behaviour. */
    public static String environment() {
        return read(ENVIRONMENT, "env", "local");
    }

    public static boolean hasBaseUrl() {
        return !baseUrl().isEmpty();
    }

    public static boolean hasCredentials() {
        return !username().isEmpty() && !password().isEmpty();
    }

    public static String missingBaseUrlReason() {
        return "Set " + BASE_URL + " to the application under test, e.g. http://localhost:5225";
    }

    public static String missingCredentialsReason() {
        return "Set " + USERNAME + " and " + PASSWORD + " for an account on " + baseUrl()
                + ". These tests sign in, so they cannot run without one.";
    }

    /** A one-line description for the run log. Deliberately never includes the password. */
    public static String describe() {
        return "environment=" + environment()
                + " baseUrl=" + (hasBaseUrl() ? baseUrl() : "<unset>")
                + " user=" + (hasCredentials() ? "<configured>" : "<unset>")
                + " headless=" + headless()
                + " locale=" + locale();
    }

    private static String read(String environmentVariable, String systemProperty, String fallback) {
        String fromProperty = System.getProperty(systemProperty);
        if (isSet(fromProperty)) {
            return fromProperty.trim();
        }
        String fromEnvironment = System.getenv(environmentVariable);
        if (isSet(fromEnvironment)) {
            return fromEnvironment.trim();
        }
        return fallback;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static double number(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
