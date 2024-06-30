package com.maybeitssquid.retry;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LimitRetryAfterTest {
    private static final Duration TWO_SECONDS = Duration.ofSeconds(2L);

    private final HttpServletResponse response;

    public LimitRetryAfterTest(@Mock HttpServletResponse response) {
        this.response = response;
    }

    @Test
    void testPredicate() {
        final LimitRetryAfter limiter = LimitRetryAfter.maximum(TWO_SECONDS);

        when(response.getHeader("Retry-After")).thenReturn(null);
        assertTrue(limiter.test(response));

        when(response.getHeader("Retry-After")).thenReturn("0");
        assertTrue(limiter.test(response));

        when(response.getHeader("Retry-After")).thenReturn("1");
        assertTrue(limiter.test(response));

        when(response.getHeader("Retry-After")).thenReturn("2");
        assertTrue(limiter.test(response));

        when(response.getHeader("Retry-After")).thenReturn("3");
        assertFalse(limiter.test(response));
    }

    @Test
    void testMaximumMilliseconds() {
        final LimitRetryAfter limiter = LimitRetryAfter.maximum(2000L);
        assertEquals(TWO_SECONDS, limiter.getMaximum());
    }

    @Test
    void testMaximumDuration() {
        final LimitRetryAfter limiter = LimitRetryAfter.maximum(TWO_SECONDS);
        assertEquals(TWO_SECONDS, limiter.getMaximum());
    }
}
