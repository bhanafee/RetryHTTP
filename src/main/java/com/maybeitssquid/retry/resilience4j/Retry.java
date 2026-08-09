package com.maybeitssquid.retry.resilience4j;

import com.maybeitssquid.retry.LimitRetryAfter;
import com.maybeitssquid.retry.RetryStatusCodes;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Factories for common configurations.
 *
 * <p><strong>Apply these factories after setting the wait interval.</strong> The factories that
 * heed the {@code Retry-After} header decorate the builder's {@link IntervalBiFunction}. {@link
 * RetryConfig.Builder#waitDuration(Duration)} and {@link
 * RetryConfig.Builder#intervalFunction(io.github.resilience4j.core.IntervalFunction)} replace that
 * function rather than composing with it, so calling either one afterwards discards the {@code
 * Retry-After} support without warning: the retry then waits the configured interval and ignores
 * the header. Other builder methods, including {@link RetryConfig.Builder#maxAttempts(int)}, are
 * unaffected and may be called in any order.
 *
 * <pre>{@code
 * // Correct - wait interval first, factory last
 * RetryConfig.Builder<HttpServletResponse> builder = RetryConfig.custom();
 * builder.maxAttempts(3).waitDuration(Duration.ofSeconds(5));
 * Retry.idempotent(Duration.ofSeconds(30)).accept(builder);
 *
 * // Wrong - waitDuration() discards the Retry-After support installed above
 * Retry.idempotent(Duration.ofSeconds(30)).accept(builder);
 * builder.waitDuration(Duration.ofSeconds(5));   // header now silently ignored
 * }</pre>
 */
public interface Retry {

  /**
   * Adds a {@link java.util.function.Predicate} that decides whether to retry based on the HTTP
   * status code in the response.
   *
   * @param retry status codes that allow retry, in addition to the defaults for idempotent
   *     functions provided by {@link RetryStatusCodes}
   * @return consumer that uses HTTP status code for retry decisions.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> idempotent(final int... retry) {
    return builder -> builder.retryOnResult(RetryStatusCodes.idempotent(retry));
  }

  /**
   * Adds a {@link java.util.function.Predicate} that decides whether to retry based on the HTTP
   * status code in the response.
   *
   * @param retry status codes that allow retry, in addition to the defaults for non-idempotent
   *     functions provided by {@link RetryStatusCodes}
   * @return consumer that uses HTTP status code for retry decisions.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> nonIdempotent(
      final int... retry) {
    return builder -> builder.retryOnResult(RetryStatusCodes.nonIdempotent(retry));
  }

  /**
   * Adds a {@link java.util.function.Predicate} that decides whether to retry based on the HTTP
   * status code in the response.
   *
   * @param retry complete list of status codes that allow retry
   * @return consumer that uses HTTP status code for retry decisions.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> onlyCodes(final int... retry) {
    return builder -> builder.retryOnResult(RetryStatusCodes.only(retry));
  }

  /**
   * Adds a {@link java.util.function.Predicate} that decides whether to retry based on the HTTP
   * status code in the response and whether any {code Retry-After} header allows for a retry within
   * an acceptable interval, and adds an {@link IntervalBiFunction} to extend the wait interval as
   * needed.
   *
   * @param limit the maximum wait interval that will be allowed by a {@code Retry-After}.
   * @param retry status codes that allow retry, in addition to the defaults for idempotent
   *     functions provided by {@link RetryStatusCodes}
   * @return consumer that uses HTTP status code and Retry-After for retry decisions and waits.
   * @apiNote Apply after {@link RetryConfig.Builder#waitDuration(Duration)}; see the class
   *     documentation for why order matters.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> idempotent(
      final Duration limit, final int... retry) {
    return limitAndCodes(limit, RetryStatusCodes.idempotent(retry));
  }

  /**
   * Adds a {@link java.util.function.Predicate} that decides whether to retry based on the HTTP
   * status code in the response and whether any {code Retry-After} header allows for a retry within
   * an acceptable interval, and adds an {@link IntervalBiFunction} to extend the wait interval as
   * needed.
   *
   * @param limit the maximum wait interval that will be allowed by a {@code Retry-After}.
   * @param retry status codes that allow retry, in addition to the defaults for non-idempotent
   *     functions provided by {@link RetryStatusCodes}
   * @return consumer that uses HTTP status code and Retry-After for retry decisions and waits.
   * @apiNote Apply after {@link RetryConfig.Builder#waitDuration(Duration)}; see the class
   *     documentation for why order matters.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> nonIdempotent(
      final Duration limit, final int... retry) {
    return limitAndCodes(limit, RetryStatusCodes.nonIdempotent(retry));
  }

  /**
   * Adds a {@link java.util.function.Predicate} that decides whether to retry based on the HTTP
   * status code in the response and whether any {code Retry-After} header allows for a retry within
   * an acceptable interval, and adds an {@link IntervalBiFunction} to extend the wait interval as
   * needed.
   *
   * @param limit the maximum wait interval that will be allowed by a {@code Retry-After}.
   * @param retry complete list of status codes that allow retry
   * @return consumer that uses HTTP status code and Retry-After for retry decisions and waits.
   * @apiNote Apply after {@link RetryConfig.Builder#waitDuration(Duration)}; see the class
   *     documentation for why order matters.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> onlyCodes(
      final Duration limit, final int... retry) {
    return limitAndCodes(limit, RetryStatusCodes.only(retry));
  }

  /**
   * Adds an {@link IntervalBiFunction} that respects any {@code Retry-After} header provided in the
   * response, without limit.
   *
   * <p><strong>CAUTION:</strong> The response may specify a {@code Retry-After} interval years in
   * the future. Skew between the local system clock and the clock on the service that generated the
   * header also may produce unexpectedly long wait durations. A delay too large to express is
   * clamped to {@link com.maybeitssquid.retry.RetryAfterParser#MAX_DELAY}, so without a limit a
   * single hostile or misconfigured response can park a retry effectively forever. Implementations
   * that do not set a limit alternatively can defend against an unacceptable wait interval using
   * e.g. a TimeLimiter to terminate excessive waits and a Bulkhead to prevent too many outstanding
   * requests.
   *
   * @return consumer that uses Retry-After for retry waits.
   * @apiNote Apply after {@link RetryConfig.Builder#waitDuration(Duration)}; see the class
   *     documentation for why order matters.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> retryAfter() {
    return Retry::heedRetryAfter;
  }

  /**
   * Adds an {@link IntervalBiFunction} that respects any {@code Retry-After} header provided in the
   * response, and adds a predicate to ensure the wait does not exceed the limit.
   *
   * @param limit the longest the client should wait.
   * @return consumer that uses Retry-After for retry decisions and waits.
   * @apiNote Apply after {@link RetryConfig.Builder#waitDuration(Duration)}; see the class
   *     documentation for why order matters.
   */
  public static Consumer<RetryConfig.Builder<HttpServletResponse>> retryAfter(
      final Duration limit) {
    return builder -> {
      builder.retryOnResult(LimitRetryAfter.maximum(limit));
      heedRetryAfter(builder);
    };
  }

  /**
   * Common code for several variations.
   *
   * @param limit the maximum wait interval that will be allowed by a {@code Retry-After}.
   * @param codes the HTTP status codes to retry
   * @return consumer that adds HTTP status code and Retry-After support.
   */
  private static Consumer<RetryConfig.Builder<HttpServletResponse>> limitAndCodes(
      final Duration limit, final RetryStatusCodes codes) {
    return builder -> {
      final LimitRetryAfter maximum = LimitRetryAfter.maximum(limit);
      builder.retryOnResult(codes.and(maximum));
      heedRetryAfter(builder);
    };
  }

  /**
   * Ugly way to decorate the existing {@link IntervalBiFunction}.
   *
   * @param builder the builder to decorate.
   */
  private static void heedRetryAfter(final RetryConfig.Builder<HttpServletResponse> builder) {
    // Builder lacks accessors, so have to instantiate
    final IntervalBiFunction<HttpServletResponse> original =
        builder.build().getIntervalBiFunction();
    builder.intervalBiFunction(HeedRetryAfter.heed(original));
  }
}
