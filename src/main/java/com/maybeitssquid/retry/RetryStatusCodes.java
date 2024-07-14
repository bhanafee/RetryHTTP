package com.maybeitssquid.retry;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Predicate to decide whether a retry is allowable based on the HTTP response code. By default, retries are allowed
 * for HTTP response codes in the 1xx and 3xx ranges, as shown in the following table.
 *
 * <table>
 *     <caption>Default retry behavior</caption>
 *     <thead>
 *         <tr><th>Status</th><th>Idempotent</th><th>Non-Idempotent</th></tr>
 *     </thead>
 *     <tbody>
 *          <tr><td>1xx</td><td>true</td><td>true</td></tr>
 *          <tr><td>2xx</td><td>false</td><td>false</td></tr>
 *          <tr><td>3xx</td><td>true</td><td>true</td></tr>
 *          <tr><td>4xx</td><td>false</td><td>false</td></tr>
 *          <tr><td>{@link HttpServletResponse#SC_REQUEST_TIMEOUT 408}</td><td>true</td><td>true</td></tr>
 *          <tr><td>{@link HttpServletResponse#SC_CONFLICT 409}</td><td>true</td><td>true</td></tr>
 *          <tr><td>429</td><td>true</td><td>true</td></tr>
 *          <tr><td>5xx</td><td>true</td><td><strong>false</strong></td></tr>
 *          <tr><td>{@link HttpServletResponse#SC_NOT_IMPLEMENTED 501}</td><td>false</td><td>false</td></tr>
 *          <tr><td>{@link HttpServletResponse#SC_HTTP_VERSION_NOT_SUPPORTED 505}</td><td>false</td><td>false</td></tr>
 *     </tbody>
 * </table>
 */
public class RetryStatusCodes implements Predicate<HttpServletResponse> {

    /**
     * The HTTP status code for a retry being too early.
     */
    public static final int SC_TOO_EARLY = 425;

    /** The HTTP status code for a server complaining of too many requests. */
    public static final int SC_TOO_MANY_REQUESTS = 429;

    /**
     * Offset applied to responses table entry because 0xx status codes are unused.
     */
    private static final int OFFSET = 100;

    /**
     * Default decisions for retry. Note that index is {@link #OFFSET} from status codes.
     */
    private static final boolean[] DEFAULTS = new boolean[500];

    static {
        // 1xx are incomplete results, so the “retry” is to continue processing
        Arrays.fill(DEFAULTS, 100 - OFFSET, 199 - OFFSET, true);
        // 3xx are redirections, so the “retry” is to follow the redirection to a new target
        Arrays.fill(DEFAULTS, 300 - OFFSET, 399 - OFFSET, true);

        // 4xx is client error, but there a few where retry should be safe

        // 408 is request timeout, server confirming it did not receive the request so OK to retry
        DEFAULTS[HttpServletResponse.SC_REQUEST_TIMEOUT - OFFSET] = true;
        // 409 is conflict in resource state, it may resolve upon retry
        DEFAULTS[HttpServletResponse.SC_CONFLICT - OFFSET] = true;
        // 425 is due to risk of replay of data during TLS negotiation, expect server to ensure safe retry
        DEFAULTS[SC_TOO_EARLY - OFFSET] = true;
        // 429 is server-managed throttling of the client, expect server to ensure safe retry
        DEFAULTS[SC_TOO_MANY_REQUESTS - OFFSET] = true;

        // 5xx codes are managed in the constructor based on idempotence.
    }

    /**
     * Decisions for retry
     */
    private final boolean[] responses;

    /**
     * Creates a predicate to decide whether a retry is allowable based on the HTTP response code.
     *
     * @param idempotent whether the service is idempotent. If it is not idempotent, server errors are not retried
     *                   unless explicitly allowed by the {@code retry} parameter.
     * @param additional status codes that are expressly allowed for retry. Codes in this list override defaults.
     * @throws ArrayIndexOutOfBoundsException if any of the retry status codes are out of the range 100..599
     */
    private RetryStatusCodes(final boolean idempotent, final int... additional) {
        if (!idempotent && (additional != null && additional.length == 0)) {
            this.responses = DEFAULTS;
        } else {
            this.responses = Arrays.copyOf(DEFAULTS, DEFAULTS.length);
            if (idempotent) {
                // 5xx codes are retried, with exceptions
                Arrays.fill(this.responses, 500 - OFFSET, 599 - OFFSET, true);
                this.responses[HttpServletResponse.SC_NOT_IMPLEMENTED - OFFSET] = false;
                this.responses[HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED - OFFSET] = false;
            }
            if (additional != null) {
                for (int r : additional) {
                    this.responses[r - OFFSET] = true;
                }
            }
        }
    }

    /**
     * Create an instance that retries only specific codes.
     *
     * @param only HTTP status codes that should be retried.
     * @throws ArrayIndexOutOfBoundsException if any of the retry status codes are out of the range 100..599
     */
    private RetryStatusCodes(final int... only) {
        this.responses = new boolean[500];
        for (int r : only) {
            this.responses[r - OFFSET] = true;
        }
    }

    /**
     * Returns a predicate with default decisions for an idempotent service. Idempotent services allow retries of 5xx
     * HTTP status codes except for {@link HttpServletResponse#SC_NOT_IMPLEMENTED} and
     * {@link HttpServletResponse#SC_HTTP_VERSION_NOT_SUPPORTED}.
     *
     * @param additional status codes that are expressly allowed for retry. Codes in this list override defaults.
     * @return predicate with decisions for an idempotent service.
     */
    public static RetryStatusCodes idempotent(final int... additional) {
        return new RetryStatusCodes(true, additional);
    }

    /**
     * Returns a predicate with default decisions for a non-idempotent service. Non-idempotent services do not allow
     * retries of 5xx HTTP status codes.
     *
     * @param additional status codes that are expressly allowed for retry. Codes in this list override defaults.
     * @return predicate with decisions for a non-idempotent service.
     */
    public static RetryStatusCodes nonIdempotent(final int... additional) {
        return new RetryStatusCodes(false, additional);
    }

    /**
     * Returns a predicate that allows retry for only the explicitly provided status codes.
     *
     * @param retry status codes that are expressly allowed for retry.
     * @return predicate that returns {@code true} for the provided response codes
     */
    public static RetryStatusCodes only(final int... retry) {
        return new RetryStatusCodes(retry);
    }

    /**
     * Returns whether the status code allows a retry.
     *
     * @param code the status code to check.
     * @return whether the status code allows a retry.
     */
    public boolean retries(final int code) {
        try {
            return this.responses[code - OFFSET];
        } catch (final IndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * Tests whether the HTTP response status allows for a retry.
     *
     * @param t the HTTP response
     * @return whether a retry is allowed based on the status code
     */
    @Override
    public boolean test(final HttpServletResponse t) {
        return retries(t.getStatus());
    }
}
