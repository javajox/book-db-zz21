# Implementation decisions

These decisions complete the dependency and deployment planning in [stage 1](tasks.md#1-confirm-the-baseline-and-resolve-implementation-dependencies). Functional requirements remain defined in [system_specification.md](system_specification.md).

## Dependency selection

Keep Java 25, Spring Boot 4.1.0, Vaadin 25.2.5, and Gradle Wrapper 9.6.1. The Spring Boot plugin and dependency-management plugin import the fixed Spring Boot 4.1.0 dependency BOM. The dependencies below therefore have pinned versions even though the Gradle dependency declarations omit individual version strings. The Vaadin BOM remains pinned to 25.2.5.

| Component | Dependency | Selected version | Purpose |
|-----------|------------|------------------|---------|
| Spring JDBC | `org.springframework:spring-jdbc` | `7.0.8` | Blocking SQL access and JDBC transaction support. |
| SQLite JDBC | `org.xerial:sqlite-jdbc` | `3.53.2.0` | SQLite driver and bundled native libraries; runtime dependency. |
| Flyway | `org.flywaydb:flyway-core` | `12.4.0` | Versioned SQL migrations, with SQLite support included in core. |
| Validation starter | `org.springframework.boot:spring-boot-starter-validation` | `4.1.0` | Jakarta validation integration for future form/service inputs. |
| Hibernate Validator | `org.hibernate.validator:hibernate-validator` | `9.1.0.Final` | Validation implementation selected by the Boot BOM. |
| Test starter | `org.springframework.boot:spring-boot-starter-test` | `4.1.0` | Existing JUnit Jupiter, AssertJ, and Spring test support. |

Flyway's SQLite implementation is present in `flyway-core`; no separate database plugin or database server is required. Use versioned SQL files and let migration exceptions propagate through Spring initialization so an unsuccessful migration prevents startup.

Stage 1 adds the core Spring JDBC and Flyway libraries. Production data-source configuration, connection initialization, migration startup integration, and the application schema belong to stages 5 and 6. Using the core libraries at this stage preserves the existing runnable GUI without enabling an unconfigured data-source auto-configuration or creating an application database prematurely. No auto-configuration exclusions or temporary in-memory database are introduced.

Spring Boot DevTools and Vaadin development tooling remain `developmentOnly`. JUnit and the migration fixtures remain test-only. SQLite uses native code, so Gradle's `test` and `bootRun` tasks enable native access for classpath libraries on Java 25. Packaged startup instructions should include the same JVM option:

```bash
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar
```

This option grants native access to the classpath dependencies used by the application. Java 25 otherwise warns when the SQLite driver loads its native library.

Dependency sources:

- [Spring Boot 4.1.0 dependency BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom).
- [Flyway 12.4.0 core artifact](https://repo.maven.apache.org/maven2/org/flywaydb/flyway-core/12.4.0/).
- [SQLite JDBC 3.53.2.0 artifact](https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.2.0/).

## Compatibility verification

The baseline production build was verified from a fresh export of the committed repository using the tracked Gradle Wrapper and Java 25.0.4. The build may download dependencies and use the normal Gradle cache; it does not rely on an existing project build directory or generated frontend files.

`PersistenceDependenciesTest` uses temporary, file-backed SQLite databases and migration fixtures under `src/test/resources/db/`. It verifies:

1. A first migration creates a usable schema, and Spring JDBC can insert and read data through Xerial's driver.
2. A later migration upgrades a populated database while retaining existing data, and another migration run makes no changes.
3. A broken migration aborts a test Spring application during bean initialization, reports the failing migration, rolls back that migration's schema changes, and preserves previously committed data.

These are library integration checks. The fixtures contain only a small probe table; they are not the application's schema, and the startup probe is excluded from normal application component scanning. Production `bookdb.*` configuration, WAL/busy-timeout setup, and actual migration lifecycle verification are still tasks for stages 5 and 6.

Run the checks with `./gradlew test`. Verify production packaging with `./gradlew clean build -Pvaadin.productionMode=true`.

## Initial deployment and access policy

This section is the authoritative record of the policy summarized in [Verification and remaining decisions](system_specification.md#verification-and-remaining-decisions). The table below defines the initial hosting arrangement, GUI operator authentication, and API access policy. Implement and verify these controls in stage 14 before network deployment; selecting the policy in stage 1 does not enable them in the starter application.

| Area | Decision |
|------|----------|
| Application process | Run one executable Spring Boot JAR under a dedicated operating-system account on one host. |
| Database | Keep SQLite and its WAL/journal files on a writable local persistent filesystem on that host. Use an explicit absolute database path in deployment configuration. |
| Operator activity | One operator works with the database at a time. API clients may read while the operator makes changes. |
| Network entry point | Expose the embedded Spring Boot web server directly from the executable JAR to the intended private network. |
| HTTPS | Configure TLS directly in Spring Boot with certificate material stored outside the JAR and repository and trusted by operator browsers and integration clients. Provision the certificate through the hosting environment's certificate process and document renewal during deployment. |
| GUI authentication | One provisioned operator account with form login and logout. Store a BCrypt password hash in external configuration. No default password or public registration. |
| API access | Require a separate bearer token for `/books-brief` and `/books-full`. The token grants read access to these endpoints and does not grant access to the management GUI. |
| Credential storage | Keep credentials and configuration outside the JAR and repository, readable only by the application/service administrator as appropriate. |

During stage 14, provide a local provisioning procedure to select the operator username, hash the operator's chosen password, and generate an API token from at least 32 cryptographically random bytes. Give the raw token to the intended integration client through the deployment's credential channel, and keep its SHA-256 verification digest in the application's external configuration. A high-entropy API token is verified independently of the operator password; raw passwords and tokens must not be logged.

Provision required credentials before network deployment. Missing required security configuration must prevent the secured deployment from starting rather than enabling anonymous management access. Password changes and token rotation update external configuration and restart the single application instance; the initial deployment does not require an account-management database or public registration endpoints. Document exact security property names and the provisioning commands when implementing stage 14.

GUI requests without a valid operator session go to the login flow. API requests use token authentication and return JSON authentication errors instead of redirects or HTML; define those access-related responses in the API contract during stage 14. Preserve Spring Security's protection of state-changing GUI requests and configure Vaadin's required HTTP/WebSocket traffic.

The database is not shared over a network filesystem and multiple application instances are not supported. Simultaneous-operator conflict handling, edit locks, public write APIs, and API pagination/filtering/sorting remain outside the first release.

## Local files and version control

Use the ignored root directories `data/`, `backups/`, and `logs/` for local runtime files. Use `config/`, `secrets/`, or ignored `.env`/`*.local.properties`/`*.local.yml`/`*.local.yaml` files for deployment settings and credentials. Environment files must be loaded by the deployment tooling; Spring Boot does not automatically read `.env` files.

The ignore rules also cover common SQLite file extensions and their companion files, private key files, and common keystore formats. `.env.example` remains eligible for tracking and must contain placeholders only. Source-controlled application defaults and SQL migration fixtures under `src/` remain eligible for tracking, as does `gradle/wrapper/gradle-wrapper.jar`.
