# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

RetryHTTP is a Java library that adds HTTP-awareness to [Resilience4j](https://resilience4j.readme.io). It provides `Predicate<HttpServletResponse>` and `IntervalBiFunction<HttpServletResponse>` implementations that make retry decisions based on HTTP status codes and `Retry-After` response headers, operating on `jakarta.servlet.http.HttpServletResponse`.

## Commands

```bash
./gradlew build          # compile, test, and lint (spotless check)
./gradlew test           # run tests only
./gradlew spotlessApply  # auto-format Java source (run before committing)
./gradlew spotlessCheck  # check formatting without applying
./gradlew javadoc        # generate Javadoc
./gradlew dependencyCheckAnalyze  # OWASP CVE scan (slow; fails build at CVSS >= 7)
```

To run a single test class:
```bash
./gradlew test --tests "com.maybeitssquid.retry.RetryAfterParserTest"
```

Java toolchain: Java 21. CI also tests against Java 17 and 25.

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

## Code Style

Spotless enforces Google Java Format. Run `./gradlew spotlessApply` before committing. The `module-info.java` file is excluded from Spotless.

## Security Patches

`gradle/libs.versions.toml` maintains a `security-patches` bundle of transitive dependency version constraints pinned to safe minimums. `settings.gradle` eagerly resolves these patches into the buildscript classpath so plugin dependencies are also covered. When adding a new CVE patch, add it to both the `[libraries]` section and the `security-patches` bundle.
