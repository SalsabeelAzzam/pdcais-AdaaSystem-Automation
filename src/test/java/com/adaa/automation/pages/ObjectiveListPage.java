package com.adaa.automation.pages;

import com.adaa.automation.utils.TestData;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The objectives list: its table, the search box and the Add button - and the way into the
 * add/edit form and the detail page, which are reached from here rather than by address.
 */
public final class ObjectiveListPage extends BasePage {

    private static final String PATH = "/Objective/Index";
    private static final String ACCESS_DENIED_PATH = "/Error/";

    /** The request the table loads its rows with, as seen in the browser's network traffic. */
    private static final String TABLE_DATA_PATH = "/GetAllObjectivePaginated";
    private static final String SEARCH_PARAMETER = "searchTerm";

    /** Zero-based position of the Objective Name column. */
    private static final int NAME_COLUMN = 1;

    public ObjectiveListPage(Page page) {
        super(page);
    }

    public Locator content() {
        return page.locator("#contentContainer");
    }

    public Locator table() {
        return page.locator("#objectivesTableId");
    }

    public Locator rows() {
        return page.locator("#objectivesTableId tbody tr");
    }

    public Locator addButton() {
        return page.locator("#AddObjectiveBtn");
    }

    /** The table renders its search box with a generated id. */
    public Locator searchBox() {
        return page.locator("input[id^='dt-search-']");
    }

    /** The "no matching records" row shown for an empty result. */
    public Locator emptyMessage() {
        return page.locator("#objectivesTableId td.dataTables_empty, #objectivesTableId td.dt-empty");
    }

    public Locator rowContaining(String text) {
        return rows().filter(new Locator.FilterOptions().setHasText(text));
    }

    public void open() {
        navigateTo(PATH);
    }

    /** The control that opens a row for editing. */
    public Locator editControlIn(Locator row) {
        return row.locator("[class*='edit'], [onclick*='Edit'], a[href*='AddEdit']");
    }

    /** The control that opens a row's read-only detail page. */
    public Locator viewControlIn(Locator row) {
        return row.locator("[class*='viewDetails'], [onclick*='ViewDetails'], a[href*='ViewDetails']");
    }

    /** The control that starts deleting a row; it opens a confirmation dialog. */
    public Locator deleteControlIn(Locator row) {
        return row.locator("[onclick*='StartDeleting']");
    }

    /** The delete confirmation dialog. It does not name the record it is about to delete. */
    public Locator deleteDialog() {
        return page.locator("#deleteRecored");
    }

    // ---- reaching the form and the detail page ------------------------------

    /**
     * Opens the add form the way a user does, from this list's Add button.
     *
     * <p>Never by its address: see {@link ObjectiveFormPage} for why the form must be
     * reached from another page of the application in the same tab.
     */
    public ObjectiveFormPage openAddForm() {
        waitForRows();
        addButton().click();
        page.waitForURL(url -> url.contains("/Objective/AddEdit"));
        ObjectiveFormPage form = new ObjectiveFormPage(page);
        form.waitForReady();
        return form;
    }

    /** Finds the one objective matching {@code text} and opens it for editing. */
    public ObjectiveFormPage openEditFor(String text) {
        editControlIn(onlyRowMatching(text)).first().click();
        page.waitForURL(url -> url.contains("/Objective/AddEdit"));
        ObjectiveFormPage form = new ObjectiveFormPage(page);
        form.waitForReady();
        form.waitForLoadedValues();
        return form;
    }

    /** Finds the one objective matching {@code text} and opens its detail page. */
    public ObjectiveDetailsPage openViewFor(String text) {
        return openView(onlyRowMatching(text));
    }

    /** Opens the detail page of a row already on screen. */
    public ObjectiveDetailsPage openView(Locator row) {
        ObjectiveDetailsPage details = new ObjectiveDetailsPage(page);
        details.openVia(() -> viewControlIn(row).first().click());
        return details;
    }

    // ---- reading the table ---------------------------------------------------

    /** The column headers, in order, as the user sees them. */
    public List<String> columnHeaders() {
        return table().locator("thead th").allInnerTexts().stream().map(String::trim).toList();
    }

    /** The first row holding an objective rather than a placeholder. */
    public Locator firstDataRow() {
        return page.locator("#objectivesTableId tbody tr:not(:has(td.dt-empty)):not(:has(td.dataTables_empty))")
                .first();
    }

    /**
     * One cell of a row, found by its column's header text. Looked up by header rather than
     * by position so that a test says which column it means, in the language on screen.
     */
    public String cellText(Locator row, String header) {
        return row.locator("td").nth(columnIndex(header)).innerText().trim();
    }

