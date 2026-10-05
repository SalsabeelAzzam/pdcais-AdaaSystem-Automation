package com.adaa.automation.fixtures;

import com.adaa.automation.pages.AppHeader;
import com.adaa.automation.pages.ObjectiveDetailsPage;
import com.adaa.automation.pages.ObjectiveFormPage;
import com.adaa.automation.pages.ObjectiveListPage;
import com.microsoft.playwright.Page;
import org.testng.annotations.AfterMethod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for tests that act as a signed-in user.
 *
 * <p>Each test still gets its own browser context; that context is simply seeded with the
 * session established once for the run, so the tests stay independent of each other while
 * none of them has to sign in.
 *
 * <p>A test that creates an objective registers its name, and the objective is deleted
 * when the test finishes, pass or fail. Only names this suite generated are ever deleted;
 * see {@link ObjectiveListPage#deleteIfPresent}.
 */
public abstract class AuthenticatedTest extends BaseTest {

    private final List<String> createdObjectives = new ArrayList<>();

    @Override
    protected Path storageState() {
        return AuthSession.storageState();
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

    protected AppHeader header() {
        return new AppHeader(page);
    }

    /**
     * Records an objective this test is about to create, so it is deleted afterwards.
     * Register before saving: a save that half-succeeds still leaves a record behind.
     */
    protected String createsObjective(String name) {
        createdObjectives.add(name);
        return name;
    }

    /**
     * Deletes what this test created.
     *
     * <p>Runs before {@link BaseTest}'s own after-method - TestNG runs a subclass's
     * after-methods first - so the context is still open. The work is done in a tab of its
     * own: the test's page is left exactly as the test left it, for the failure screenshot.
     * A cleanup problem is reported but never fails the test; it says nothing about whether
     * the behaviour under test works.
     */
    @AfterMethod(alwaysRun = true)
    public void deleteCreatedObjectives() {
        if (createdObjectives.isEmpty() || context == null) {
            return;
        }
        try {
            Page cleanup = context.newPage();
            ObjectiveListPage list = new ObjectiveListPage(cleanup);
            list.open();
            list.waitForRows();
            createdObjectives.forEach(name -> deleteQuietly(list, name));
        } catch (RuntimeException problem) {
            System.err.println("cleanup: could not open the objective list to delete "
                    + createdObjectives + ": " + problem.getMessage());
        } finally {
            createdObjectives.clear();
        }
    }

    private static void deleteQuietly(ObjectiveListPage list, String name) {
        try {
            list.deleteIfPresent(name);
        } catch (RuntimeException problem) {
            System.err.println("cleanup: could not delete objective '" + name + "': "
                    + problem.getMessage());
        }
    }
}
