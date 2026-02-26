# Build & Deploy Optimization Analysis

**Project:** EclipseStore (`store`)
**Date:** 2026-02-26
**Problem:** Snapshot deploy takes 15–30 minutes, severely slowing down development.

---

## 1. Overview of Current Build

| Metric                  | Value                                       |
|-------------------------|---------------------------------------------|
| Total pom.xml modules   | 47 (excluding examples)                     |
| Leaf JAR modules        | ~34                                         |
| Parent POM modules      | 13 (also deployed)                          |
| Total JAR artifacts     | ~69 files (JARs + source JARs)              |
| Total artifact size     | ~79 MB (of which 75 MB is one standalone JAR) |
| Snapshot repository     | Sonatype Central (`central.sonatype.com`)   |

---

## 2. Identified Problems (Ordered by Impact)

### 🔴 P1 — `maven-source-plugin` runs ALWAYS, not just during deploy

**Impact: HIGH — doubles the number of uploaded artifacts**

The `maven-source-plugin` with `jar-no-fork` goal is declared in the root `<build><plugins>` section (line 152 of root pom.xml), which means it runs on **every build for every module** — including `mvn install` and `mvn deploy`.

This means every module produces and uploads a `-sources.jar` alongside the main JAR. For 34 leaf modules, that's **34 extra uploads** to the snapshot repository.

**Recommendation:** Move `maven-source-plugin` into the `deploy` profile alongside `maven-javadoc-plugin` and `maven-gpg-plugin`. For snapshot deploys, source JARs are usually not needed at all.

```xml
<!-- Move from <build><plugins> to <profiles><profile id="deploy"> -->
<profile>
    <id>deploy</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-gpg-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</profile>
```

---

### 🔴 P2 — `license-maven-plugin` runs on EVERY build

**Impact: HIGH — scans every Java file in every module on every build**

The `license-maven-plugin` executes `update-file-header` and `update-project-license` during `process-sources` for **every module, every build**. This scans all `**/*.java` files and processes them even if nothing changed.

This is declared in root `<build><plugins>` (line 148).

**Recommendation:** Move to a dedicated profile (e.g., `license-check`) that is only activated manually or in CI. License headers don't change between snapshot deploys.

---

### 🔴 P3 — `<updatePolicy>always</updatePolicy>` on snapshot repository

**Impact: HIGH — every module resolution re-checks all SNAPSHOTs from remote**

Root pom.xml line 86:
```xml
<updatePolicy>always</updatePolicy>
```

This forces Maven to check the remote snapshot repository for **every SNAPSHOT dependency on every build**. Since you depend on `eclipse.serializer.version=4.0.0-SNAPSHOT`, this triggers HTTP requests for every serializer artifact resolution.

With 47 modules, many of which depend on serializer SNAPSHOTs, this can add **minutes of network latency** just for dependency resolution.

**Recommendation:** Change to `daily` or `interval:60`. During active development, use `-nsu` (no snapshot updates) flag:
```bash
mvn deploy -nsu
```

---

### 🟠 P4 — `maven-bundle-plugin` (OSGi manifest) runs on every module

**Impact: MEDIUM — adds ~1-2 seconds per module × 34 modules**

The `maven-bundle-plugin` generates OSGi MANIFEST.MF for every module in `process-classes` phase. This is declared in root `<build><plugins>`.

Question: **Do your users actually consume EclipseStore via OSGi?** If not, this is pure overhead.

**Recommendation:** If OSGi is not critical, move to a `release` or `osgi` profile. If it is needed, at least skip it for modules that are never consumed as OSGi bundles (e.g., rest client-app, standalone assembly, embedded-tools).

---

### 🟠 P5 — `central-publishing-maven-plugin` is always active as extension

**Impact: MEDIUM — extension loaded for every build, even `mvn install`**

Line 160 of root pom.xml:
```xml
<plugin>
    <groupId>org.sonatype.central</groupId>
    <artifactId>central-publishing-maven-plugin</artifactId>
</plugin>
```

This is in the main `<build><plugins>`, meaning it initializes for every build lifecycle.

**Recommendation:** Move to the `deploy` profile, or guard with a property so it doesn't interfere during `mvn install` or `mvn package`.

---

### 🟠 P6 — Standalone assembly JAR (75 MB) gets deployed

**Impact: MEDIUM — single artifact is 75 MB**

`storage-restclient-app-standalone-assembly` produces a 75 MB fat JAR. If this gets uploaded to the snapshot repository, it alone takes significant time.

**Recommendation:** Add `<maven.deploy.skip>true</maven.deploy.skip>` to this module's properties, similar to what was done for `storage-converter` and `storage-migrator`. Users build this from source.

---

