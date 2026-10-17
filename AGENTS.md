# Codebase Guidance

This file documents key information about the project architecture, build commands, code style, and security practices.

## Project overview

RetryHTTP is a Java library that adds HTTP-awareness to [Resilience4j](https://resilience4j.readme.io). It provides `Predicate<HttpServletResponse>` and `IntervalBiFunction<HttpServletResponse>` implementations that make retry decisions based on HTTP status codes and `Retry-After` response headers, operating on `jakarta.servlet.http.HttpServletResponse`. Published to GitHub Packages as `com.maybeitssquid:retry-http`.

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

**NVD key (optional):** `dependencyCheckAnalyze` reads the key from the `NVD_API_KEY` environment variable or the `nvd.apiKey` Gradle property. With a key, it updates the NVD data live. Without one, it uses the local NVD cache and logs a warning. CI runs without a key, so it also uses the cache.

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

## Versioning and Releases

Versions are derived from git tags using [`gradle-git-version`](https://github.com/palantir/gradle-git-version):

- **On a tag** (e.g., `v1.0.0`) → version = `1.0.0`
- **After a tag** → version = tag + distance + commit hash (e.g., `1.0.1-3-gABC1234` = 3 commits after v1.0.0)
- **No tags yet** → version synthesized from git history (e.g., `0.0.1-dev-88-gXYZ`)

**To create a release:**

```bash
# Ensure all commits are pushed
git push origin main

# Create and push the tag (triggers automatic version picking in build)
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0

# Publish the GitHub Release; gradle-publish.yml publishes to GitHub Packages
gh release create v1.0.0 --title "v1.0.0" --generate-notes
```

**To delete a release tag:**

```bash
git tag -d v1.0.0              # Delete locally
git push origin :v1.0.0        # Delete from remote
```

## Shared build sections

`build.gradle` and `settings.gradle` wrap the build logic this project shares with the other Java/Gradle projects in `// shared:begin NAME` and `// shared:end NAME` markers. The canonical copies live in `claude-skills/init-java-project/shared/`. The `shared-build` CI job checks out that directory and fails if a wrapped section differs from its canonical copy. Indentation, blank lines and comment lines are ignored. To change a shared section, change the canonical copy and the same section in every project in one change set. Run the check locally with `bash <claude-skills>/init-java-project/shared/check-shared-sections.sh . <claude-skills>/init-java-project/shared`.

## Code style

Spotless enforces Google Java Format. Run `./gradlew spotlessApply` before committing. The `module-info.java` file is excluded from Spotless.

## Security patches

For CVE patch management, see the `gradle-security-patch` skill. Use `/gradle-security-patch` to pin a CVE fix in the version catalog.
