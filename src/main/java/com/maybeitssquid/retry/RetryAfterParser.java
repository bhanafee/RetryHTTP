package com.maybeitssquid.retry;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Parses an HTTP Retry-After header to determine how long to wait before retrying. Parsing of the header is based
 * on a superset of RFC 7231. Anything permitted by that RFC should parse correctly. Minor variations may be allowed,
 * such as fractional seconds, optional day name, single-digit hour/minute/second, optional seconds, extra whitespace
 * before the hour.
 */
public class RetryAfterParser implements Function<HttpServletResponse, Optional<Duration>> {

    private final List<Function<String, Optional<Duration>>> parsers;

    public static final Logger LOGGER = LoggerFactory.getLogger(RetryAfterParser.class);

    public static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final DateTimeFormatter RFC_850_FORMATTER = DateTimeFormatter.ofPattern("[EEEE, ]d-MMM-yy H:m[:s] z");
    private static final DateTimeFormatter ASCTIME_FORMATTER = DateTimeFormatter.ofPattern("[E ]MMM [ ]d H:m[:s] yyyy");

    @SafeVarargs
    private RetryAfterParser(final Function<String, Optional<Duration>>... parsers) {
        this.parsers = Arrays.asList(parsers);
    }

    /**
     * Parser that accepts only {@code Retry-After} headers with an integer number of seconds to wait. If
     * the header contains some other value, such as a date, it is ignored.
     *
     * @return Parser that accepts only {@code delay-seconds} in the {@code Retry-After} header.
     */
    public static RetryAfterParser secondsOnly() {
        return new RetryAfterParser(STRICT_SECONDS);
    }

    /**
     * Parser that accepts {@code Retry-After} headers with a potentially decimal number of seconds to wait. If
     * the header contains some other value, such as a date, it is ignored.
     *
     * @return Parser that accepts only seconds in the {@code Retry-After} header.
     */
    public static RetryAfterParser decimalSeconds() {
        // Attempt STRICT_SECONDS first, because the DECIMAL_SECONDS parse is expensive
        return new RetryAfterParser(STRICT_SECONDS, DECIMAL_SECONDS);
    }

    /**
     * Parser that accepts only {@code Retry-After} headers that meet a reasonably strict interpretation of
     * RFC-9110.
     *
     * @param clock the clock to use to compute offsets when the header is a date. Useful for testing.
     * @return Parser for RFC-9110 {@code Retry-After} headers.
     */
    public static RetryAfterParser strict(final InstantSource clock) {
        return new RetryAfterParser(
                STRICT_SECONDS,
                wait(clock, IMF_FIXDATE),
                wait(clock, RFC_850),
                wait(clock, ASCTIME)
        );
    }

    /**
     * Parser that accepts only {@code Retry-After} headers that meet a reasonably strict interpretation of
     * RFC-9110. Uses system clock to compute offsets when the header is a date.
     *
     * @return Parser for RFC-9110 {@code Retry-After} headers.
     */
    public static RetryAfterParser strict() {
        return strict(InstantSource.system());
    }

    /**
     * Parser that accepts {@code Retry-After} headers that meet a superset of RFC-9110, including headers
     * with a decimal number of seconds delay and ISO-8601 instants.
     *
     * @param clock the clock to use to compute offsets when the header is a date. Useful for testing.
     * @return Parser for extended {@code Retry-After} headers.
     */
    public static RetryAfterParser extended(final InstantSource clock) {
        return new RetryAfterParser(
                STRICT_SECONDS,
                DECIMAL_SECONDS,
                wait(clock, IMF_FIXDATE),
                wait(clock, RFC_850),
                wait(clock, ASCTIME),
                wait(clock, ISO)
        );
    }

    /**
     * Parser that accepts {@code Retry-After} headers that meet a superset of RFC-9110, including headers
     * with a decimal number of seconds delay. Uses system clock to compute offsets when the header is a date.
     *
     * @return Parser for extended {@code Retry-After} headers.
     */
    public static RetryAfterParser extended() {
        return extended(InstantSource.system());
    }

    /**
     * Accept {@code Retry-After} header that matches only strict
     * <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-7.1.3>RFC 7231</a> {@code delay-seconds}.
     */
    public static final Function<String, Optional<Duration>> STRICT_SECONDS = new PatternGuarded<>(
            "^\\d+$",
            h -> Duration.ofSeconds(Long.parseLong(h))
    );

    /**
     * Accept extended {@code Retry-After} header that allows decimal seconds.
     */
    public static final Function<String, Optional<Duration>> DECIMAL_SECONDS = new PatternGuarded<>(
            "^\\d+(\\.\\d*)?$",
            h -> Duration.ofMillis(new BigDecimal(h).movePointRight(3).longValue())
    );

