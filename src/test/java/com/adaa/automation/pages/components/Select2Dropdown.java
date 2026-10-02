package com.adaa.automation.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * One of the application's rich dropdowns: a native select hidden behind a select2 control.
 *
 * <p>An option is chosen the way a user chooses it - open the control, click a result -
 * and the choice is then confirmed on the underlying select, because two things make a
 * click unreliable on its own. The list is prefixed with a placeholder entry that is just
 * as clickable and just as useless, and while a remote list is loading a "Searching..." row
 * is rendered that is clickable too.
 */
public final class Select2Dropdown {

    private static final String OPEN_RESULTS = ".select2-container--open .select2-results__option";
    private static final String REAL_RESULTS = OPEN_RESULTS
            + ":not(.loading-results):not(.select2-results__message)";

    private final Page page;
    private final String selectId;

    public Select2Dropdown(Page page, String selectId) {
        this.page = page;
        this.selectId = selectId;
    }

    public Locator select() {
        return page.locator("#" + selectId);
    }

    /** The visible part of the control, which opens it when clicked. */
    public Locator opener() {
        return page.locator("#select2-" + selectId + "-container");
    }

    public boolean isDisabled() {
        return select().isDisabled();
    }

    /** The underlying select's value; empty, or "0", when nothing real is chosen. */
    public String value() {
        Object value = page.evaluate(
                "(id) => { const e = document.getElementById(id); return e ? String(e.value || '') : ''; }",
                selectId);
        return String.valueOf(value);
    }

    public boolean hasRealValue() {
        String value = value();
        return !value.isEmpty() && !"0".equals(value);
    }

    /** The text the control shows for its current choice. */
    public String selectedText() {
        return opener().innerText().trim();
    }

    /** Waits until the control can be used: present and enabled. */
    public void waitUntilEnabled() {
        page.waitForFunction(
                "(id) => { const e = document.getElementById(id); return e && !e.disabled; }",
                selectId);
    }

    /**
     * Chooses the first real option and returns its text.
     *
     * @throws IllegalStateException when no real option could be chosen - the caller's save
     *                               would otherwise be refused for a reason it never sees
     */
    public String chooseFirstOption() {
        waitUntilEnabled();
        open();
        int available = page.locator(REAL_RESULTS).count();

        for (int index = 0; index < available; index++) {
            if (index > 0) {
                // Clicking a result closes the list, so reopen it for each attempt.
                open();
                if (index >= page.locator(REAL_RESULTS).count()) {
                    break;
                }
            }
            page.locator(REAL_RESULTS).nth(index).click();
            if (hasRealValue()) {
                return selectedText();
            }
        }
        throw new IllegalStateException("no option could be chosen in #" + selectId
                + " (" + available + " results offered)");
    }

    /** Opens the control and waits for its results, not its loading row. */
    private void open() {
        opener().click();
        page.waitForFunction(
                "() => {"
                        + " const rows = document.querySelectorAll('" + OPEN_RESULTS + "');"
                        + " return rows.length > 0"
                        + "   && !document.querySelector('" + OPEN_RESULTS + ".loading-results'); }");
    }
}
