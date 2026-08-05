# Codebase Guidance

This file documents key information about the project architecture, build commands, code style, and security practices.

## Project overview

RetryHTTP is a Java library that adds HTTP-awareness to [Resilience4j](https://resilience4j.readme.io). It provides `Predicate<HttpServletResponse>` and `IntervalBiFunction<HttpServletResponse>` implementations that make retry decisions based on HTTP status codes and `Retry-After` response headers, operating on `jakarta.servlet.http.HttpServletResponse`. Published to GitHub Packages as `com.maybeitssquid:RetryHTTP`.

## Commands

```bash
./gradlew build                   # compile, test, spotless check
./gradlew test                    # run tests
./gradlew test --tests "..."      # run a single test class
./gradlew spotlessApply           # auto-format (required before commit)
./gradlew javadoc                 # generate Javadoc
./gradlew dependencyCheckAnalyze  # OWASP vulnerability scan (slow; fails at CVSS ≥ 7)
```

Build uses Java 25 toolchain, compiles to Java 17 bytecode (`release = "17"`). CI tests on Java 17, 21, and 25.

## Architecture

Two packages:

**`com.maybeitssquid.retry`** — Core, framework-agnostic classes:
- `RetryStatusCodes` — `Predicate<HttpServletResponse>` that maps HTTP status codes to retry decisions. Factory methods: `idempotent()`, `nonIdempotent()`, `only()`. Backed by a boolean array indexed at `status - 100`.
- `RetryAfterParser` — `Function<HttpServletResponse, Optional<Duration>>` that parses `Retry-After` headers. Factory methods: `strict()`, `extended()`, `secondsOnly()`, `decimalSeconds()`. The `extended()` variant additionally accepts decimal seconds and ISO-8601 dates.
- `LimitRetryAfter` — `Predicate<HttpServletResponse>` that blocks retries when a `Retry-After` header requests a wait exceeding a configured maximum.

**`com.maybeitssquid.retry.resilience4j`** — Resilience4j integration:
- `Retry` interface — Static factory methods that return `Consumer<RetryConfig.Builder<HttpServletResponse>>` for wiring into Resilience4j `RetryConfig`. Overloads accept an optional `Duration` limit to enable `Retry-After` support alongside status code predicates.
- `HeedRetryAfter` — `IntervalBiFunction<HttpServletResponse>` that wraps an existing `IntervalBiFunction` and extends its wait to honor the `Retry-After` header.

The typical integration pattern is: call `Retry.idempotent(limit)` or `Retry.nonIdempotent(limit)` to get a consumer, then pass it to `RetryConfig.custom()` via `.apply()` or similar.

## Versioning

Release versions are derived automatically from git tags using [Palantir's gradle-git-version plugin](https://github.com/palantir/gradle-git-version). The plugin inspects the git repository and generates a version string. Tag names should use the format `v<major>.<minor>.<patch>` (e.g., `v1.0.0`); the `v` prefix is stripped in the published version.

To cut a release:
```bash
git tag v1.0.0
git push origin v1.0.0
```

The version is automatically picked up by the build and used in published artifacts.

## Code style

Spotless enforces Google Java Format. Run `./gradlew spotlessApply` before committing. The `module-info.java` file is excluded from Spotless.

## Security patches

For CVE patch management, see the `gradle-security-patch` skill. Use `/gradle-security-patch` to pin a CVE fix in the version catalog.
