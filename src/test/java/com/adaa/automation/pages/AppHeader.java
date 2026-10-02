package com.adaa.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

/**
 * The header shown on every signed-in page, and its language toggle.
 *
 * <p>The toggle is used exactly as a user uses it. That has a consequence worth knowing:
 * besides switching the page, the application saves the choice as the account's preferred
 * language, so it outlives the browser session. A test that switches to Arabic must switch
 * back to English when it finishes, pass or fail - otherwise every later sign-in with the
 * account starts in Arabic.
 *
 * <p>The application marks the direction on {@code <body dir>}, and leaves
 * {@code <html lang>} as "en" in both languages, so the direction is read from the body.
 */
public final class AppHeader extends BasePage {

    public AppHeader(Page page) {
        super(page);
    }

    /** The globe button; it shows the language it will switch to. */
    public Locator languageToggle() {
        return page.locator("#toggleButton");
    }

    public boolean isRightToLeft() {
        return "rtl".equals(page.locator("body").getAttribute("dir"));
    }

    /** Whether the right-to-left stylesheet is the one in use. */
    public boolean usesRightToLeftStylesheet() {
        Object rtl = page.evaluate(
                "() => [...document.querySelectorAll('link[rel=stylesheet]')]"
                        + "  .some(l => /bootstrap\\.rtl/.test(l.href))");
        return Boolean.TRUE.equals(rtl);
    }

    public void switchToArabic() {
        if (!isRightToLeft()) {
            toggleAndWaitFor("rtl");
        }
    }

    public void switchToEnglish() {
        if (isRightToLeft()) {
            toggleAndWaitFor("ltr");
        }
    }

    /**
     * Clicks the toggle and waits for the page it reloads into. The toggle flips
     * {@code body[dir]} on the current page before reloading, so the direction alone does
     * not prove the switch has finished.
     */
    private void toggleAndWaitFor(String direction) {
        waitForReloadAfter(() -> languageToggle().click());
        page.waitForFunction("(dir) => document.body && document.body.dir === dir", direction);
    }

    // ---- entity context -------------------------------------------------------

    /** The header's Entity selector, which decides whose objectives the pages work with. */
    public Locator entitySelector() {
        return page.locator("#entityDropdownHeader");
    }

    /** The name of the entity the header has selected. */
    public String selectedEntity() {
        return String.valueOf(entitySelector().evaluate("s => s.selectedOptions[0]?.text.trim() || ''"));
    }

    /**
     * Selects another entity than the current one, as a user does, and returns its name.
     * The page reloads into the new entity's context. The choice is held by this browser
     * tab only; nothing is saved to the account.
     */
    public String selectAnotherEntity() {
        page.waitForFunction("() => document.getElementById('entityDropdownHeader')?.options.length > 1");
        String other = String.valueOf(entitySelector().evaluate(
                "s => [...s.options].find(o => o.value !== s.value).text.trim()"));
        waitForReloadAfter(() -> entitySelector().selectOption(new SelectOption().setLabel(other)));
        return other;
    }
}
