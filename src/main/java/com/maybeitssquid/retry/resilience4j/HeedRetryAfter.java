package com.maybeitssquid.retry.resilience4j;

import com.maybeitssquid.retry.RetryAfterParser;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import static io.github.resilience4j.retry.RetryConfig.DEFAULT_WAIT_DURATION;

/**
 * Function that extends the wait interval specified by a wrapped {@link IntervalBiFunction} so that it respects any
 * {@code Retry-After} header that was returned in an HTTP response. If a {@code Retry-After} header is found, returns
 * the maximum of the wait returned by the wrapped function and the wait indicated by the header. If no header is found,
 * returns only the result of the wrapped function.
 */
public class HeedRetryAfter implements IntervalBiFunction<HttpServletResponse> {

    /**
     * The parser to read Retry-After headers from the response.
     */
    private final Function<HttpServletResponse, Optional<Duration>> parser;

    /**
     * The wrapped function to determine the wait interval without considering the header.
     */
    private final IntervalBiFunction<HttpServletResponse> wrapped;

    public HeedRetryAfter(final IntervalBiFunction<HttpServletResponse> wrapped, final Function<HttpServletResponse, Optional<Duration>> parser) {
        this.wrapped = wrapped;
        this.parser = parser;
    }

    public HeedRetryAfter(final IntervalBiFunction<HttpServletResponse> wrapped) {
        this(wrapped, RetryAfterParser.extended());
    }

    /**
     * Heeds the {@code Retry-After} header with a required minimum wait.
     *
     * @param milliseconds the minimum wait in milliseconds.
     * @return function that waits for the {@code Retry-After} interval with a minimum.
     */
    public static HeedRetryAfter atLeast(final long milliseconds) {
        return new HeedRetryAfter((t, u) -> milliseconds);
    }

    /**
     * Heeds the {@code Retry-After} header with a required minimum wait.
     *
     * @param minimum the minimum wait.
     * @return function that waits for the {@code Retry-After} interval with a minimum.
     */
    public static HeedRetryAfter atLeast(final Duration minimum) {
        return new HeedRetryAfter((t, u) -> minimum.toMillis());
    }

    /**
     * Heeds the {@code Retry-After} header with a wait of
     * {@link io.github.resilience4j.retry.RetryConfig#DEFAULT_WAIT_DURATION} if none is required by a header.
     *
     * @return function that waits for the {@code Retry-After} interval with a default minimum.
     */
    public static HeedRetryAfter defaulted() {
        return atLeast(DEFAULT_WAIT_DURATION);
    }

    /**
     * Heeds the {@code Retry-After} header with no wait if none is required by a header.
     *
     * @return function that waits for the {@code Retry-After} interval.
     */
    public static HeedRetryAfter heed() {
        return atLeast(0L);
    }

    /**
     * Extends an {@link IntervalBiFunction} to heed any {@code Retry-After} header in the response.
     *
     * @param extending the function to extend.
     * @return a function that extends the wait interval to heed the {@code Retry-After} header.
     */
    public static HeedRetryAfter heed(final IntervalBiFunction<HttpServletResponse> extending) {
        return new HeedRetryAfter(extending);
    }

    /**
     * Checks for an HTTP response with a {@code Retry-After} header, and computes the required wait interval.
     *
     * @param t the retry count, which is passed to the wrapped function.
     * @param u the result to evaluate
     * @return the maximum of the wait interval specified by the wrapped function and the interval indicated by the
     * {@code Retry-After} header.
     */
    @Override
    public Long apply(final Integer t, final Either<Throwable, HttpServletResponse> u) {
        final Long b = this.wrapped.apply(t, u);
        if (u.isRight()) {
            final Optional<Long> retryAfter = this.parser.apply(u.get()).map(Duration::toMillis);
            return retryAfter.map(ra -> b == null ? ra : Long.max(ra, b)).orElse(b);
        } else {
            return b;
        }
    }
}
