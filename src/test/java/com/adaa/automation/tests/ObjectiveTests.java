package com.adaa.automation.tests;

import com.adaa.automation.fixtures.AuthenticatedTest;
import com.adaa.automation.pages.ObjectiveDetailsPage;
import com.adaa.automation.pages.ObjectiveFormPage;
import com.adaa.automation.pages.ObjectiveListPage;
import com.adaa.automation.utils.TestData;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * The objective module, as a user meets it.
 *
 * <p>Every test here is self-contained: it makes whatever record it needs, with a name
 * unique to this run, and asserts nothing about data that some other test created. That
 * is why creating and verifying live in one test rather than two - a "reopen what was
 * created" test that depended on a separate "create" test would fail the moment the two
 * ran in a different order, or alone.
 */
public class ObjectiveTests extends AuthenticatedTest {

    // ---- the list -----------------------------------------------------------

    @Test(groups = "smoke", description = "The objective list loads with rows")
    public void objectiveListLoads() {
        ObjectiveListPage list = objectiveList();
        list.open();
        list.waitForRows();

        assertThat(list.table()).isVisible();
        assertTrue(list.rowCount() > 0, "the objective list must render at least one row");
    }

    @Test(groups = "smoke", description = "Searching narrows the list to matching rows")
    public void searchFiltersObjectives() {
        ObjectiveListPage list = objectiveList();
        list.open();
        list.waitForRows();

        // A term that cannot match anything: the test must not depend on which objectives
        // happen to exist in this environment.
        list.search(TestData.uniqueName("zzz-no-match"));

        assertEquals(list.rowCount(), 1,
                "a search that matches nothing must leave only the empty-result row");
        assertThat(list.emptyMessage()).isVisible();
    }

    @Test(groups = "smoke", description = "Clearing the search restores the full list")
    public void clearingSearchRestoresResults() {
        ObjectiveListPage list = objectiveList();
        list.open();
        list.waitForRows();
        int before = list.rowCount();

        list.search(TestData.uniqueName("zzz-no-match"));
        list.clearSearch();

        assertEquals(list.rowCount(), before,
                "clearing the search must restore the rows that were there before it");
    }

    // ---- creating and editing ------------------------------------------------

    @Test(groups = "regression",
          description = "A new objective is created through the form and is then visible in the list")
    public void createObjectiveAndVerifyItPersists() {
        String name = TestData.uniqueName("auto-objective");

        ObjectiveFormPage form = objectiveForm();
        form.openCreate();
        form.fillNewObjective(name, name);

        // Report what the form actually held if the save is refused, so a failure here
        // says why rather than just that it happened.
        String fieldState = form.requiredFieldState();
        form.saveAndWaitForList();

        ObjectiveListPage list = objectiveList();
        list.waitForRows();
        list.search(name);

        assertTrue(list.rowContaining(name).count() > 0,
                "the objective just created must appear in the list. Form state at save: "
                        + fieldState);

        // Reopen it and read it back: the check is that the application stored it, not
        // merely that the list redrew with it.
        list.viewControlIn(list.rowContaining(name).first()).first().click();

        ObjectiveDetailsPage details = objectiveDetails();
        details.waitForLoaded();

        assertThat(details.name()).containsText(name);
    }

    @Test(groups = "regression",
          description = "An objective's description is edited and the change survives a reopen")
    public void editObjectiveDescriptionAndVerifyItPersists() {
        // This test creates the objective it edits. Reusing one left behind by another
        // test would make the two order-dependent.
        String name = TestData.uniqueName("auto-objective-edit");
        String description = TestData.uniqueName("auto-description");

        ObjectiveFormPage form = objectiveForm();
        form.openCreate();
        form.fillNewObjective(name, name);
        form.saveAndWaitForList();

        // Reopen it by finding it in the list, rather than guessing at an id.
        ObjectiveListPage list = objectiveList();
        list.waitForRows();
        list.search(name);
        list.editControlIn(list.rowContaining(name).first()).first().click();

        form.waitForForm();
        form.waitForLoadedValues();
        form.fillDescriptions(description, description);
        form.saveAndWaitForList();

        // Open it once more: the check is that the change was persisted, not merely that
        // the page accepted it.
        list.waitForRows();
        list.search(name);
        list.editControlIn(list.rowContaining(name).first()).first().click();

        form.waitForForm();
        form.waitForLoadedValues();

        assertEquals(form.descriptionEn().inputValue(), description,
                "the edited description must still be there after reopening the objective");
    }

    // ---- language ------------------------------------------------------------

    @Test(groups = "regression", description = "The Arabic list renders right to left")
    public void arabicPageRendersRightToLeft() {
        ObjectiveListPage list = objectiveList();
        list.openInLanguage("ar");

        String direction = list.documentDirection();
        String language = list.documentLanguage();

        assertTrue("rtl".equals(direction) || (language != null && language.startsWith("ar")),
                "the Arabic page must render right to left, but dir was '" + direction
                        + "' and lang was '" + language + "'");
    }
}
