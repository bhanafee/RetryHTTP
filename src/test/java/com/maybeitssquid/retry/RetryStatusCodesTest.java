package com.maybeitssquid.retry;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RetryStatusCodesTest {
    private final HttpServletResponse response;

    public RetryStatusCodesTest(@Mock HttpServletResponse response) {
        this.response = response;
    }

    /**
     * Verify that {@link RetryStatusCodes#test(HttpServletResponse)} works with a mock. The rest of the unit tests are
     * for specific codes and can be validated via the {@link RetryStatusCodes#retries(int)} function.
     */
    @Test
    void testPredicate() {
        final RetryStatusCodes predicate = RetryStatusCodes.idempotent();
        when(response.getStatus()).thenReturn(200);
        assertFalse(predicate.test(response));  // OK

        when(response.getStatus()).thenReturn(302);
        assertTrue(predicate.test(response));  // OK
    }

    void testCommon(final RetryStatusCodes predicate) {
        // Out of range
        assertFalse(predicate.retries(-1));
        assertFalse(predicate.retries(0));
        assertFalse(predicate.retries(1));
        assertFalse(predicate.retries(99));
        assertFalse(predicate.retries(600));
        assertFalse(predicate.retries(Integer.MAX_VALUE));
        assertFalse(predicate.retries(Integer.MIN_VALUE));

        assertTrue(predicate.retries(100));   // Continue
        assertFalse(predicate.retries(200));  // OK
        assertTrue(predicate.retries(302));   // Moved temporarily
        assertFalse(predicate.retries(404));  // Not found
        assertFalse(predicate.retries(501));  // Not implemented
        assertFalse(predicate.retries(505));  // HTTP version not supported
    }

    @Test
    void testIdempotent() {
        final RetryStatusCodes idempotent = RetryStatusCodes.idempotent();
        testCommon(idempotent);

        assertTrue(idempotent.retries(500));   // Internal server error
        assertTrue(idempotent.retries(503));   // Service unavailable

        // Override to retry "not implemented"
        assertTrue(RetryStatusCodes.idempotent(501).retries(501));

        // Pathology forcing varargs to null
        final RetryStatusCodes badVarArgs = RetryStatusCodes.idempotent((int[]) null);
        testCommon(badVarArgs);
    }

    @Test
    void testNonIdempotent() {
        final RetryStatusCodes nonIdempotent = RetryStatusCodes.nonIdempotent();
        testCommon(nonIdempotent);


        assertFalse(nonIdempotent.retries(500));   // Internal server error
        assertFalse(nonIdempotent.retries(503));   // Service unavailable

        // Override to retry "not implemented"
        assertTrue(RetryStatusCodes.nonIdempotent(501).retries(501));

        // Pathology forcing varargs to null
        final RetryStatusCodes badVarArgs = RetryStatusCodes.nonIdempotent((int[]) null);
        testCommon(badVarArgs);
    }

    @Test
    void testOnly() {
        final RetryStatusCodes only = RetryStatusCodes.only();
        for (int status = -1; status < 1000; status++) {
            assertFalse(only.retries(status));
        }

        final RetryStatusCodes justOne = RetryStatusCodes.only(200);
        for (int status = -1; status < 199; status++) {
            assertFalse(only.retries(status));
        }
        assertTrue(justOne.retries(200));
        for (int status = 201; status < 1000; status++) {
            assertFalse(only.retries(status));
        }
    }

}
