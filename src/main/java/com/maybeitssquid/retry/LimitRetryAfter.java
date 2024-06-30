package com.maybeitssquid.retry;

import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Predicate to prevent a retry if there is a {@code Retry-After} header that specifies too long a wait interval.
 */
public class LimitRetryAfter implements Predicate<HttpServletResponse> {

    /**
     * The parser to read Retry-After headers from the response.
     */
    private final Function<HttpServletResponse, Optional<Duration>> parser;

    /**
     * The maximum wait interval
     */
    private final Duration maximum;

    /**
     * Creates a predicate to limit intervals requested by an HTTP Retry-After header.
     *
     * @param maximum the maximum wait interval
     * @param parser  the parser for the headers.
     */
    public LimitRetryAfter(final Duration maximum, final Function<HttpServletResponse, Optional<Duration>> parser) {
        this.parser = parser;
        this.maximum = maximum;
    }

    /**
     * Creates a predicate to limit intervals requested by an HTTP Retry-After header, using an extended parser.
     *
     * @param limit the maximum wait interval
     * @see RetryAfterParser#extended()
     */
    public static LimitRetryAfter maximum(final Duration limit) {
        return new LimitRetryAfter(limit, RetryAfterParser.extended());
    }

    /**
     * Convenience wrapper to express the limit in milliseconds.
     *
     * @param milliseconds the limit in milliseconds.
     * @see #maximum(Duration)
     */
    public static LimitRetryAfter maximum(final long milliseconds) {
        return maximum(Duration.ofMillis(milliseconds));
    }

    /**
     * Gets the maximum wait interval allowed.
     *
     * @return the maximum wait interval allowed.
     */
    public Duration getMaximum() {
        return this.maximum;
    }

    /**
     * Tests whether there is a Retry-After header that would exceed the limit.
     *
     * @param t the HTTP response to check for a Retry-After header
     * @return false if there is a Retry-After header and the requested duration would exceed the limit.
     */
    @Override
    public boolean test(final HttpServletResponse t) {
        return this.parser.apply(t).map(ra -> ra.compareTo(getMaximum()) < 1).orElse(true);
    }
}
