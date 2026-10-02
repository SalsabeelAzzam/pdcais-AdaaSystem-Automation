package com.adaa.automation.tests;

import com.adaa.automation.fixtures.AuthenticatedTest;
import com.adaa.automation.pages.AppHeader;
import com.adaa.automation.pages.ObjectiveDetailsPage;
import com.adaa.automation.pages.ObjectiveFormPage;
import com.adaa.automation.pages.ObjectiveListPage;
import com.adaa.automation.pages.components.ParticipatingEntitiesPicker;
import com.adaa.automation.utils.TestData;
import com.microsoft.playwright.Locator;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

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

    @Test(groups = "smoke", description = "OBJ-SMK-04: the list shows every expected column, in order")
    public void listShowsTheExpectedColumns() {
        ObjectiveListPage list = objectiveList();
        list.open();
        list.waitForRows();

        assertEquals(list.columnHeaders(), List.of(
                "Ref Code", "Objective Name", "Objective Type", "Period performance",
                "Annual Performance", "Start Date", "End Date", "Status",
                "Organizational unit", "Responsible Person", "Item Source", "Institutional",
                "Actions"),
                "the objective list columns");
    }

    /**
     * Reads a row, opens it, and checks the detail page shows the same objective. Uses
     * whichever objective the list shows first and changes nothing, so it needs no data of
     * its own. The page's field labels are not compared: several of them read "Target"
     * where "Objective" is meant, which is reported separately.
     */
    @Test(groups = "smoke", description = "OBJ-SMK-05: the detail page opened from a row shows that objective")
    public void viewShowsTheObjectiveOpenedFromTheList() {
        ObjectiveListPage list = objectiveList();
        list.open();
        list.waitForRows();

        Locator row = list.firstDataRow();
        String refCode = list.cellText(row, "Ref Code");
        String name = list.cellText(row, "Objective Name");
        String type = list.cellText(row, "Objective Type");
        String start = list.cellText(row, "Start Date");
        String end = list.cellText(row, "End Date");
        String unit = list.cellText(row, "Organizational unit");
        String person = list.cellText(row, "Responsible Person");
        String source = list.cellText(row, "Item Source");
        String institutional = list.cellText(row, "Institutional");

        ObjectiveDetailsPage details = list.openView(row);

        assertThat(details.referenceCode()).hasText(refCode);
        assertThat(details.heading()).hasText(name);
        assertThat(details.name()).hasText(name);
        assertThat(details.type()).hasText(type);
        assertThat(details.startDate()).hasText(start);
        assertThat(details.endDate()).hasText(end);
        assertThat(details.organisationalUnit()).hasText(unit);
        assertThat(details.responsiblePerson()).hasText(person);
        assertThat(details.itemSource()).hasText(source);
        assertThat(details.institutional()).hasText(institutional);

        // Description and comments are optional and may be empty: check they are shown.
        for (Locator field : List.of(details.referenceCode(), details.type(), details.name(),
                details.description(), details.startDate(), details.endDate(),
                details.organisationalUnit(), details.responsiblePerson(), details.itemSource(),
                details.comments())) {
            assertThat(details.labelOf(field)).isVisible();
        }
    }

    @Test(groups = "smoke", description = "OBJ-SMK-07: saving an empty form is refused and nothing is sent")
    public void emptySaveIsRefusedWithRequiredFieldMessages() {
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();

        ObjectiveFormPage.SaveAttempt attempt = form.attemptRefusedSave();

        assertTrue(attempt.writes().isEmpty(), "an empty form must send nothing, but sent " + attempt.writes());
        assertTrue(attempt.url().contains("/Objective/AddEdit"), "the form must stay open, but is at " + attempt.url());
        for (String field : List.of("Objective Type", "Objective Name in Arabic",
                "Objective Name in English", "Organizational unit", "Responsible Person")) {
            assertEquals(attempt.errors().get(field), "Required Field",
                    "required-field message on '" + field + "'; all messages: " + attempt.errors());
        }
    }

    // ---- validation ------------------------------------------------------------

    /**
     * The name boxes take any length - there is no limit on the input itself - and the form
     * says so as soon as a name is too long, and refuses to save it.
     */
    @Test(groups = "regression", description = "OBJ-NEG-01: names of 256 characters are refused, 255 are accepted")
    public void nameLengthIsLimitedTo255CharactersOnBothNames() {
        String tooLong = "Field can not accept more than 255 characters";
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();

        form.fillNames("ع".repeat(256), "e".repeat(256));
        assertEquals(form.waitForValidationError(form.nameAr()), tooLong);
        assertEquals(form.waitForValidationError(form.nameEn()), tooLong);
        assertEquals(form.nameAr().inputValue().length(), 256, "the Arabic name box keeps what was typed");
        assertEquals(form.nameEn().inputValue().length(), 256, "the English name box keeps what was typed");

        ObjectiveFormPage.SaveAttempt attempt = form.attemptRefusedSave();
        assertTrue(attempt.writes().isEmpty(), "a 256-character name must not be sent, but sent " + attempt.writes());
        assertEquals(attempt.errors().get("Objective Name in Arabic"), tooLong);
        assertEquals(attempt.errors().get("Objective Name in English"), tooLong);

        form.fillNames("ع".repeat(255), "e".repeat(255));
        form.waitForNoValidationError(form.nameAr());
        form.waitForNoValidationError(form.nameEn());
    }

    @Test(groups = "regression", description = "OBJ-NEG-01: an objective with 255-character names is saved in full")
    public void objectiveWith255CharacterNamesIsSaved() {
        String name = createsObjective(TestData.uniqueNameOfLength("auto-objective-max", 255));
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);
        form.saveAndWaitForList();

        list.waitForRows();
        form = list.openEditFor(name);

        assertEquals(form.nameEn().inputValue(), name, "the 255-character English name, read back");
        assertEquals(form.nameAr().inputValue(), name, "the 255-character Arabic name, read back");
    }

    @Test(groups = "regression", description = "OBJ-CRUD-14: Close asks first; No keeps the form, Yes discards it")
    public void closingANewObjectiveAsksBeforeDiscardingIt() {
        // Registered although it should never be saved: if Yes ever did save it, cleanup removes it.
        String name = createsObjective(TestData.uniqueName("auto-objective-close"));
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.nameEn().fill(name);

        form.requestClose();
        assertThat(form.closeDialogMessage())
                .hasText("You are about to leave the screen, all unsaved changes will be discarded.");

        form.stayOnForm();
        assertTrue(form.currentUrl().contains("/Objective/AddEdit"), "No must keep the form open");
        assertEquals(form.nameEn().inputValue(), name, "No must keep what was typed");

        form.requestClose();
        form.leaveForm();

        list.waitForRows();
        list.search(name);
        assertEquals(list.rowContaining(name).count(), 0, "a discarded objective must not be created");
        assertThat(list.emptyMessage()).isVisible();
    }

    // ---- creating and editing ------------------------------------------------

    @Test(groups = "regression",
          description = "A new objective is created through the form and is then visible in the list")
    public void createObjectiveAndVerifyItPersists() {
        String name = createsObjective(TestData.uniqueName("auto-objective"));

        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);

        // Report what the form actually held if the save is refused, so a failure here
        // says why rather than just that it happened.
        String fieldState = form.requiredFieldState();
        form.saveAndWaitForList();

        list.waitForRows();
        list.search(name);

        assertTrue(list.rowContaining(name).count() > 0,
                "the objective just created must appear in the list. Form state at save: "
                        + fieldState);

        // Reopen it and read it back: the check is that the application stored it, not
        // merely that the list redrew with it.
        ObjectiveDetailsPage details = list.openViewFor(name);

        assertThat(details.name()).containsText(name);
    }

    @Test(groups = "regression",
          description = "An objective's description is edited and the change survives a reopen")
    public void editObjectiveDescriptionAndVerifyItPersists() {
        // This test creates the objective it edits. Reusing one left behind by another
        // test would make the two order-dependent.
        String name = createsObjective(TestData.uniqueName("auto-objective-edit"));
        String description = TestData.uniqueName("auto-description");

        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);
        form.saveAndWaitForList();

        // Reopen it by finding it in the list, rather than guessing at an id.
        list.waitForRows();
        form = list.openEditFor(name);
        form.fillDescriptions(description, description);
        form.saveAndWaitForList();

        // Open it once more: the check is that the change was persisted, not merely that
        // the page accepted it.
        list.waitForRows();
        form = list.openEditFor(name);

        assertEquals(form.descriptionEn().inputValue(), description,
                "the edited description must still be there after reopening the objective");
    }

    // ---- Institutional Item ----------------------------------------------------

    /**
     * Precondition: the configured account is an institutional user, and the header starts
     * on the monitoring entity - the context in which the application offers the switch.
     */
    @Test(groups = "regression", description = "OBJ-INST-01: an institutional user on the monitoring entity gets the Institutional switch")
    public void institutionalSwitchIsOfferedOnTheMonitoringEntity() {
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();

        assertThat(form.institutionalSwitch()).isVisible();
        assertThat(form.institutionalSwitch()).isEnabled();
        assertThat(form.institutionalLabel()).hasText("Institutional Item");
        assertThat(form.institutionalSwitch()).not().isChecked();

        form.setInstitutional(true);
        assertThat(form.institutionalSwitch()).isChecked();
        form.setInstitutional(false);
        assertThat(form.institutionalSwitch()).not().isChecked();
    }

    @Test(groups = "regression", description = "OBJ-INST-01 / OBJ-PE-03: on a participating entity neither the switch nor the picker is offered")
    public void institutionalSwitchAndPickerAreHiddenOnAParticipatingEntity() {
        ObjectiveListPage list = objectiveList();
        list.open();
        list.waitForRows();
        String entity = header().selectAnotherEntity();

        ObjectiveFormPage form = list.openAddForm();

        assertThat(form.institutionalSwitch()).isHidden();
        assertTrue(!form.participatingEntities().isShown(),
                "the Participating Entities picker must be hidden on '" + entity + "'");
    }

    @Test(groups = "regression", description = "OBJ-INST-03: an objective saved as institutional shows Yes in the list, the detail page and the form")
    public void institutionalObjectiveShowsYesEverywhere() {
        String name = createsObjective(TestData.uniqueName("auto-objective-inst"));
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);
        form.setInstitutional(true);
        form.saveAndWaitForList();

        list.waitForRows();
        assertEquals(list.cellText(list.onlyRowMatching(name), "Institutional"), "Yes", "list Institutional column");

        ObjectiveDetailsPage details = list.openViewFor(name);
        assertThat(details.institutional()).hasText("Yes");

        list.open();
        list.waitForRows();
        form = list.openEditFor(name);
        assertThat(form.institutionalSwitch()).isChecked();
    }

    /**
     * The switch can be changed on an objective that has no institutional child items. The
     * application locks it once such children are linked; producing that condition needs a
     * KPI, project or service linked to the objective, which belongs to a later phase.
     */
    @Test(groups = "regression", description = "OBJ-INST-04: the Institutional state can be changed on Edit, both ways")
    public void institutionalStateCanBeChangedOnEdit() {
        String name = createsObjective(TestData.uniqueName("auto-objective-inst-edit"));
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);
        form.saveAndWaitForList();

        list.waitForRows();
        assertEquals(list.cellText(list.onlyRowMatching(name), "Institutional"), "No",
                "an objective saved with the switch off");

        form = list.openEditFor(name);
        assertThat(form.institutionalSwitch()).isEnabled();
        assertThat(form.institutionalSwitch()).not().isChecked();
        form.setInstitutional(true);
        form.saveAndWaitForList();

        list.waitForRows();
        assertEquals(list.cellText(list.onlyRowMatching(name), "Institutional"), "Yes", "after switching it on");

        form = list.openEditFor(name);
        assertThat(form.institutionalSwitch()).isChecked();
        form.setInstitutional(false);
        form.saveAndWaitForList();

        list.waitForRows();
        assertEquals(list.cellText(list.onlyRowMatching(name), "Institutional"), "No", "after switching it off again");
    }

    // ---- Participating Entities -------------------------------------------------

    /** Exercises the picker without saving: nothing is created. */
    @Test(groups = "regression", description = "OBJ-PE-03: the Participating Entities picker moves entities both ways and filters")
    public void participatingEntitiesPickerMovesEntitiesBothWays() {
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        ParticipatingEntitiesPicker picker = form.participatingEntities();
        picker.waitForEntities();

        assertThat(picker.availableLabel()).hasText("Participating Entities");
        assertThat(picker.selectedLabel()).hasText("Selected Entity");
        int total = picker.availableCount();
        assertTrue(total > 1, "the picker must offer entities to choose from, offered " + total);
        assertTrue(picker.selected().isEmpty(), "a new objective starts with none selected");

        List<String> two = picker.available().subList(0, 2);
        picker.select(two.toArray(String[]::new));
        assertEquals(Set.copyOf(picker.selected()), Set.copyOf(two), "selected after moving two across");
        assertEquals(picker.availableCount(), total - 2, "available after moving two across");

        picker.remove(two.get(0));
        assertEquals(picker.selected(), List.of(two.get(1)), "selected after moving one back");

        picker.selectAll();
        assertEquals(picker.availableCount(), 0, "available after moving all across");
        assertEquals(picker.selected().size(), total, "selected after moving all across");

        picker.removeAll();
        assertTrue(picker.selected().isEmpty(), "selected after moving all back");
        assertEquals(picker.availableCount(), total, "available after moving all back");

        String term = two.get(0);
        picker.searchAvailable(term);
        List<String> shown = picker.available();
        assertTrue(shown.contains(term), "searching '" + term + "' must show it; shown " + shown);
        assertTrue(shown.size() < total, "searching must hide entities that do not match");
        assertTrue(shown.stream().allMatch(e -> e.toLowerCase().contains(term.toLowerCase())),
                "every entity shown must match '" + term + "': " + shown);
    }

    @Test(groups = "regression", description = "OBJ-PE-04: the participating entities chosen for an objective are still selected when it is reopened")
    public void selectedParticipatingEntitiesPersist() {
        String name = createsObjective(TestData.uniqueName("auto-objective-pe"));
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);
        form.setInstitutional(true);
        ParticipatingEntitiesPicker picker = form.participatingEntities();
        picker.waitForEntities();
        List<String> chosen = picker.available().subList(0, 2);
        picker.select(chosen.toArray(String[]::new));
        form.saveAndWaitForList();

        list.waitForRows();
        form = list.openEditFor(name);
        picker = form.participatingEntities();
        picker.waitForEntities();

        assertEquals(Set.copyOf(picker.selected()), Set.copyOf(chosen), "participating entities after reopening");
    }

    @Test(groups = "regression", description = "OBJ-PE-07: the detail page lists the objective's participating entities")
    public void viewListsTheParticipatingEntities() {
        String name = createsObjective(TestData.uniqueName("auto-objective-pe-view"));
        ObjectiveListPage list = objectiveList();
        list.open();
        ObjectiveFormPage form = list.openAddForm();
        form.fillNewObjective(name, name);
        form.setInstitutional(true);
        ParticipatingEntitiesPicker picker = form.participatingEntities();
        picker.waitForEntities();
        List<String> chosen = picker.available().subList(0, 3);
        picker.select(chosen.toArray(String[]::new));
        form.saveAndWaitForList();

        list.waitForRows();
        ObjectiveDetailsPage details = list.openViewFor(name);

        assertThat(details.participatingEntitiesTable()).isVisible();
        assertEquals(Set.copyOf(details.participatingEntityNames()), Set.copyOf(chosen),
                "participating entities on the detail page");
    }

    // ---- language ------------------------------------------------------------

    /**
     * Switches with the header's own language toggle, as a user does. The toggle also saves
     * Arabic as the account's preferred language, so English is restored whatever happens.
     *
     * <p>The application marks the direction on {@code <body dir>} and leaves
     * {@code <html lang>} as "en" in Arabic too, so the language attribute is reported in
     * the message but not relied on.
     */
    @Test(groups = "regression", description = "The Arabic list renders right to left")
    public void arabicPageRendersRightToLeft() {
        ObjectiveListPage list = objectiveList();
        AppHeader header = header();
        list.open();
        list.waitForRows();

        try {
            header.switchToArabic();
            list.waitForRows();

            assertTrue(header.isRightToLeft(),
                    "the Arabic page must render right to left, but body dir was '"
                            + list.documentDirection() + "' (html lang '"
                            + list.documentLanguage() + "')");
            assertTrue(header.usesRightToLeftStylesheet(),
                    "the Arabic page must load the right-to-left stylesheet");
        } finally {
            header.switchToEnglish();
        }
    }

    @Test(groups = "regression", description = "OBJ-AR-02: in Arabic the list's labels are Arabic and Institutional reads نعم / لا")
    public void arabicListShowsArabicLabelsAndInstitutionalValues() {
        ObjectiveListPage list = objectiveList();
        AppHeader header = header();
        list.open();
        list.waitForRows();

        try {
            header.switchToArabic();
            list.waitForRows();

            assertTrue(header.isRightToLeft(), "the Arabic list must render right to left");
            assertEquals(list.columnHeaders(), List.of(
                    "الرمز التعريفي", "اسم الهدف", "نوع الهدف", "أداء الفترة", "الأداء السنوي",
                    "تاريخ البدء", "تاريخ الانتهاء", "الحالة", "الوحدة التنظيمية", "الشخص المسؤول",
                    "مصدر العنصر", "مؤسسي", "الاجراءات"),
                    "the Arabic column headers");

            List<String> institutional = list.columnValues("مؤسسي");
            assertTrue(!institutional.isEmpty(), "the list must show objectives to read");
            assertTrue(Set.of("نعم", "لا").containsAll(institutional),
                    "Institutional must read نعم or لا in Arabic, but read " + institutional);
        } finally {
            header.switchToEnglish();
        }
    }
}
