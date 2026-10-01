package com.adaa.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * The add/edit objective form.
 *
 * <p>Two things about this screen shape the code below. Its dropdowns are rich controls
 * that replace the native select, so an option is chosen through their own behaviour
 * rather than by setting the element's value; and several of them load their contents
 * only after an earlier choice has been made, so the order of filling matters.
 */
public final class ObjectiveFormPage extends BasePage {

    private static final String CREATE_PATH = "/Objective/AddEdit";

    public ObjectiveFormPage(Page page) {
        super(page);
    }

    public Locator form() {
        return page.locator("#objectiveForm");
    }

    public Locator nameAr() {
        return page.locator("#nameAr");
    }

    public Locator nameEn() {
        return page.locator("#nameEn");
    }

    public Locator descriptionAr() {
        return page.locator("#descAr");
    }

    public Locator descriptionEn() {
        return page.locator("#descEn");
    }

    public Locator startDate() {
        return page.locator("#startDate");
    }

    public Locator endDate() {
        return page.locator("#endDate");
    }

    public Locator objectiveType() {
        return page.locator("#objectiveTypeId");
    }

    public Locator orgUnit() {
        return page.locator("#orgUnitId");
    }

    public Locator responsiblePerson() {
        return page.locator("#respPersonId");
    }

    public Locator saveButton() {
        return page.locator("button[onclick='addEditObjective()']");
    }

    public void openCreate() {
        navigateTo(CREATE_PATH);
        waitForForm();
    }

    public void openEdit(String objectiveId) {
        navigateTo(CREATE_PATH + "/" + objectiveId);
        waitForForm();
    }

    public void waitForForm() {
        form().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    /** Waits until the form has been populated with an existing objective's values. */
    public void waitForLoadedValues() {
        page.waitForFunction(
                "() => { const e = document.getElementById('nameEn');"
                        + " return e && e.value.trim().length > 0; }",
                null,
                new Page.WaitForFunctionOptions().setTimeout(30_000));
    }

    public void fillNames(String arabic, String english) {
        nameAr().fill(arabic);
        nameEn().fill(english);
    }

    public void fillDescriptions(String arabic, String english) {
        descriptionAr().fill(arabic);
        descriptionEn().fill(english);
    }

    /**
     * Fills everything a new objective needs: both names, the type, the organisational
     * unit and the responsible person. The dates are derived by the page itself from the
     * selected type.
     *
     * <p>The order is not arbitrary. Choosing the type is what fills the date bounds, and
     * the responsible-person list is only populated once an organisational unit has been
     * chosen.
     */
    public void fillNewObjective(String arabicName, String englishName) {
        fillNames(arabicName, englishName);

        chooseFirstOption("objectiveTypeId");
        page.waitForTimeout(1200);

        // This dropdown is rebuilt once the page knows which entity and cycle it is
        // working in, so a first attempt can land on an earlier, empty instance.
        for (int attempt = 0; attempt < 4; attempt++) {
            if (chooseFirstOption("orgUnitId")) {
                break;
            }
            page.waitForTimeout(1500);
        }

        // Choosing the unit is what enables and loads the person list.
        try {
            page.waitForFunction(
                    "() => { const e = document.getElementById('respPersonId');"
                            + " return e && !e.disabled; }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(15_000));
            chooseFirstOption("respPersonId");
        } catch (RuntimeException noUnitChosen) {
            // No organisational unit was available, so there is nobody to choose. The
            // save will be refused and the test will say so.
        }
    }

    public void save() {
        saveButton().click();
    }

    /** Saves and waits for the application to return to the list, which it does on success. */
    public void saveAndWaitForList() {
        save();
        page.waitForURL(url -> url.contains("/Objective/Index"),
                new Page.WaitForURLOptions().setTimeout(30_000));
    }

    /** The current value of every field a new objective requires, for a clear failure message. */
    public String requiredFieldState() {
        Object state = page.evaluate(
                "() => ['nameEn', 'nameAr', 'startDate', 'endDate',"
                        + "        'objectiveTypeId', 'orgUnitId', 'respPersonId']"
                        + "    .map(id => {"
                        + "        const e = document.getElementById(id);"
                        + "        return id + '=' + (e ? String(e.value || '') : '<missing>');"
                        + "    })"
                        + "    .join(', ')");
        return String.valueOf(state);
    }

    /**
     * Chooses the first real option in a dropdown.
     *
     * @return whether a real value was chosen
     */
    public boolean chooseFirstOption(String selectId) {
        return optionCount(selectId) == 0
                ? chooseFromRemoteDropdown(selectId)
                : chooseFromNativeSelect(selectId);
    }

    private int optionCount(String selectId) {
        Object count = page.evaluate(
                "(id) => { const e = document.getElementById(id);"
                        + " return e ? e.options.length : 0; }",
                selectId);
        return ((Number) count).intValue();
    }

    /**
     * For dropdowns that already hold their options: set the value and fire the change
     * event, which is exactly what the page's own code does. Driving the popup for these
     * is slower and no more faithful.
     */
    private boolean chooseFromNativeSelect(String selectId) {
        Object chosen = page.evaluate(
                "(id) => {"
                        + "    const select = document.getElementById(id);"
                        + "    if (!select) return null;"
                        + "    const option = Array.from(select.options)"
                        + "        .find(o => o.value && o.value !== '0');"
                        + "    if (!option) return null;"
                        + "    select.value = option.value;"
                        + "    if (window.jQuery) { window.jQuery(select).trigger('change'); }"
                        + "    else { select.dispatchEvent(new Event('change', { bubbles: true })); }"
                        + "    return option.value;"
                        + "}",
                selectId);
        return chosen != null;
    }

    /**
     * For dropdowns that fetch their contents when opened, the popup has to be driven.
     *
     * <p>Two things make this awkward: a "Searching..." row is rendered while the fetch is
     * in flight and is itself clickable, and the list is prefixed with a placeholder entry
     * that is equally selectable and equally useless. So wait for the loading row to go,
     * then click results until the underlying control actually holds a real value.
     */
    private boolean chooseFromRemoteDropdown(String selectId) {
        Locator opener = page.locator("#select2-" + selectId + "-container");
        if (opener.count() == 0 || page.locator("#" + selectId).isDisabled()) {
            return false;
        }

        opener.click();

        try {
            page.waitForFunction(
                    "() => {"
                            + "    const rows = document.querySelectorAll('.select2-results__option');"
                            + "    if (rows.length === 0) return false;"
                            + "    return !document.querySelector('.select2-results__option.loading-results');"
                            + "}",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(20_000));
        } catch (RuntimeException neverLoaded) {
            page.keyboard().press("Escape");
            return false;
        }

        String resultSelector = ".select2-results__option:not(.select2-results__message)";
        int available = page.locator(resultSelector).count();

        for (int index = 0; index < available; index++) {
            if (index > 0) {
                // Clicking a result closes the popup, so reopen for each attempt.
                opener.click();
                page.waitForTimeout(800);
                if (index >= page.locator(resultSelector).count()) {
                    break;
                }
            }

            page.locator(resultSelector).nth(index).click();

            if (hasRealValue(selectId)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasRealValue(String selectId) {
        Object value = page.evaluate(
                "(id) => { const e = document.getElementById(id);"
                        + " return e ? String(e.value || '') : ''; }",
                selectId);
        String chosen = String.valueOf(value);
        return !chosen.isEmpty() && !"0".equals(chosen);
    }
}
