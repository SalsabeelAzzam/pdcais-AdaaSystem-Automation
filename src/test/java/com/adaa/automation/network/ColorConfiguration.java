package com.adaa.automation.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.playwright.Response;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

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

    /** Reads an answer in full, waiting for its body to have arrived. */
    public static Answer read(Response response) {
        response.finished();
        return new Answer(response.status(), String.valueOf(response.headerValue("content-type")),
                response.text());
    }

    /** Whether a response is the application's answer to the color configuration request. */
    public static boolean isResponse(Response response) {
        return "GET".equals(response.request().method())
                && ENDPOINT.equalsIgnoreCase(URI.create(response.url()).getPath());
    }

    /** The bands in a color configuration answer. */
    public static List<Band> parse(String json) {
        return GSON.fromJson(json, new TypeToken<List<Band>>() { }.getType());
    }
}
