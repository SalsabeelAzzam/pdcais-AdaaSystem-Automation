package com.adaa.automation.fixtures;

import com.adaa.automation.pages.ObjectiveDetailsPage;
import com.adaa.automation.pages.ObjectiveFormPage;
import com.adaa.automation.pages.ObjectiveListPage;
import com.microsoft.playwright.BrowserContext;

import java.nio.file.Path;

/**
 * Base for tests that act as a signed-in user.
 *
 * <p>Each test still gets its own browser context; that context is simply seeded with the
 * session established once for the run, so the tests stay independent of each other while
 * none of them has to sign in.
 */
public abstract class AuthenticatedTest extends BaseTest {

    @Override
    protected Path storageState() {
        return AuthSession.storageState();
    }

    /** The cookies alone are not a signed-in session here; see {@link AuthSession}. */
    @Override
    protected void prepareContext(BrowserContext context) {
        AuthSession.restoreSessionStorage(context);
    }

    protected ObjectiveListPage objectiveList() {
        return new ObjectiveListPage(page);
    }

    protected ObjectiveFormPage objectiveForm() {
        return new ObjectiveFormPage(page);
    }

    protected ObjectiveDetailsPage objectiveDetails() {
        return new ObjectiveDetailsPage(page);
    }
}
