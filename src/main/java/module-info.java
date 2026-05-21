/**
 * HTTP retry utilities with Resilience4j integration, including support for the {@code Retry-After}
 * response header.
 */
module com.maybeitssquid.retry {
    requires transitive jakarta.servlet;
    requires transitive org.slf4j;
    requires transitive io.github.resilience4j.core;
    requires transitive io.github.resilience4j.retry;

    exports com.maybeitssquid.retry;
    exports com.maybeitssquid.retry.resilience4j;
}
