package com.maybeitssquid.retry.resilience4j;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HeedRetryAfterTest {
    private static final long LIMIT = 2000L;
    private static final Either<Throwable, HttpServletResponse> LEFT = Either.left(new Exception("test"));

    private final IntervalBiFunction<HttpServletResponse> wrapped;
    private final HttpServletResponse response;
    private final Either<Throwable, HttpServletResponse> result;

    public HeedRetryAfterTest(@Mock HttpServletResponse response, @Mock IntervalBiFunction<HttpServletResponse> wrapped) {
        this.response = response;
        this.wrapped = wrapped;
        this.result = Either.right(this.response);
    }

    void testLimited(final HeedRetryAfter test, final long expected) {
        assertEquals(expected, test.apply(1, LEFT));
        assertEquals(expected, test.apply(1, this.result));

        testWithHeader(test, expected, "0");
    }

    void testWithHeader(final HeedRetryAfter test, final long expected, final String header) {
        when(this.response.getHeader("Retry-After")).thenReturn(header);
        assertEquals(expected, test.apply(1, this.result));
    }

    @Test
    void testAtLeastMilliseconds() {
        final HeedRetryAfter test = HeedRetryAfter.atLeast(2000L);
        testLimited(test, LIMIT);

        testWithHeader(test, LIMIT, "0");
        testWithHeader(test, LIMIT, "1");
        testWithHeader(test, LIMIT, "2");
        testWithHeader(test, 3000, "3");
    }

    @Test
    void testAtLeastDuration() {
        final HeedRetryAfter test = HeedRetryAfter.atLeast(Duration.ofSeconds(2L));
        testLimited(test, LIMIT);

        testWithHeader(test, LIMIT, "0");
        testWithHeader(test, LIMIT, "1");
        testWithHeader(test, LIMIT, "2");
        testWithHeader(test, 3000, "3");
    }

    @Test
    void testDefaulted() {
        final HeedRetryAfter test = HeedRetryAfter.defaulted();
        testLimited(test, 500L);

        testWithHeader(test, 500L, "0");
        testWithHeader(test, 1000L, "1");
        testWithHeader(test, 2000L, "2");
        testWithHeader(test, 3000L, "3");
    }

    @Test
    void testHeed() {
        final HeedRetryAfter test = HeedRetryAfter.heed();
        testLimited(test, 0L);

        testWithHeader(test, 0L, "0");
        testWithHeader(test, 1000L, "1");
        testWithHeader(test, 2000L, "2");
        testWithHeader(test, 3000L, "3");
    }

    @Test
    void testExtending() {
        final HeedRetryAfter test = HeedRetryAfter.heed(wrapped);
        when(this.wrapped.apply(1, LEFT)).thenReturn(LIMIT);
        when(this.wrapped.apply(1, this.result)).thenReturn(LIMIT);
        when(this.wrapped.apply(1, this.result)).thenReturn(LIMIT);
        testLimited(test, LIMIT);

        when(this.wrapped.apply(1, this.result)).thenReturn(1L);
        assertEquals(1L, test.apply(1, this.result));

        when(this.wrapped.apply(1, this.result)).thenReturn(5000L);
        when(this.response.getHeader("Retry-After")).thenReturn("2");
        assertEquals(5000L, test.apply(1, this.result));

        when(this.wrapped.apply(1, this.result)).thenReturn(3000L);
        when(this.response.getHeader("Retry-After")).thenReturn("4");
        assertEquals(4000L, test.apply(1, this.result));

        when(this.wrapped.apply(1, this.result)).thenReturn(1000L);
        when(this.response.getHeader("Retry-After")).thenReturn(null);
        assertEquals(1000L, test.apply(1, this.result));

        // Explicit wrapped function is the only variant where the wrapped function *might* return null
        when(this.wrapped.apply(1, LEFT)).thenReturn(null);
        assertNull(test.apply(1, LEFT));

        when(this.wrapped.apply(1, this.result)).thenReturn(null);
        when(this.response.getHeader("Retry-After")).thenReturn(null);
        assertNull(test.apply(1, this.result));

        when(this.wrapped.apply(1, this.result)).thenReturn(null);
        when(this.response.getHeader("Retry-After")).thenReturn(null);
        assertNull(test.apply(1, this.result));

        when(this.wrapped.apply(1, this.result)).thenReturn(null);
        when(this.response.getHeader("Retry-After")).thenReturn("1");
        assertEquals(1000L, test.apply(1, this.result));
    }

}
