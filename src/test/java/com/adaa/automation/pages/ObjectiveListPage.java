package com.adaa.automation.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * The objectives list: its table, the search box and the Add button.
 */
public final class ObjectiveListPage extends BasePage {

    private static final String PATH = "/Objective/Index";
    private static final String ACCESS_DENIED_PATH = "/Error/";

    /** The request the table loads its rows with, as seen in the browser's network traffic. */
    private static final String TABLE_DATA_PATH = "/GetAllObjectivePaginated";
    private static final String SEARCH_PARAMETER = "searchTerm";

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

    /** Opens the list in a specific language, to check the page renders in it. */
    public void openInLanguage(String culture) {
        navigateTo(PATH + "?culture=" + culture);
    }

    /** The control that opens a row for editing. */
    public Locator editControlIn(Locator row) {
        return row.locator("[class*='edit'], [onclick*='Edit'], a[href*='AddEdit']");
    }

    /** The control that opens a row's read-only detail page. */
    public Locator viewControlIn(Locator row) {
        return row.locator("[class*='viewDetails'], [onclick*='ViewDetails'], a[href*='ViewDetails']");
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
     */
    public void search(String term) {
        page.waitForResponse(response -> isTableResponseFor(response, term),
                () -> searchBox().fill(term));
        waitForTableToSettle();
    }

    /**
     * Clears the search and waits for the unfiltered table.
     *
     * <p>Clearing the box can first send one more request with the old term still in it;
     * waiting for a response with no term at all skips past that one.
     */
    public void clearSearch() {
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
