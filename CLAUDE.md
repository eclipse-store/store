# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EclipseStore is a high-performance Java-native persistence library (object graph store). It is built on top of [Eclipse Serializer](https://github.com/eclipse-serializer/serializer) and allows storing/loading Java object graphs without JPA overhead. Current version: `5.0.0-SNAPSHOT`. License: EPL-2.0.

## Build Commands

**Requirements:** Java 17, Maven 3.9+

```bash
# Build everything (excluding examples)
mvn install

# Build including examples
mvn install -P examples

# Skip tests
mvn install -DskipTests

# Run all tests for a single module
mvn test -pl storage/embedded

# Run a specific test class
mvn test -pl <module-path> -Dtest=ClassName

# Run a specific test method
mvn test -pl <module-path> -Dtest=ClassName#methodName

# Run slow tests (excluded by default)
mvn test -P slow-tests

# Check license headers
mvn verify -P license-check

# Check module-info consistency
mvn verify -P module-info-check
```

Failing tests are automatically retried twice (surefire and failsafe both set `rerunFailingTestsCount=2`).

## Module Structure

| Directory | Purpose |
|---|---|
| `afs/` | Abstract File System — storage backend implementations (AWS S3, Azure Blob, GCS, Oracle Cloud, Redis, Kafka, SQL, NIO) |
| `storage/` | Core storage engine: `storage/` (API & types), `embedded/` (embedded DB impl), `embedded-configuration/`, `embedded-tools/` (converter, migrator), `rest/` (REST API with multiple server adapters) |
| `cache/` | Caching layer: `cache/` (JCache implementation), `hibernate/` (Hibernate second-level cache) |
| `integrations/` | Framework integrations: `spring-boot3/`, `cdi4/`, plus `itest/` for integration tests |
| `gigamap/` | High-performance indexed map/search: core `gigamap/`, `lucene/` (full-text), `jvector/` (vector similarity), `concurrency-test/` |
| `examples/` | Standalone example apps (built only with `-P examples` profile) |
| `docs/` | Antora-based documentation source |

## Architecture

### Layered Design

```
Framework Integrations (Spring Boot, CDI4)
         ↓
   Embedded Storage API
         ↓
   Core Storage Engine
         ↓
Abstract File System (AFS)
         ↓
Backend Implementations (NIO / S3 / Azure / Redis / SQL / ...)
```

### Key Abstractions

- **AFS (Abstract File System)**: `afs/blobstore/` defines the base abstraction; all cloud/DB backends implement it. NIO is the default local filesystem backend.
- **Embedded Storage**: The main entry point for application code. `EmbeddedStorage` / `EmbeddedStorageManager` from `storage/embedded` is what most users interact with.
- **Type Handlers**: Pluggable serialization handlers for custom types, registered via Eclipse Serializer infrastructure.
- **REST layer**: Multiple server adapters (Javalin, Spring Boot) all implement the same `storage/rest/service` interface.

### Java Module System

All production modules have `module-info.java`. When adding code, respect existing `exports` and `requires` declarations. The `module-info-check` profile enforces consistency.

## Testing

- Slow tests are annotated `@Tag("slow")` and excluded from the default test run.
- Integration tests live in `integrations/itest/`.
- The `gigamap/concurrency-test/` module is a standalone concurrency stress test, not part of the main test suite.

## Code Conventions

- All source files must carry the EPL-2.0 license header (enforced by `license-maven-plugin`). The header template is in `etc/license/`.
- All commits must be GPG-signed (Eclipse Foundation requirement).
- Group ID for all artifacts: `org.eclipse.store`.

## Workflow

- After creating any new non-Markdown file, immediately stage it with `git add <path>`.