### 🟠 P7 — `integrations-spring-boot3-itest` is deployed as a library

**Impact: LOW-MEDIUM — unnecessary deploy of a test-only module**

The `itest` module is an integration test project (comment in parent POM says so). It has no `maven.deploy.skip`. It should not be published.

**Recommendation:** Add `<maven.deploy.skip>true</maven.deploy.skip>` to `integrations/itest/pom.xml`.

---

### 🟡 P8 — `surefire.rerunFailingTestsCount=2` — tests run up to 3× on failure

**Impact: VARIABLE — can dramatically increase build time on flaky tests**

Root pom.xml:
```xml
<surefire.rerunFailingTestsCount>2</surefire.rerunFailingTestsCount>
<failsafe.rerunFailingTestsCount>2</failsafe.rerunFailingTestsCount>
```

If a test fails, it re-runs up to 2 more times. With 47 modules, even one flaky test per module can add significant time.

**Recommendation:** Set to `0` for snapshot deploys. Use a CI-specific profile to re-run.

---

### 🟡 P9 — No parallel build `-T`

**Impact: MEDIUM — sequential build of 47 modules**

There is no `.mvn/maven.config` configured for parallel builds. On a machine with multiple cores, parallel builds can cut build time significantly.

**Recommendation:** Create `.mvn/maven.config`:
```
-T 1C
```
Or use `-T 1C` on the command line. Test for stability first — the module dependency graph should allow significant parallelism (afs/* modules are independent of cache/*, gigamap/*, etc.).

---

### 🟡 P10 — `maven-enforcer-plugin` runs twice per module

**Impact: LOW — minor overhead, adds up**

The enforcer runs:
1. `enforce-env` (from root pom.xml `<build><plugins>`)
2. `enforce-files-exist` (from `module-info-check` profile, auto-activated when `src/main/java` exists)

These are fast but still 2 executions × 47 modules = 94 plugin invocations.

**Recommendation:** No urgent action needed, but consider running enforcer only in CI.

---

### 🟡 P11 — Vaadin `prepare-frontend` runs on every build of `client-app`

**Impact: LOW-MEDIUM — npm/node operations add seconds**

The `vaadin-maven-plugin` with `prepare-frontend` goal runs on every build. Vaadin frontend preparation involves npm operations and file scanning.

**Recommendation:** If the client-app is not being changed, skip it with `maven.deploy.skip=true` or exclude it from the reactor: `mvn deploy -pl '!storage/rest/client-app,!storage/rest/client-app-standalone-assembly'`

---

## 3. Quick Wins (Do These First)

| # | Action | Expected Savings |
|---|--------|-----------------|
| 1 | Add `-nsu` (no snapshot updates) to deploy command | 2–5 min |
| 2 | Move `maven-source-plugin` to `deploy` profile | Halves artifacts for non-release deploys |
| 3 | Move `license-maven-plugin` to its own profile | 1–3 min (I/O per module) |
| 4 | Skip deploy for `client-app-standalone-assembly` | 75 MB less upload |
| 5 | Skip deploy for `integrations/itest` | Minor |
| 6 | Use `-T 1C` parallel build | 30–50% faster compilation |
| 7 | Change `<updatePolicy>always</updatePolicy>` → `daily` | Avoids redundant remote checks |

---

## 4. Recommended Deploy Command

### Current (likely):
```bash
mvn clean deploy
```

### Optimized for snapshot deploy:
```bash
mvn deploy -T 1C -nsu -DskipTests
```

### Optimized for full CI deploy (release-quality):
```bash
mvn clean deploy -Pdeploy -T 1C
```

---

## 5. Modules That Should NEVER Be Deployed

These modules should have `<maven.deploy.skip>true</maven.deploy.skip>`:

| Module | Reason | Status |
|--------|--------|--------|
| `storage-embedded-tools-storage-converter` | Build from source | ✅ Already done |
| `storage-embedded-tools-storage-migrator` | Build from source | ✅ Already done |
| `storage-restclient-app-standalone-assembly` | 75 MB fat JAR, build from source | ❌ Missing |
| `integrations-spring-boot3-itest` | Test-only module | ❌ Missing |

---

## 6. Summary

The biggest contributors to the 15–30 minute deploy time are likely:

1. **Network overhead** — `updatePolicy=always` forces remote resolution for every SNAPSHOT dependency
2. **Unnecessary artifacts** — source JARs generated and uploaded for every module, every deploy
3. **License scanning** — full file scan every build, no caching
4. **Sequential build** — no `-T` parallelism
5. **Large artifact** — 75 MB standalone assembly uploaded every time

By implementing the quick wins in section 3, the deploy time should drop to **3–7 minutes**.

