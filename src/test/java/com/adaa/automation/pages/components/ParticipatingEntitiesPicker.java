package com.adaa.automation.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

/**
 * The objective form's Participating Entities picker: two lists, "Participating Entities"
 * (available) on one side and "Selected Entity" on the other, with buttons between them.
 *
 * <p>It is used as a user uses it: click a row to mark it, then a button to move the
 * marked rows across - or a double-arrow button to move everything. Each list has its own
 * search box, which hides the rows that do not match rather than removing them.
 *
 * <p>The picker is shown only when the header has the monitoring entity selected and the
 * user may assign entities; elsewhere it stays hidden.
 */
public final class ParticipatingEntitiesPicker {

    private final Page page;

    public ParticipatingEntitiesPicker(Page page) {
        this.page = page;
    }

    public Locator wrapper() {
        return page.locator("#participatingEntitiesWrapper");
    }

    public Locator availableLabel() {
        return page.locator("#left-label");
    }

    public Locator selectedLabel() {
        return page.locator("#right-label");
    }

    public Locator availableSearch() {
        return page.locator("#search-left");
    }

    public boolean isShown() {
        return wrapper().isVisible();
    }

    /** Waits until the available list has been filled from the server. */
    public void waitForEntities() {
        wrapper().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.waitForFunction("() => document.querySelectorAll('#left-list tr, #right-list tr').length > 0");
    }

    /** Names in the available list that are not hidden by its search box. */
    public List<String> available() {
        return names("#left-list tr:visible");
    }

    /** Every name in the selected list. */
    public List<String> selected() {
        return names("#right-list tr");
    }

    /** All available names, including any its search box is hiding. */
    public int availableCount() {
        return page.locator("#left-list tr").count();
    }

    /** Moves the named entities from the available list to the selected one. */
    public void select(String... entities) {
        move("#left-list", ".move-right", entities);
    }

    /** Moves the named entities from the selected list back to the available one. */
    public void remove(String... entities) {
        move("#right-list", ".move-left", entities);
    }

    public void selectAll() {
        wrapper().locator(".move-all-right").click();
    }

    public void removeAll() {
        wrapper().locator(".move-all-left").click();
    }

    public void searchAvailable(String term) {
        availableSearch().fill(term);
    }

    private void move(String fromList, String button, String... entities) {
        for (String entity : entities) {
            row(fromList, entity).click();
        }
        wrapper().locator(button).click();
    }

    /** The row whose cell text is exactly the entity's name. */
    private Locator row(String list, String entity) {
        Locator row = page.locator(list + " tr").filter(new Locator.FilterOptions()
                .setHas(page.getByText(entity, new Page.GetByTextOptions().setExact(true))));
        if (row.count() != 1) {
            throw new IllegalStateException("expected one '" + entity + "' in " + list
                    + " but found " + row.count());
        }
        return row;
    }

    private List<String> names(String rows) {
        return page.locator(rows).allInnerTexts().stream().map(String::trim).toList();
    }
}
