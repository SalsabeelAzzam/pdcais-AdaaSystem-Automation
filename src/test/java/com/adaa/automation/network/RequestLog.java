package com.adaa.automation.network;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records, in order, the requests a page makes to the application's own gateways, as
 * "METHOD /Path" without query strings.
 *
 * <p>It observes the requests the browser really makes; it never makes one itself. That is
 * what lets a test say the application called something, and when - before or after what.
 * Bodies and headers are not kept, so nothing secret a request carries is recorded.
 */
public final class RequestLog implements AutoCloseable {

    private final Page page;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final Consumer<Request> recorder = this::record;

    private RequestLog(Page page) {
        this.page = page;
    }

    /** Starts recording the page's gateway requests. */
    public static RequestLog start(Page page) {
        RequestLog log = new RequestLog(page);
        page.onRequest(log.recorder);
        return log;
    }

    /** Everything recorded so far, in the order the browser sent it. */
    public List<String> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    /** How many times one request was made. */
    public long count(String request) {
        return requests().stream().filter(request::equals).count();
    }

    /** The position of the first such request, or -1 when it was never made. */
    public int indexOf(String request) {
        return requests().indexOf(request);
    }

    @Override
    public void close() {
        page.offRequest(recorder);
    }

    private void record(Request request) {
        String path = URI.create(request.url()).getPath();
        if (path != null && path.contains("GW/")) {
            requests.add(request.method() + " " + path);
        }
    }
}
