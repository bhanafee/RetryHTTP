# Codebase Guidance

This file documents key information about the project architecture, build commands, code style, and security practices.

## Project overview

RetryHTTP is a Java library that adds HTTP-awareness to [Resilience4j](https://resilience4j.readme.io). It provides `Predicate<HttpServletResponse>` and `IntervalBiFunction<HttpServletResponse>` implementations that make retry decisions based on HTTP status codes and `Retry-After` response headers, operating on `jakarta.servlet.http.HttpServletResponse`. Published to GitHub Packages as `com.maybeitssquid:RetryHTTP`.

## Commands

**Build and test:**
```bash
./gradlew build              # compile, test, spotless check
./gradlew test               # run all tests
./gradlew test --tests "*ParserTest"           # run tests by class name
./gradlew test --tests "*ParserTest.test*"     # run tests by method pattern
```

**Code quality:**
```bash
./gradlew spotlessApply           # auto-format (required before commit)
./gradlew dependencyCheckAnalyze  # OWASP vulnerability scan (slow; fails at CVSS ≥ 7)
```

**External dependencies:** Standalone library; integrates with Resilience4j at the application layer.

Build uses Java 25 toolchain, compiles to Java 17 bytecode (`release = "17"`). CI tests on Java 17, 21, and 25.

## Key Entry Points

- **`Retry`** — factory interface; static methods `idempotent()`, `nonIdempotent()` return consumers for wiring into `RetryConfig`
- **`RetryStatusCodes`** — predicate for HTTP status code decisions
- **`RetryAfterParser`** — parser for `Retry-After` header values

## Architecture

Two packages:

**`com.maybeitssquid.retry`** — Core classes:
- `RetryStatusCodes` — maps HTTP status codes to retry decisions
- `RetryAfterParser` — parses `Retry-After` headers
- `LimitRetryAfter` — blocks retries when `Retry-After` exceeds maximum

**`com.maybeitssquid.retry.resilience4j`** — Resilience4j integration:
- `Retry` — factory for wiring into `RetryConfig`
- `HeedRetryAfter` — extends wait to honor `Retry-After` header

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
