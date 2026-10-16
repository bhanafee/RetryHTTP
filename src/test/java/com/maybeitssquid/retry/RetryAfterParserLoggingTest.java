package com.maybeitssquid.retry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/** Covers the logger the parser writes to, and how it is supplied and used. */
public class RetryAfterParserLoggingTest {
  private static final String EMPTY_MESSAGE = "Received empty Retry-After header \"{}\"";
  private static final String UNRECOGNIZED_MESSAGE =
      "Received unrecognized Retry-After header \"{}\"";
  private static final String FAILED_MESSAGE = "Failed to parse Retry-After header \"{}\"";
  private static final String IMF_HEADER = "Thu, 02 Jan 2003 01:23:45 GMT";

  /** A logger whose WARN builder returns itself, so every fluent call lands on one mock. */
  private static final class MockLogger {
    final Logger logger = mock(Logger.class);
    final LoggingEventBuilder builder = mock(LoggingEventBuilder.class, RETURNS_SELF);

    MockLogger() {
      when(logger.atWarn()).thenReturn(builder);
    }
  }

  private static HttpServletResponse responseWith(final String header) {
    final HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getHeader(RetryAfterParser.RETRY_AFTER_HEADER)).thenReturn(header);
    return response;
  }

  @Test
  void defaultLoggerIsUsedWhenNoLoggerSupplied() {
    final String expected = LoggerFactory.getLogger(RetryAfterParser.class).getName();
    assertEquals(expected, RetryAfterParser.secondsOnly().getLogger().getName());
    assertEquals(expected, RetryAfterParser.decimalSeconds().getLogger().getName());
    assertEquals(expected, RetryAfterParser.strict().getLogger().getName());
    assertEquals(expected, RetryAfterParser.extended().getLogger().getName());
  }

  @Test
  void defaultLoggerIsUsedWhenNullSupplied() {
    final String expected = LoggerFactory.getLogger(RetryAfterParser.class).getName();
    assertEquals(expected, RetryAfterParser.secondsOnly(null).getLogger().getName());
    assertEquals(expected, RetryAfterParser.decimalSeconds(null).getLogger().getName());
    assertEquals(
        expected, RetryAfterParser.strict(null, InstantSource.system()).getLogger().getName());
    assertEquals(
        expected, RetryAfterParser.extended(null, InstantSource.system()).getLogger().getName());
  }

  @Test
  void suppliedLoggerIsReturnedByEveryFactory() {
    final Logger logger = mock(Logger.class);
    assertSame(logger, RetryAfterParser.secondsOnly(logger).getLogger());
    assertSame(logger, RetryAfterParser.decimalSeconds(logger).getLogger());
    assertSame(logger, RetryAfterParser.strict(logger, InstantSource.system()).getLogger());
    assertSame(logger, RetryAfterParser.extended(logger, InstantSource.system()).getLogger());
  }

  @Test
  void validHeaderDoesNotLog() {
    final MockLogger mocks = new MockLogger();
    final RetryAfterParser parser = RetryAfterParser.secondsOnly(mocks.logger);

    assertEquals(Optional.of(Duration.ofSeconds(5)), parser.apply(responseWith("5")));
    verifyNoInteractions(mocks.logger);
  }

  @Test
  void emptyHeaderLogsWarningWithRenderedValue() {
    final MockLogger mocks = new MockLogger();
    final RetryAfterParser parser = RetryAfterParser.secondsOnly(mocks.logger);

    assertTrue(parser.apply(responseWith("   ")).isEmpty());

    verify(mocks.builder).log(EMPTY_MESSAGE);
    assertSuppliedArgument(mocks.builder, "   ");
  }

  @Test
  void unrecognizedHeaderLogsWarningWithRenderedValue() {
    final MockLogger mocks = new MockLogger();
    final RetryAfterParser parser = RetryAfterParser.secondsOnly(mocks.logger);

    assertTrue(parser.apply(responseWith("garbage")).isEmpty());

    verify(mocks.builder).log(UNRECOGNIZED_MESSAGE);
    assertSuppliedArgument(mocks.builder, "garbage");
  }

  /** Control characters in the header must reach the log escaped, not raw. */
  @Test
  void loggedValueIsEscaped() {
    final MockLogger mocks = new MockLogger();
    final RetryAfterParser parser = RetryAfterParser.secondsOnly(mocks.logger);

    assertTrue(parser.apply(responseWith("60\nforged")).isEmpty());

    assertSuppliedArgument(mocks.builder, "60\\u000aforged");
  }

  /** A clock failure is caught per parser, logged with its cause, and parsing carries on. */
  @Test
  void clockFailureLogsWarningWithCause() {
    final MockLogger mocks = new MockLogger();
    final InstantSource clock = mock(InstantSource.class);
    final IllegalStateException failure = new IllegalStateException("clock unavailable");
    when(clock.instant()).thenThrow(failure);
    final RetryAfterParser parser = RetryAfterParser.strict(mocks.logger, clock);

    assertTrue(parser.apply(responseWith(IMF_HEADER)).isEmpty());

    verify(mocks.builder).setCause(failure);
    verify(mocks.builder).log(FAILED_MESSAGE);
    verify(mocks.builder).log(UNRECOGNIZED_MESSAGE);
    assertEquals(List.of(IMF_HEADER, IMF_HEADER), renderedArguments(mocks.builder));
  }

  /** Each parser writes to its own logger, not to a shared one. */
  @Test
  void loggersAreIndependentPerParser() {
    final MockLogger first = new MockLogger();
    final MockLogger second = new MockLogger();
    final RetryAfterParser parserA = RetryAfterParser.secondsOnly(first.logger);
    final RetryAfterParser parserB = RetryAfterParser.secondsOnly(second.logger);

    assertTrue(parserA.apply(responseWith("garbage")).isEmpty());

    verify(first.builder).log(UNRECOGNIZED_MESSAGE);
    verifyNoInteractions(second.logger);
    assertNotSame(parserA.getLogger(), parserB.getLogger());
  }

  /** Checks that the rendered value was passed as a supplier and that it renders as expected. */
  private static void assertSuppliedArgument(
      final LoggingEventBuilder builder, final String expected) {
    assertEquals(List.of(expected), renderedArguments(builder).subList(0, 1));
  }

  /** Runs every supplied argument, in call order, and returns what each rendered to. */
  @SuppressWarnings("unchecked")
  private static List<Object> renderedArguments(final LoggingEventBuilder builder) {
    final ArgumentCaptor<Supplier<?>> captor = ArgumentCaptor.forClass(Supplier.class);
    verify(builder, atLeastOnce()).addArgument(captor.capture());
    return captor.getAllValues().stream().map(s -> (Object) s.get()).toList();
  }
}
