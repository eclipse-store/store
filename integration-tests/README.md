# Integration tests

Everything under this directory is test code that exercises the Eclipse Store reactor as a user would. It is
only built with the `IT` profile of the root pom and never deployed. It replaces the former external
`microstream-test` suite and the Jenkins jobs that ran it.

| Module | Suite | Needs | Jenkins job it replaces |
|---|---|---|---|
| `core` | storage engine, GigaMap, Lazy collections, REST service, recovery, legacy mapping, ... | – | (already here) |
| `fixtures/types-library` | sample data for (almost) every handled type (`TypeEnum`), shared by the other suites | – | microstream-test-api |
| `fixtures/legacy-a`, `fixtures/legacy-b` | two shapes of the same classes for legacy type mapping | – | microstream-test-api |
| `jdk/*` | records, sealed classes, immutable and sequenced collections, Kotlin interop | JDK-gated profiles | vmt |
| `skills/*` | sample applications of the eclipse-store-skills guides, verified against the snapshot | Docker for `storage-targets-afs` | vmt |
| `afs/*` | one module per Abstract File System backend, all running the shared abstract tests from `afs/commons` (`sql-oracle` parked) | Docker (Testcontainers) | afs-all-in-one, afs-all-in-one-slow |
| `cache/jcache-tck` | JSR-107 TCK (published `javax.cache:cache-tests` test-jar) against the Eclipse Store cache | – | cache-tck-test |
| `cache/jcache-tck-annotations-{cdi,spring,guice}` | the TCK annotation tests through the published CDI / Spring / Guice harnesses | – | cache-tck-test |
| `cache/spring-hibernate` | Spring Boot + JPA app with the cache as Hibernate L2 and Spring cache, MySQL in Docker | Docker | cache-spring-integration |
| `compatibility/producer`, `compatibility/consumer` | storages written by a released version (`-Dcompatibility.producer.version`) or another JDK, read by the snapshot | – | compatible_* |
| `communication-legacy/host`, `communication-legacy/client` | legacy type mapping over the communication layer, host in a second JVM | – | legacy_test |
| `ui` | Selenium + Cucumber tests of the Vanilla-JS viewer, headless Chrome (`-P gui-tests`) | Chrome | gui-vanilla |

## Running

```bash
# build the reactor once (tests off), then run a suite
mvn -P IT install -DskipTests -pl integration-tests/afs/sql-mariadb -am
mvn -P IT test -pl integration-tests/afs/sql-mariadb

# everything except the slow AFS backends, as the nightly JDK matrix does
mvn -P IT test -pl 'integration-tests,!integration-tests/afs/kafka' -amd

# UI tests need a Chrome; headless by default, UI_HEADED=true shows the browser
mvn -P IT -P gui-tests test -pl integration-tests/ui

# compatibility: write with a release, read with the snapshot
mvn -P IT test -pl integration-tests/compatibility/producer -Dcompatibility.producer.version=4.2.0
mvn -P IT test -pl integration-tests/compatibility/consumer
```

The Kafka image is amd64 only. On an arm64 machine run that module with `-Dafs.kafka.image=confluentinc/cp-kafka:7.6.1`;
CI keeps the pinned default. `afs/sql-oracle` is parked (commented out in `afs/pom.xml`).

JVM flags for the surefire forks go through `extra.jvm.args`; the `jdk25-plus` profile pins
`-XX:-UseCompactObjectHeaders`. The CI matrix (`.github/workflows/it-jdk-matrix.yml`) overrides it for the
compact-object-headers leg.

## Workflows

| Workflow | Trigger | Scope |
|---|---|---|
| `maven_it_test.yml` | push, PR | `core` on JDK 17 and 25 |
| `it-jdk-matrix.yml` | nightly, push to main | all suites except slow AFS on Temurin 17 / 21 / 21-ZGC / 25 / 25-COH; EA JDKs nightly only, informational |
| `it-afs.yml` | push/PR touching afs, nightly | fast backends; Kafka nightly |
| `it-cache.yml` | push/PR touching cache, nightly | TCK, Spring/Hibernate |
| `it-compatibility.yml` | nightly | every released minor line (from Central metadata) and JDK 17 <-> 25 |
| `it-ui.yml` | push/PR touching storage/rest, nightly | browser tests |
