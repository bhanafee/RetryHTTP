# Graph Report - .  (2026-08-01)

## Corpus Check
- Corpus is ~10,391 words - fits in a single context window. You may not need a graph.

## Summary
- 193 nodes · 420 edges · 24 communities (14 shown, 10 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 33 edges (avg confidence: 0.8)
- Token cost: 37,879 input · 0 output

## Community Hubs (Navigation)
- Test Infrastructure
- HTTP Retry Core
- Retry-After Header Support
- Resilience4j Configuration
- HTTP Retry Architecture
- Retry Limiting Logic
- Pattern Matching
- Release & Versioning
- Build Scripts
- Documentation Rendering
- HTTP Standards & Design
- Java Version Matrix
- Documentation Deployment
- Gradle Wrapper
- Java Toolchain
- Security Patches
- Community Standards
- Code Coverage
- Testing Framework
- Mock Framework

## God Nodes (most connected - your core abstractions)
1. `RetryAfterParserTest` - 23 edges
2. `RetryTest` - 17 edges
3. `RetryAfterParser` - 14 edges
4. `HeedRetryAfterTest` - 13 edges
5. `RetryStatusCodes` - 11 edges
6. `HeedRetryAfter` - 10 edges
7. `RetryStatusCodesTest` - 9 edges
8. `LimitRetryAfter` - 7 edges
9. `Retry` - 7 edges
10. `LimitRetryAfterTest` - 7 edges

## Surprising Connections (you probably didn't know these)
- `Spotless Google Java Format enforcement` --rationale_for--> `RetryStatusCodes`  [INFERRED]
  CLAUDE.md → README.md
- `RetryStatusCodes (architecture)` --references--> `RetryStatusCodes`  [EXTRACTED]
  CLAUDE.md → README.md
- `RetryAfterParser (architecture)` --references--> `RetryAfterParser`  [EXTRACTED]
  CLAUDE.md → README.md
- `Retry interface (architecture)` --references--> `Retry`  [EXTRACTED]
  CLAUDE.md → README.md
- `CI test matrix (Java 17, 21, 25)` --references--> `Java 25 toolchain (runs on 17+)`  [INFERRED]
  .github/workflows/gradle.yml → README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Core HTTP-aware retry predicates** — readme_retrystatus codes, readme_limitretryafter, readme_httpstatuscodeproblem [EXTRACTED 1.00]
- **Resilience4j integration factory and functions** — readme_retry, readme_heedretryafter, readme_com_maybeitssquid_retry_resilience4j [EXTRACTED 1.00]
- **HTTP retry awareness decision flow** — readme_statuscodedecisions, readme_retryafterheadersupport, readme_idempotenceconcept [EXTRACTED 1.00]

## Communities (24 total, 10 thin omitted)

### Community 0 - "Test Infrastructure"
Cohesion: 0.14
Nodes (12): CsvSource, InstantSource, Logger, ParameterizedTest, SafeVarargs, HttpServletResponse, RetryAfterParser, ExtendWith (+4 more)

### Community 1 - "HTTP Retry Core"
Cohesion: 0.16
Nodes (10): Builder, HttpServletResponse, Retry, HttpServletResponse, Override, RetryStatusCodes, ExtendWith, HttpServletResponse (+2 more)

### Community 2 - "Retry-After Header Support"
Cohesion: 0.21
Nodes (11): IntervalBiFunction, HeedRetryAfter, Either, HttpServletResponse, Override, HeedRetryAfterTest, Either, ExtendWith (+3 more)

### Community 3 - "Resilience4j Configuration"
Cohesion: 0.28
Nodes (8): RetryConfig, Builder, ExtendWith, HttpServletResponse, IntervalBiFunction, Test, RetryTest, SuppressWarnings

### Community 4 - "HTTP Retry Architecture"
Cohesion: 0.13
Nodes (20): HeedRetryAfter (architecture), LimitRetryAfter (architecture), Retry interface (architecture), RetryAfterParser (architecture), RetryStatusCodes (architecture), Spotless Google Java Format enforcement, com.maybeitssquid.retry, com.maybeitssquid.retry.resilience4j (+12 more)

### Community 5 - "Retry Limiting Logic"
Cohesion: 0.22
Nodes (7): HttpServletResponse, Override, LimitRetryAfter, ExtendWith, HttpServletResponse, Test, LimitRetryAfterTest

### Community 6 - "Pattern Matching"
Cohesion: 0.40
Nodes (3): Pattern, Override, PatternGuarded

### Community 7 - "Release & Versioning"
Cohesion: 0.50
Nodes (4): Palantir gradle-git-version plugin versioning, Dependabot Gradle dependency updates, GitHub release publishing workflow, Gradle 9.6.1

### Community 8 - "Build Scripts"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 10 - "HTTP Standards & Design"
Cohesion: 0.67
Nodes (3): HTTP status code retry awareness problem, Retry-After header support, RFC-7231 section 7.1.3 (Retry-After header)

## Knowledge Gaps
- **8 isolated node(s):** `RFC-7231 section 4.2.2 (idempotent methods)`, `RFC-7231 section 7.1.3 (Retry-After header)`, `Jakarta Servlet API 6.1+`, `Java 25 toolchain (runs on 17+)`, `JUnit 6.1.0` (+3 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **10 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RetryAfterParser` connect `Test Infrastructure` to `Retry-After Header Support`, `Pattern Matching`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **Why does `RetryStatusCodes` connect `HTTP Retry Core` to `Retry Limiting Logic`?**
  _High betweenness centrality (0.111) - this node is a cross-community bridge._
- **What connects `RFC-7231 section 4.2.2 (idempotent methods)`, `RFC-7231 section 7.1.3 (Retry-After header)`, `Jakarta Servlet API 6.1+` to the rest of the system?**
  _8 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Test Infrastructure` be split into smaller, more focused modules?**
  _Cohesion score 0.1378048780487805 - nodes in this community are weakly interconnected._
- **Should `HTTP Retry Architecture` be split into smaller, more focused modules?**
  _Cohesion score 0.13157894736842105 - nodes in this community are weakly interconnected._