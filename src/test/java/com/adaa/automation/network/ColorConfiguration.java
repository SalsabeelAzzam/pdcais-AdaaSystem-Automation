package com.adaa.automation.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The application's color configuration: the performance bands - a percentage range and the
 * color it is shown in - that every performance figure on every page is colored by.
 *
 * <p>Observed behaviour, as a browser sees it:
 * <ul>
 *   <li>{@code GET /ColorConfigGW/GetColorConfig}, no parameters, no body. The browser sends
 *       only {@code Content-Type: application/json}; it is authorised by the session
 *       cookie, and the gateway forwards the signed-in user's token to the API.</li>
 *   <li>Called once per sign-in, by the sign-in page, after the credentials have been
 *       accepted and before the page navigates on. The page waits for it: it moves on only
 *       when the answer is a non-empty list, which it keeps in session storage under
 *       {@code colorConfig} for the pages that follow. Those pages read that copy; none of
 *       them request it again.</li>
 *   <li>The answer is a JSON array of bands: {@code id}, {@code descEn}, {@code descAr},
 *       {@code minPercentage}, {@code maxPercentage}, {@code color}, plus lookup metadata.</li>
 * </ul>
 */
public final class ColorConfiguration {

    /** The gateway the browser calls. */
    public static final String ENDPOINT = "/ColorConfigGW/GetColorConfig";

    /** The request, as {@link RequestLog} records it. */
    public static final String REQUEST = "GET " + ENDPOINT;

    /** The session storage key the sign-in page keeps the answer under. */
    public static final String SESSION_KEY = "colorConfig";

    private static final Gson GSON = new Gson();

    private ColorConfiguration() {
    }

    /** One performance band: values from {@code minPercentage} to {@code maxPercentage} show in {@code color}. */
    public record Band(int id, String descEn, String descAr,
                       BigDecimal minPercentage, BigDecimal maxPercentage, String color) {
    }

    /**
     * The application's answer, read while it is still available: the sign-in page
     * navigates away right after receiving it, and a browser may drop a page's response
     * bodies once it has left that page.
     */
    public record Answer(int status, String contentType, String body) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public List<Band> bands() {
            return parse(body);
        }
    }

    /**
     * Runs {@code action} and returns the color configuration answer it caused the page to
     * receive.
     *
     * <p>The answer is captured by routing the request: it is fetched on the page's behalf,
     * its body kept, and the page fulfilled with that same response. Reading the body from a
     * {@link com.microsoft.playwright.Response} instead races the sign-in page's navigation -
     * once the page has moved on Chromium drops the body ({@code Network.getResponseBody:
     * No resource with given identifier found}).
     */
    public static Answer capture(Page page, Runnable action, double timeoutMs) {
        AtomicReference<Answer> answer = new AtomicReference<>();
        Predicate<String> matcher = url -> ENDPOINT.equalsIgnoreCase(URI.create(url).getPath());
        Consumer<Route> handler = route -> {
            if (!"GET".equals(route.request().method()) || answer.get() != null) {
                route.fallback();
                return;
            }
            APIResponse fetched = route.fetch();
            answer.set(new Answer(fetched.status(),
                    String.valueOf(fetched.headers().get("content-type")), fetched.text()));
            route.fulfill(new Route.FulfillOptions().setResponse(fetched));
        };
        page.route(matcher, handler);
        try {
            action.run();
            page.waitForCondition(() -> answer.get() != null,
                    new Page.WaitForConditionOptions().setTimeout(timeoutMs));
            return answer.get();
        } finally {
            page.unroute(matcher, handler);
        }
    }

    /** The bands in a color configuration answer. */
    public static List<Band> parse(String json) {
        return GSON.fromJson(json, new TypeToken<List<Band>>() { }.getType());
    }
}
