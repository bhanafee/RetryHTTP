package com.maybeitssquid.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RetryStatusCodesTest {
  private final HttpServletResponse response;

  public RetryStatusCodesTest(@Mock HttpServletResponse response) {
    this.response = response;
  }

  /**
   * Verify that {@link RetryStatusCodes#test(HttpServletResponse)} works with a mock. The rest of
   * the unit tests are for specific codes and can be validated via the {@link
   * RetryStatusCodes#retries(int)} function.
   */
  @Test
  void testPredicate() {
    final RetryStatusCodes predicate = RetryStatusCodes.idempotent();
    when(response.getStatus()).thenReturn(200);
    assertFalse(predicate.test(response)); // OK

    when(response.getStatus()).thenReturn(302);
    assertTrue(predicate.test(response)); // OK
  }

  /**
   * Behavior shared by every predicate, whatever its 5xx policy. Arrays.fill excludes its end
   * index, so the range endpoints (199, 399) were previously missed.
   */
  static Stream<Arguments> commonStatuses() {
    return Stream.of(
        // Out of range
        Arguments.of(-1, false),
        Arguments.of(0, false),
        Arguments.of(1, false),
        Arguments.of(99, false),
        Arguments.of(600, false),
        Arguments.of(Integer.MAX_VALUE, false),
        Arguments.of(Integer.MIN_VALUE, false),
        // Continue, and the 1xx range endpoints
        Arguments.of(100, true),
        Arguments.of(199, true),
        // 2xx and 3xx
        Arguments.of(200, false), // OK
        Arguments.of(302, true), // Moved temporarily
        Arguments.of(399, true), // last 3xx
        // 4xx and 5xx not covered by the 5xx policy
        Arguments.of(404, false), // Not found
        Arguments.of(501, false), // Not implemented
        Arguments.of(505, false)); // HTTP version not supported
  }

  @ParameterizedTest
  @MethodSource("commonStatuses")
  void idempotentCommon(final int status, final boolean retries) {
    assertCommon(RetryStatusCodes.idempotent(), status, retries);
  }

  @ParameterizedTest
  @MethodSource("commonStatuses")
  void nonIdempotentCommon(final int status, final boolean retries) {
    assertCommon(RetryStatusCodes.nonIdempotent(), status, retries);
  }

  // Pathology forcing varargs to null
  @ParameterizedTest
  @MethodSource("commonStatuses")
  void idempotentNullVarArgsCommon(final int status, final boolean retries) {
    assertCommon(RetryStatusCodes.idempotent((int[]) null), status, retries);
  }

  @ParameterizedTest
  @MethodSource("commonStatuses")
  void nonIdempotentNullVarArgsCommon(final int status, final boolean retries) {
    assertCommon(RetryStatusCodes.nonIdempotent((int[]) null), status, retries);
  }

  private static void assertCommon(
      final RetryStatusCodes predicate, final int status, final boolean retries) {
    assertEquals(retries, predicate.retries(status), () -> "retries(" + status + ")");
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 503, 599}) // Internal server error, service unavailable, last 5xx
  void idempotentRetriesServerErrors(final int status) {
    assertTrue(RetryStatusCodes.idempotent().retries(status));
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 503, 599}) // Internal server error, service unavailable, last 5xx
  void nonIdempotentDoesNotRetryServerErrors(final int status) {
    assertFalse(RetryStatusCodes.nonIdempotent().retries(status));
  }

  @Test
  void idempotentOverrideRetriesNotImplemented() {
    assertTrue(RetryStatusCodes.idempotent(501).retries(501));
  }

  @Test
  void nonIdempotentOverrideRetriesNotImplemented() {
    assertTrue(RetryStatusCodes.nonIdempotent(501).retries(501));
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