    /**
     * Forgiving parser for a superset of IMF-fixdate using the builtin {@link DateTimeFormatter#RFC_1123_DATE_TIME}
     * <p>
     * Example: "Thu, 02 Jan 2003 01:23:45 GMT"
     */
    public static final Function<String, Optional<ZonedDateTime>> IMF_FIXDATE = new PatternGuarded<>(
            "^(\\w{3},\\s)?\\d{1,2}\\s\\w{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}(:\\d{2})?\\sGMT$",
            h -> ZonedDateTime.parse(h, DateTimeFormatter.RFC_1123_DATE_TIME)
    );

    /**
     * Forgiving parer for a superset of RFC-850 dates.
     * <p>
     * Example: "Thursday, 02-Jan-03 01:23:45 GMT"
     */
    public static final Function<String, Optional<ZonedDateTime>> RFC_850 = new PatternGuarded<>(
            "^(\\w+,\\s)?\\d{1,2}-\\w{3}-\\d{2}\\s\\d{1,2}:\\d{2}(:\\d{2})?\\s\\w+$",
            h -> ZonedDateTime.parse(h, RFC_850_FORMATTER)
    );

    /**
     * Forgiving parer for a superset of ASCTIME dates.
     * <p>
     * Example: "Thu Jan  2 01:23:45 2003"
     */
    public static final Function<String, Optional<ZonedDateTime>> ASCTIME = new PatternGuarded<>(
            "^(\\w{3}\\s+)?\\w{3}\\s+\\d+\\s\\d{1,2}:\\d{2}(:\\d{2}+)?\\s+\\d{4}$",
            h -> LocalDateTime.parse(h, ASCTIME_FORMATTER).atZone(ZoneOffset.UTC)
    );

    /**
     * Parser for ISO-8601 dates.
     * <p>
     * Example: "2011-12-03T10:15:30Z"
     * Example: "2011-12-03T10:15:30.123Z"
     * Example: "2011-12-03T10:15:30.123456Z"
     * Example: "2011-12-03T10:15:30.123456789Z"
     */
    public static final Function<String, Optional<ZonedDateTime>> ISO = new PatternGuarded<>(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.(\\d{3}){1,3})?Z$",
            h -> ZonedDateTime.parse(h, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    );

    /**
     * Parser to recognize the Retry-After formats defined in section 5.6.6 of RFC-9110 and convert the value to a
     * duration. If the header specified a date, the duration is relative to the current time.
     * <p>
     * The source of current time defaults to the system clock, but can be overridden by supplying an
     * {@link InstantSource} to the constructor.
     *
     * @param response the raw HTTP servlet response
     * @return duration until a retry is allowed
     */
    @Override
    public Optional<Duration> apply(final HttpServletResponse response) {
        if (response == null) return Optional.empty();

        final String rawHeader = response.getHeader(RETRY_AFTER_HEADER);
        if (rawHeader == null) return Optional.empty();

        final String header = rawHeader.trim();
        if (header.isEmpty()) {
            LOGGER.warn("Received empty Retry-After header \"{}\"", rawHeader);
            return Optional.empty();
        }

        // Return the result of the first successful parse
        for (final Function<String, Optional<Duration>> parser : parsers) {
            final Optional<Duration> retryAfter = parser.apply(header);
            if (retryAfter.isPresent()) {
                return retryAfter;
            }
        }

        LOGGER.warn("Received unrecognized Retry-After header \"{}\"", rawHeader);
        return Optional.empty();
    }

    /**
     * Converts header that arrives as a date into an offset from q clock.
     *
     * @param clock the clock to obtain the current time.
     * @param parser parser for the date in the header.
     * @return function that converts a date header into an offset.
     */
    private static Function<String, Optional<Duration>> wait(
            final InstantSource clock,
            final Function<String, Optional<ZonedDateTime>> parser) {

        return h -> parser.apply(h).map( t -> {
            final Duration difference = Duration.between(clock.instant(), t.toInstant());
            return difference.isNegative() ? Duration.ZERO : difference;
        });
    }

    /**
     * Creates a function that tests an input string against a pattern before attempting to parse the string.
     *
     * @param <T> the type generated by the parser
     */
    private static class PatternGuarded<T> implements Function<String, Optional<T>> {
        private final Pattern guard;
        private final Function<String, T> parser;

        public PatternGuarded(final String pattern, final Function<String, T> parser) {
            this.guard = Pattern.compile(pattern);
            this.parser = parser;
        }

        @Override
        public Optional<T> apply(final String header) {
            try {
                return guard.matcher(header).matches() ? Optional.of(parser.apply(header)) : Optional.empty();
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to parse Retry-After header \"{}\"", header, e);
                return Optional.empty();
            }
        }
    }

}