    /** Every value in one column, for the rows on the current page. */
    public List<String> columnValues(String header) {
        int index = columnIndex(header);
        Object values = page.locator("#objectivesTableId tbody tr:not(:has(td.dt-empty))")
                .evaluateAll("(rows, i) => rows.map(r => (r.cells[i]?.innerText || '').trim())", index);
        return ((List<?>) values).stream().map(String::valueOf).toList();
    }

    private int columnIndex(String header) {
        List<String> headers = columnHeaders();
        int index = headers.indexOf(header);
        if (index < 0) {
            throw new IllegalArgumentException("no column '" + header + "' in " + headers);
        }
        return index;
    }

    /** Searches for {@code text} and returns its row, failing unless exactly one matches. */
    public Locator onlyRowMatching(String text) {
        search(text);
        Locator matches = rowContaining(text);
        int count = matches.count();
        if (count != 1) {
            throw new AssertionError("expected exactly one objective matching '" + text
                    + "' but the list shows " + count);
        }
        return matches.first();
    }

    // ---- cleaning up ---------------------------------------------------------

    /**
     * Deletes an objective this suite created, if it is still there.
     *
     * <p>Deliberately hard to point at the wrong record: the name must be one
     * {@link TestData} generated, exactly one row may match it, and the confirmation dialog
     * must name it. Anything else is refused rather than guessed at - an objective that was
     * not ours must never be deleted.
     *
     * @return whether an objective was deleted; false when none matched
     */
    public boolean deleteIfPresent(String name) {
        if (!TestData.isGenerated(name)) {
            throw new IllegalArgumentException("refusing to delete '" + name
                    + "': it is not a name this suite generated");
        }
        search(name);
        Locator matches = rowContaining(name);
        int count = matches.count();
        if (count == 0) {
            return false;
        }
        if (count > 1) {
            throw new IllegalStateException("refusing to delete '" + name + "': " + count
                    + " objectives match it");
        }

        // The confirmation dialog does not name the record ("You are about to delete an
        // Objective"), so the record is pinned down before it opens: the row's name cell
        // must be exactly this name, and the delete button being clicked must carry it in
        // the row data it hands to the page.
        Locator row = matches.first();
        String rowName = row.locator("td").nth(NAME_COLUMN).innerText().trim();
        Locator deleteButton = deleteControlIn(row).first();
        String payload = String.valueOf(deleteButton.getAttribute("onclick"));
        if (!rowName.equals(name) || !payload.contains(name)) {
            throw new IllegalStateException("refusing to delete '" + name
                    + "': the matching row is named '" + rowName + "'");
        }

        deleteButton.click();
        deleteDialog().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

        // After a delete the page reloads the list, a couple of seconds later; the check
        // below runs on the reloaded page, not on this one.
        markPageBeforeReload();
        Response deleted = page.waitForResponse(
                response -> response.url().contains("/ObjectiveGW/")
                        && !"GET".equals(response.request().method()),
                () -> deleteDialog().locator("#btnDelete").click());
        if (!deleted.ok()) {
            throw new IllegalStateException("deleting '" + name + "' was refused: HTTP "
                    + deleted.status());
        }
        waitForReloadedPage();

        // The reloaded list restores the search for the record just deleted, so it may be
        // showing only the empty-result row: wait for the table, not for data rows.
        table().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        waitForTableToSettle();
        search(name);
        if (rowContaining(name).count() != 0) {
            throw new IllegalStateException("'" + name + "' is still listed after deleting it");
        }
        return true;
    }

    /** The application's access-denied screen, which a refused page redirects to. */
    public Locator accessDenied() {
        return page.locator(".errorContainer");
    }

    /**
     * Waits for the table to finish its first data load.
     *
     * <p>A page that refuses the user does not leave the table to time out: it redirects to
     * the access-denied screen. Waiting for either one turns that into an immediate failure
     * that names the cause, instead of a timeout that only says the table stayed hidden.
     *
     * <p>Any row is not enough: the table shows a placeholder row ("Loading...", "No data
     * available in table") while it has no data, and its first request goes out before the
     * header has chosen an entity and strategic cycle, so it comes back empty and is followed
     * by a reload. Waiting for a real data row, then for the table to settle, means the row
     * count read afterwards is the reloaded list's, not the placeholder's.
     */
    public void waitForRows() {
        table().or(accessDenied())
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        failIfAccessDenied();

        table().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.waitForFunction(
                "() => { if (location.pathname.startsWith('" + ACCESS_DENIED_PATH + "')) return true;"
                        + " const b = document.querySelector('#objectivesTableId tbody');"
                        + " if (!b) return false;"
                        + " const placeholder = /^(Loading\\.\\.\\.|No data available in table)$/;"
                        + " return [...b.rows].some(r =>"
                        + "   !r.querySelector('td.dt-empty, td.dataTables_empty')"
                        + "   && !placeholder.test(r.innerText.trim())); }",
                null,
                new Page.WaitForFunctionOptions().setTimeout(20_000));
        failIfAccessDenied();

        waitForTableToSettle();
    }

