package com.maybeitssquid.retry.resilience4j;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.maybeitssquid.retry.resilience4j.Retry.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static io.github.resilience4j.retry.RetryConfig.DEFAULT_WAIT_DURATION;

@ExtendWith(MockitoExtension.class)
public class RetryTest {
    private static final Duration TEST_MAXIMUM = Duration.ofSeconds(1L);
    private static final int TEST_CODE = 250;

    private final HttpServletResponse response;

    public RetryTest(@Mock final HttpServletResponse response) {
        this.response = response;
    }

    @SuppressWarnings("deprecation")
    private IntervalBiFunction<HttpServletResponse> validatedBiFunction(final RetryConfig config) {
        assertNull(config.getIntervalFunction());
        final IntervalBiFunction<HttpServletResponse> biFunction = config.getIntervalBiFunction();
        assertNotNull(biFunction);

        assertEquals(DEFAULT_WAIT_DURATION, biFunction.apply(1, Either.left(new Throwable())));

        when(response.getHeader("Retry-After")).thenReturn("0");
        assertEquals(DEFAULT_WAIT_DURATION, biFunction.apply(1, Either.right(this.response)));

        when(response.getHeader("Retry-After")).thenReturn("1");
        assertEquals(1000L, biFunction.apply(1, Either.right(this.response)));

        when(response.getHeader("Retry-After")).thenReturn("2");
        assertEquals(2000L, biFunction.apply(1, Either.right(this.response)));

        return biFunction;
    }

    private Predicate<HttpServletResponse> validatedPredicate(final RetryConfig config) {
        final Predicate<HttpServletResponse> predicate = config.getResultPredicate();
        assertNotNull(predicate);
        return predicate;
    }

    private void testPredicateCodes(final Predicate<HttpServletResponse> predicate) {
        when(this.response.getStatus()).thenReturn(TEST_CODE);
        assertTrue(predicate.test(this.response));

        when(this.response.getStatus()).thenReturn(200);
        assertFalse(predicate.test(this.response));
    }

    private void testPredicateWait(final Predicate<HttpServletResponse> predicate) {
        when(response.getStatus()).thenReturn(TEST_CODE);
        when(response.getHeader("Retry-After")).thenReturn("0");
        assertTrue(predicate.test(this.response));
        when(response.getHeader("Retry-After")).thenReturn("1");
        assertTrue(predicate.test(this.response));
        when(response.getHeader("Retry-After")).thenReturn("2");
        assertFalse(predicate.test(this.response));
    }

    private RetryConfig build(Consumer<RetryConfig.Builder<HttpServletResponse>> consumer) {
        final RetryConfig.Builder<HttpServletResponse> builder = RetryConfig.custom();
        consumer.accept(builder);
        return builder.build();
    }

    @Test
    void testIdempotent() {
        final RetryConfig config = build(idempotent(TEST_CODE));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
        testPredicateCodes(predicate);
    }

    @Test
    void testNonIdempotent() {
        final RetryConfig config = build(nonIdempotent(TEST_CODE));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
    }

    @Test
    void testOnly() {
        final RetryConfig config = build(onlyCodes(TEST_CODE));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
        testPredicateCodes(predicate);
    }

    @Test
    void testIdempotentWithMaximum() {
        final RetryConfig config = build(idempotent(TEST_MAXIMUM, TEST_CODE));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
        testPredicateCodes(predicate);
        testPredicateWait(predicate);
        final IntervalBiFunction<HttpServletResponse> biFunction = validatedBiFunction(config);
    }

    @Test
    void testNonIdempotentWithMaximum() {
        final RetryConfig config = build(nonIdempotent(TEST_MAXIMUM, TEST_CODE));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
        testPredicateCodes(predicate);
        testPredicateWait(predicate);
        final IntervalBiFunction<HttpServletResponse> biFunction = validatedBiFunction(config);
    }

    @Test
    void testOnlyWithMaximum() {
        final RetryConfig config = build(onlyCodes(TEST_MAXIMUM, TEST_CODE));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
        testPredicateCodes(predicate);
        testPredicateWait(predicate);
        final IntervalBiFunction<HttpServletResponse> biFunction = validatedBiFunction(config);
    }

    @Test
    void testUnlimitedWait() {
        final RetryConfig config = build(retryAfter());
        assertNull(config.getResultPredicate());
        final IntervalBiFunction<HttpServletResponse> biFunction = validatedBiFunction(config);
    }

    @Test
    void testLimitedWait() {
        final RetryConfig config = build(retryAfter(TEST_MAXIMUM));
        final Predicate<HttpServletResponse> predicate = validatedPredicate(config);
        testPredicateWait(predicate);
        final IntervalBiFunction<HttpServletResponse> biFunction = validatedBiFunction(config);
   }

}
