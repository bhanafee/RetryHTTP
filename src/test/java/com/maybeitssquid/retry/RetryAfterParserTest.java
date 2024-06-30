package com.maybeitssquid.retry;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.maybeitssquid.retry.RetryAfterParser.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RetryAfterParserTest {
    private static final Instant TEST_INSTANT = Instant.parse("2003-01-02T01:23:45Z");
    private final HttpServletResponse response;

    public RetryAfterParserTest(@Mock HttpServletResponse response) {
        this.response = response;
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "100, 100",
            "00000, 0",
            "00001, 1",
            "999999999, 999999999"
    })
    void testStrictSecondsFunction(final String header, final long seconds) {
        Optional<Duration> result = STRICT_SECONDS.apply(header);
        assertTrue(result.isPresent());
        assertEquals(seconds, result.get().getSeconds());
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 0",
            "0.5, 500",
            "1.5, 1500",
            "0000.0, 0",
            "00.001, 1"
    })
    void testDecimalSecondsFunction(final String header, final long milliseconds) {
        final Optional<Duration> result = DECIMAL_SECONDS.apply(header);
        assertTrue(result.isPresent());
        assertEquals(milliseconds, result.get().toMillis());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "Thu, 02 Jan 2003 01:23:45 GMT",
            "Thu, 2 Jan 2003 01:23:45 GMT",
            "2 Jan 2003 01:23:45 GMT",
            "2 Jan 2003 1:23:45 GMT"
    })
    void testImfFixdateFunction(final String header) {
        final Optional<ZonedDateTime> result = IMF_FIXDATE.apply(header);
        assertTrue(result.isPresent());
        assertEquals(TEST_INSTANT, result.get().toInstant());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "Thursday, 02-Jan-03 01:23:45 GMT",
            "Thursday, 2-Jan-03 01:23:45 GMT",
            "Thursday, 02-Jan-03 1:23:45 GMT",
            "Wednesday, 01-Jan-03 20:23:45 EST",
            "Thursday, 02-Jan-03 02:23:45 CET",
            "02-Jan-03 01:23:45 GMT",
            "2-Jan-03 1:23:45 GMT"
    })
    void testRfc850Function(final String header) {
        final Optional<ZonedDateTime> result = RFC_850.apply(header);
        assertTrue(result.isPresent());
        assertEquals(TEST_INSTANT, result.get().toInstant());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "Thu Jan  2 01:23:45 2003",
            "Thu Jan 2 01:23:45 2003",
            "Jan 2 01:23:45 2003",
            "Jan 2 1:23:45 2003"
    })
    void testAscTimeFunction(final String header) {
        final Optional<ZonedDateTime> result = ASCTIME.apply(header);
        assertTrue(result.isPresent());
        assertEquals(TEST_INSTANT, result.get().toInstant());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "2003-01-02T01:23:45Z",
            "2003-01-02T01:23:45.000Z",
            "2003-01-02T01:23:45.000000Z",
            "2003-01-02T01:23:45.000000000Z"
    })
    void testIsoFunction(final String header) {
        final Optional<ZonedDateTime> result = ISO.apply(header);
        assertTrue(result.isPresent());
        assertEquals(TEST_INSTANT, result.get().toInstant());
    }

    /* Test the basic error handling. */
    @Test
    void testApply() {
        final RetryAfterParser parser = RetryAfterParser.secondsOnly();

        assertTrue(parser.apply(null).isEmpty());

        assertTrue(parser.apply(response).isEmpty());

        when(response.getHeader("Retry-After")).thenReturn(null);
        assertTrue(parser.apply(response).isEmpty());

        when(response.getHeader("Retry-After")).thenReturn("");
        assertTrue(parser.apply(response).isEmpty());

        when(response.getHeader("Retry-After")).thenReturn("garbage");
        assertTrue(parser.apply(response).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "10, 10",
            "03, 3"
    })
    void testSecondsOnly(final String header, final long seconds) {
        final RetryAfterParser parser = RetryAfterParser.secondsOnly();

        when(response.getHeader("Retry-After")).thenReturn(header);

        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(Duration.ofSeconds(seconds), result.get());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "0.0",
            "1.0",
            "-1",
            "Thu, 02 Jan 2003 01:23:45 GMT",
            "garbage"
    })
    void testSecondsOnlyNegative(final String header) {
        negativeTest(RetryAfterParser.secondsOnly(), header);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1000",
            "2, 2000",
            "0.0, 0",
            "0.5, 500",
            "1.5, 1500",
            "0000.0, 0",
            "00.001, 1"
    })
    void testDecimalSeconds(final String header, final long milliseconds) {
        final RetryAfterParser parser = RetryAfterParser.decimalSeconds();

        when(response.getHeader("Retry-After")).thenReturn(header);

        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(milliseconds, result.get().toMillis());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "-1",
            "Thu, 02 Jan 2003 01:23:45 GMT",
            "garbage"
    })
    void testDecimalSecondsNegative(final String header) {
        negativeTest(RetryAfterParser.decimalSeconds(), header);
    }

    @Test
    void testStrict() {
        final RetryAfterParser parser = RetryAfterParser.strict();

        when(response.getHeader("Retry-After")).thenReturn("0");
        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(Duration.ZERO, result.get());

        when(response.getHeader("Retry-After")).thenReturn("1.0");
        assertFalse(parser.apply(response).isPresent());
    }

    @Test
    void testDatePast() {
        final Duration EXPECTED = Duration.ofSeconds(1);
        // Set the parser clock to a fixed time EXPECTED seconds after TEST_INSTANT
        final RetryAfterParser parser = RetryAfterParser.strict(InstantSource.fixed(TEST_INSTANT.plus(EXPECTED)));

        when(response.getHeader("Retry-After")).thenReturn("Thu, 02 Jan 2003 01:23:45 GMT");
        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(Duration.ZERO, result.get());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "1",
            "Thu, 02 Jan 2003 01:23:45 GMT",
            "Thursday, 02-Jan-03 01:23:45 GMT",
            "Thu Jan  2 01:23:45 2003"
    })
    void testStrictDates(final String header) {
        final Duration EXPECTED = Duration.ofSeconds(1);
        // Set the parser clock to a fixed time EXPECTED seconds before TEST_INSTANT
        final RetryAfterParser parser = RetryAfterParser.strict(InstantSource.fixed(TEST_INSTANT.minus(EXPECTED)));

        when(response.getHeader("Retry-After")).thenReturn(header);
        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(EXPECTED, result.get());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "1.0",
            "-1",
            "2003-01-02T01:23:45Z",
            "",
            "garbage"
    })
    void testStrictNegative(final String header) {
        negativeTest(RetryAfterParser.strict(), header);
    }

    @Test
    void testExtended() {
        final RetryAfterParser parser = RetryAfterParser.extended();

        when(response.getHeader("Retry-After")).thenReturn("0");
        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(Duration.ZERO, result.get());

        when(response.getHeader("Retry-After")).thenReturn("1.5");
        final Optional<Duration> result2 = parser.apply(response);
        assertTrue(result2.isPresent());
        assertEquals(Duration.ofMillis(1500), result2.get());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "1",
            "1.0",
            "Thu, 02 Jan 2003 01:23:45 GMT",
            "Thursday, 02-Jan-03 01:23:45 GMT",
            "Thu Jan  2 01:23:45 2003",
            "2003-01-02T01:23:45Z"
    })
    void testExtendedDates(final String header) {
        final Duration EXPECTED = Duration.ofSeconds(1);
        // Set the parser clock to a fixed time EXPECTED seconds before TEST_INSTANT
        final RetryAfterParser parser = RetryAfterParser.extended(InstantSource.fixed(TEST_INSTANT.minus(EXPECTED)));

        when(response.getHeader("Retry-After")).thenReturn(header);
        final Optional<Duration> result = parser.apply(response);
        assertTrue(result.isPresent());
        assertEquals(EXPECTED, result.get());
    }

    @ParameterizedTest
    @ValueSource( strings= {
            "",
            "-1",
            "garbage",
            "XXX, 99 XXX 9999 99:99:99 GMT",
            "XXX, 02 Jan 2003 01:23:45 GMT",
            "Thu, 99 Jan 2003 01:23:45 GMT",
            "Thu, 02 XXX 2003 01:23:45 GMT",
            "Thu, 02 Jan 9999 01:23:45 GMT",
            "Thu, 02 Jan 2003 99:23:45 GMT",
            "Thu, 02 Jan 2003 01:99:45 GMT",
            "Thu, 02 Jan 2003 01:23:99 GMT"
    })
    void testExtendedNegative(final String header) {
        negativeTest(RetryAfterParser.extended(), header);
    }

    void negativeTest(final RetryAfterParser parser, final String header) {
        when (response.getHeader("Retry-After")).thenReturn(header);
        final Optional<Duration> result = parser.apply(response);
        assertFalse(result.isPresent());
    }

}