    /** Fails at once, with the cause, when the application has refused this page. */
    public void failIfAccessDenied() {
        if (page.url().contains(ACCESS_DENIED_PATH) || accessDenied().isVisible()) {
            throw new AssertionError("Application redirected to /Error/Index (access denied)"
                    + " instead of showing the objective list. Check authenticated"
                    + " sessionStorage/permissions. Current URL: " + page.url());
        }
    }

    public int rowCount() {
        return rows().count();
    }

    /**
     * Types a search and waits for the table to show its result.
     *
     * <p>Waiting for the table to go quiet is not enough on its own: the table sends the
     * search only after a short delay, and until then the old rows sit perfectly still. So
     * this waits for the table's own data response for exactly this term, then for the
     * redraw that follows it.
     *
     * <p>The application remembers the list's last search and puts it back when the user
     * returns to the list - after a save, for one. Typing that same term again changes
     * nothing, so the table sends nothing and there would be no response to wait for. A
     * term already in the box is therefore cleared first, as a user would clear it, so the
     * search below is always one the table actually runs.
     */
    public void search(String term) {
        if (!searchBox().inputValue().isEmpty()) {
            clearSearch();
        }
        if (term.isEmpty()) {
            return;
        }
        page.waitForResponse(response -> isTableResponseFor(response, term),
                () -> searchBox().fill(term));
        waitForTableToSettle();
    }

    /**
     * Clears the search and waits for the unfiltered table.
     *
     * <p>Clearing the box can first send one more request with the old term still in it;
     * waiting for a response with no term at all skips past that one. An empty box is
     * already showing the unfiltered table, and emptying it again would send nothing.
     */
    public void clearSearch() {
        if (searchBox().inputValue().isEmpty()) {
            waitForTableToSettle();
            return;
        }
        page.waitForResponse(response -> isTableResponseFor(response, ""),
                () -> searchBox().fill(""));
        waitForTableToSettle();
    }

    /**
     * Whether a response is the table's data for the given search term, an empty term
     * meaning no search. The table loads its rows from this endpoint, passing the search
     * box's value as {@code searchTerm} and leaving it out when the box is empty.
     */
    private static boolean isTableResponseFor(Response response, String term) {
        URI url = URI.create(response.url());
        if (url.getPath() == null || !url.getPath().endsWith(TABLE_DATA_PATH)) {
            return false;
        }
        String sent = "";
        String query = url.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                String name = equals < 0 ? pair : pair.substring(0, equals);
                if (SEARCH_PARAMETER.equals(name)) {
                    sent = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                }
            }
        }
        return sent.equals(term);
    }

    /**
     * Waits until the table has actually stopped changing.
     *
     * <p>A fixed sleep is flaky here: the table filters on keystroke, debounces, and
     * issues a request whose latency varies. This instead waits for three things that
     * together mean the table is done - no request in flight, the processing indicator
     * gone, and the row count unchanged across a quiet interval.
     *
     * <p>The interval is inlined into the script rather than passed as an argument: an
     * argument that fails to marshal arrives as undefined, and every comparison against
     * undefined is false, so the wait could never succeed.
     */
    public void waitForTableToSettle() {
        settle(15_000);

        // Each call measures its own quiet interval. A fingerprint left by an earlier call
        // already carries an old timestamp, and would end this wait before it began.
        page.evaluate("() => { delete window.__automationRowFingerprint;"
                + " delete window.__automationRowStamp; }");

        String script = """
                () => {
                    const QUIET = 400;

                    // A refusal ends the wait; failIfAccessDenied below reports it.
                    if (location.pathname.startsWith('/Error/')) return true;

                    const processing = document.querySelector(
                        '#objectivesTableId_processing, .dt-processing');
                    if (processing && processing.offsetParent !== null) return false;

                    const body = document.querySelector('#objectivesTableId tbody');
                    if (!body) return false;

                    // Row count only: cells inside a row update independently (progress
                    // bars, live figures), so their text never settles.
                    const fingerprint = String(body.rows.length);
                    const now = Date.now();

                    if (window.__automationRowFingerprint !== fingerprint) {
                        window.__automationRowFingerprint = fingerprint;
                        window.__automationRowStamp = now;
                        return false;
                    }

                    return (now - window.__automationRowStamp) >= QUIET;
                }
                """;

        page.waitForFunction(script, null,
                new Page.WaitForFunctionOptions().setTimeout(20_000));
        failIfAccessDenied();
    }
}
