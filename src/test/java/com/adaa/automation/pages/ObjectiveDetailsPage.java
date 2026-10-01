package com.adaa.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * The read-only objective detail page, used to confirm that what was saved is what the
 * application now shows.
 */
public final class ObjectiveDetailsPage extends BasePage {

    private static final String PATH = "/Objective/ViewDetails/";

    public ObjectiveDetailsPage(Page page) {
        super(page);
    }

    public Locator content() {
        return page.locator("#objectiveContent");
    }

    public Locator name() {
        return page.locator("#objectiveNameArOrEn");
    }

    public Locator description() {
        return page.locator("#ObjectiveDescArOrEn");
    }

    public Locator type() {
        return page.locator("#ObjectiveTypeDescArOrEn");
    }

    public Locator status() {
        return page.locator("#ItemStatusDescArOrEn");
    }

    public Locator referenceCode() {
        return page.locator("#RefCode");
    }

    public Locator organisationalUnit() {
        return page.locator("#orgStructureNameArOrEn");
    }

    public Locator responsiblePerson() {
        return page.locator("#respPerson");
    }

    public void open(String objectiveId) {
        navigateTo(PATH + objectiveId);
        waitForLoaded();
    }

    public void waitForLoaded() {
        name().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }
}
