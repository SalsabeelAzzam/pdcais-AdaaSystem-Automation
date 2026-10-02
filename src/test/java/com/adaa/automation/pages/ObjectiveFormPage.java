package com.adaa.automation.pages;

import com.adaa.automation.pages.components.ParticipatingEntitiesPicker;
import com.adaa.automation.pages.components.Select2Dropdown;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The add/edit objective form.
 *
 * <p>This page is never opened by typing its address. It is reached from the objective
 * list - {@link ObjectiveListPage#openAddForm()} and {@link ObjectiveListPage#openEditFor} -
 * because the form decides what to show from the entity the header has selected, and a tab
 * whose first page is this form has not selected one yet: the Institutional Item switch and
 * the Participating Entities picker are then hidden.
 *
 * <p>Its dropdowns are rich controls over a native select, and some of them load their
 * contents only after an earlier choice, so the order of filling matters.
 */
public final class ObjectiveFormPage extends BasePage {

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

    public Select2Dropdown objectiveType() {
        return new Select2Dropdown(page, "objectiveTypeId");
    }

    public Select2Dropdown orgUnit() {
        return new Select2Dropdown(page, "orgUnitId");
    }

    public Select2Dropdown responsiblePerson() {
        return new Select2Dropdown(page, "respPersonId");
    }

    public Locator saveButton() {
        return page.locator("button[onclick='addEditObjective()']");
    }

    public Locator closeButton() {
        return page.locator("button[onclick^='CancelModal']");
    }

    /** The page's own failure dialog, shown when the application refuses a save. */
    public Locator failureMessage() {
        return page.locator("#msgFailed #faildmsg");
    }

    // ---- Institutional Item and Participating Entities -----------------------

    /** The Institutional Item switch. Hidden unless an institutional user is on the monitoring entity. */
    public Locator institutionalSwitch() {
        return page.locator("#institutionalItem");
    }

    public Locator institutionalLabel() {
        return page.locator("label[for='institutionalItem']");
    }

    /** Turns the Institutional Item switch on or off by clicking it, if it is not already so. */
    public void setInstitutional(boolean on) {
        institutionalSwitch().setChecked(on);
    }

    public ParticipatingEntitiesPicker participatingEntities() {
        return new ParticipatingEntitiesPicker(page);
    }

    // ---- refused saves --------------------------------------------------------

    /**
     * Clicks Save on a form the application is expected to refuse, and reports what happened.
     *
     * <p>The refusal is waited for, not assumed: the wait ends when a validation message is
     * shown. Every request that could write is recorded meanwhile, so a test can say not only
     * that the form complained but that nothing was sent.
     */
    public SaveAttempt attemptRefusedSave() {
        List<String> writes = new ArrayList<>();
        Consumer<Request> recorder = request -> {
            if (!"GET".equals(request.method()) && request.url().contains("/ObjectiveGW/")) {
                writes.add(request.method() + " " + request.url());
            }
        };
        page.onRequest(recorder);
        try {
            save();
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.fv-plugins-message-container')]"
                            + "  .some(e => e.offsetParent !== null && e.innerText.trim() !== '')");
        } finally {
            page.offRequest(recorder);
        }
        return new SaveAttempt(validationErrors(), List.copyOf(writes), page.url());
    }

    /**
     * What a refused save left behind.
     *
     * @param errors validation message per field label, in form order
     * @param writes requests that could have written something; empty when nothing was sent
     * @param url    where the browser was afterwards
     */
    public record SaveAttempt(Map<String, String> errors, List<String> writes, String url) {
    }

    /** Validation messages the form is showing, keyed by their field's label. */
    public Map<String, String> validationErrors() {
        Object pairs = page.evaluate(
                "() => [...document.querySelectorAll('.fv-plugins-message-container')]"
                        + "  .filter(e => e.offsetParent !== null && e.innerText.trim() !== '')"
                        + "  .map(e => {"
                        + "    const label = e.closest('.aegov-form-control, .form-group, [class*=col-]')"
                        + "      ?.querySelector('label')?.innerText.replace(/[*\\s]+$/, '').trim();"
                        + "    return [label || '?', e.innerText.trim()];"
                        + "  })");
        Map<String, String> errors = new LinkedHashMap<>();
        for (Object pair : (List<?>) pairs) {
            List<?> entry = (List<?>) pair;
            errors.put(String.valueOf(entry.get(0)), String.valueOf(entry.get(1)));
        }
        return errors;
    }

    /**
     * Waits for a field to show a validation message and returns it. The form re-validates a
     * field on every input, asynchronously, so the message is waited for, not read at once.
     */
    public String waitForValidationError(Locator field) {
        Locator message = messageFor(field);
        page.waitForFunction("(e) => e.offsetParent !== null && e.innerText.trim() !== ''",
                message.elementHandle());
        return message.innerText().trim();
    }

    /** Waits until a field shows no validation message. */
    public void waitForNoValidationError(Locator field) {
        page.waitForFunction("(e) => e.offsetParent === null || e.innerText.trim() === ''",
                messageFor(field).elementHandle());
    }

    /** The validation message container that belongs to one field. */
    private Locator messageFor(Locator field) {
        return field.locator("xpath=ancestor::div[contains(@class,'aegov-form-control')][1]"
                + "//div[contains(@class,'fv-plugins-message-container')]");
    }

    // ---- leaving the form -----------------------------------------------------

    /** The "unsaved changes will be discarded" confirmation the Close button opens. */
    public Locator closeDialog() {
        return page.locator("#cancelMessage");
    }

    public Locator closeDialogMessage() {
        return page.locator("#cancelMessage #cancelmsg");
    }

    /** Clicks Close and waits for its confirmation. */
    public void requestClose() {
        closeButton().click();
        closeDialog().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    /** Answers No: the dialog goes away and the form stays as it was. */
    public void stayOnForm() {
        closeDialog().locator("button.btn-secondary[data-bs-dismiss='modal']").click();
        closeDialog().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    /** Answers Yes: the form is discarded and the list is shown again. */
    public void leaveForm() {
        closeDialog().locator("#proceedBtn").click();
        page.waitForURL(url -> url.contains("/Objective/Index"));
    }

    public void waitForForm() {
        form().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    /**
     * Waits until the form can be filled in: visible, and its objective types loaded. The
     * types arrive in a request of their own after the form is shown, and choosing a type
     * before then silently chooses nothing.
     */
    public void waitForReady() {
        waitForForm();
        page.waitForFunction(
                "() => { const e = document.getElementById('objectiveTypeId');"
                        + " return e && e.options.length > 1; }");
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
     * unit and the responsible person. The dates are filled by the page itself from the
     * strategic cycle once a type is chosen.
     *
     * <p>The order is the page's: the type sets the dates, and the responsible-person list
     * is enabled and loaded only once an organisational unit has been chosen.
     *
     * @throws IllegalStateException when a required field is still empty afterwards
     */
    public void fillNewObjective(String arabicName, String englishName) {
        fillNames(arabicName, englishName);
        objectiveType().chooseFirstOption();
        page.waitForFunction("() => document.getElementById('startDate')?.value !== ''");
        orgUnit().chooseFirstOption();
        responsiblePerson().chooseFirstOption();

        if (!objectiveType().hasRealValue() || !orgUnit().hasRealValue()
                || !responsiblePerson().hasRealValue()) {
            throw new IllegalStateException("the new objective's required fields are not all set: "
                    + requiredFieldState());
        }
    }

    public void save() {
        saveButton().click();
    }

    /**
     * Saves and waits for the application to return to the list, which it does on success.
     *
     * <p>A refused save does not navigate anywhere, so waiting only for the list would turn
     * every refusal into a long timeout that says nothing. This waits for whichever comes
     * first - the list, a validation message on the form, or the application's failure
     * dialog - and fails at once, with the page's own words, for the latter two.
     */
    public void saveAndWaitForList() {
        save();
        page.waitForFunction(
                "() => location.pathname.startsWith('/Objective/Index')"
                        + " || [...document.querySelectorAll('.fv-plugins-message-container')]"
                        + "      .some(e => e.offsetParent !== null && e.innerText.trim() !== '')"
                        + " || (document.querySelector('#msgFailed.show') !== null)",
                null,
                new Page.WaitForFunctionOptions().setTimeout(30_000));

        if (!page.url().contains("/Objective/Index")) {
            throw new AssertionError("the objective was not saved. Validation: "
                    + validationMessages() + "; failure dialog: '" + failureText()
                    + "'; fields: " + requiredFieldState());
        }
    }

    /** Every validation message the form is showing, as "label: message". */
    public String validationMessages() {
        Object messages = page.evaluate(
                "() => [...document.querySelectorAll('.fv-plugins-message-container')]"
                        + "  .filter(e => e.offsetParent !== null && e.innerText.trim() !== '')"
                        + "  .map(e => {"
                        + "    const label = e.closest('.aegov-form-control, .form-group, [class*=col-]')"
                        + "      ?.querySelector('label')?.innerText.replace(/[*\\s]+$/, '').trim();"
                        + "    return (label || '?') + ': ' + e.innerText.trim();"
                        + "  }).join(' | ')");
        return String.valueOf(messages);
    }

    private String failureText() {
        Locator message = failureMessage();
        return message.count() > 0 && message.isVisible() ? message.innerText().trim() : "";
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
}
