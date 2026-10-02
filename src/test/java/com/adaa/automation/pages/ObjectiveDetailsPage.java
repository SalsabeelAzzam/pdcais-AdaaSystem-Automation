package com.adaa.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

/**
 * The read-only objective detail page, used to confirm that what was saved is what the
 * application now shows.
 *
 * <p>Reached from the list ({@link ObjectiveListPage#openViewFor}). Every field is a label
 * and a read-only output joined by the field's id, so a field is found by its id and its
 * label through {@code label[for]}.
 */
public final class ObjectiveDetailsPage extends BasePage {

    /** The request the page loads the participating entities with, as seen in the browser's network traffic. */
    private static final String ENTITIES_REQUEST = "/GetByIdObjectiveEntities";

    public ObjectiveDetailsPage(Page page) {
        super(page);
    }

    public Locator content() {
        return page.locator("#objectiveContent");
    }

    /** The objective's name as the page heading. */
    public Locator heading() {
        return page.locator("#headingName");
    }

    public Locator referenceCode() {
        return page.locator("#RefCode");
    }

    public Locator type() {
        return page.locator("#ObjectiveTypeDescArOrEn");
    }

    public Locator name() {
        return page.locator("#objectiveNameArOrEn");
    }

    public Locator description() {
        return page.locator("#ObjectiveDescArOrEn");
    }

    public Locator startDate() {
        return page.locator("#actualStartDate");
    }

    public Locator endDate() {
        return page.locator("#actualEndDate");
    }

    public Locator organisationalUnit() {
        return page.locator("#orgStructureNameArOrEn");
    }

    public Locator responsiblePerson() {
        return page.locator("#respPerson");
    }

    public Locator itemSource() {
        return page.locator("#ItemSourceDescArOrEn");
    }

    public Locator comments() {
        return page.locator("#notes");
    }

    public Locator status() {
        return page.locator("#ItemStatusDescArOrEn");
    }

    /** The Institutional value in the header line: "Yes"/"No", or "نعم"/"لا" in Arabic. */
    public Locator institutional() {
        return page.locator("#InstitutionalItemHeader");
    }

    /** The label the page shows for a field. */
    public Locator labelOf(Locator field) {
        String id = field.getAttribute("id");
        return page.locator("label[for='" + id + "']");
    }

    /** The Participating Entities table; the page shows it only for an objective that has some. */
    public Locator participatingEntitiesTable() {
        return page.locator("#mappedEntitiesDataTable");
    }

    /**
     * The entities listed as participating, one per row. The table is filled by a request
     * of its own after the page loads, so its first row is waited for; call this only for an
     * objective that is expected to have participating entities.
     */
    public List<String> participatingEntityNames() {
        Locator rows = participatingEntitiesTable().locator("tbody tr");
        rows.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        Object names = rows.evaluateAll("rows => rows.map(r => r.cells[0].innerText.trim())");
        return ((List<?>) names).stream().map(String::valueOf).toList();
    }

    /**
     * Runs the action that opens this page and waits until the objective is fully shown.
     *
     * <p>The participating entities come from a request of their own, issued for every
     * objective - with or without entities - and it can take several seconds longer than the
     * rest of the page. The page is not complete until it has answered, so its response is
     * waited for around the action that opens the page.
     */
    public void openVia(Runnable openAction) {
        page.waitForResponse(response -> response.url().contains(ENTITIES_REQUEST), openAction);
        waitForLoaded();
    }

    /** Waits until the objective has been loaded into the page, not merely the page itself. */
    public void waitForLoaded() {
        content().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.waitForFunction("() => (document.getElementById('RefCode')?.innerText || '').trim() !== ''");
    }
}
