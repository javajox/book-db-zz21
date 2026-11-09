# Implementation decisions

These decisions complete the dependency and deployment planning in [stage 1](tasks.md#1-confirm-the-baseline-and-resolve-implementation-dependencies). Functional requirements remain defined in [system_specification.md](system_specification.md).

## Dependency selection

Keep Java 25, Spring Boot 4.1.0, Vaadin 25.2.5, and Gradle Wrapper 9.6.1. The Spring Boot plugin and dependency-management plugin import the fixed Spring Boot 4.1.0 dependency BOM. The dependencies below therefore have pinned versions even though the Gradle dependency declarations omit individual version strings. The Vaadin BOM remains pinned to 25.2.5.

| Component | Dependency | Selected version | Purpose |
|-----------|------------|------------------|---------|
| Spring JDBC | `org.springframework:spring-jdbc` | `7.0.8` | Blocking SQL access and JDBC transaction support. |
| SQLite JDBC | `org.xerial:sqlite-jdbc` | `3.53.2.0` | SQLite driver and bundled native libraries. |
| Flyway | `org.flywaydb:flyway-core` | `12.4.0` | Versioned SQL migrations, with SQLite support included in core. |
| Validation starter | `org.springframework.boot:spring-boot-starter-validation` | `4.1.0` | Jakarta validation integration for future form/service inputs. |
| Hibernate Validator | `org.hibernate.validator:hibernate-validator` | `9.1.0.Final` | Validation implementation selected by the Boot BOM. |
| Test starter | `org.springframework.boot:spring-boot-starter-test` | `4.1.0` | Existing JUnit Jupiter, AssertJ, and Spring test support. |

Flyway's SQLite implementation is present in `flyway-core`; no separate database plugin or database server is required. Use versioned SQL files and let migration exceptions propagate through Spring initialization so an unsuccessful migration prevents startup.

The SQLite driver was a `runtimeOnly` dependency through stage 4, when nothing compiled against it. Stage 5 promoted it to `implementation`, because the data-source configuration sets foreign keys, WAL journaling, and the busy timeout through `SQLiteConfig` so that the driver applies them as it opens each connection. The alternative was to express those settings as JDBC URL parameters and keep the driver off the compile classpath, which would leave a mistyped pragma name to be ignored in silence; a typed configuration is checked by the compiler instead. The project is committed to SQLite throughout the specification, so there is no portability being preserved by holding the driver at arm's length.

Stage 1 adds the core Spring JDBC and Flyway libraries. Production data-source configuration, connection initialization, migration startup integration, and the application schema belong to stages 5 and 6. Using the core libraries at this stage preserves the existing runnable GUI without enabling an unconfigured data-source auto-configuration or creating an application database prematurely. No auto-configuration exclusions or temporary in-memory database are introduced.

Spring Boot DevTools needs one auto-configuration excluded, in `src/main/resources/application.properties`. Its `DevToolsDataSourceAutoConfiguration` exists to keep an in-memory development database alive across restarts, and its condition class references `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties` — a class in Spring Boot 4's separate `spring-boot-jdbc` module, which this project does not use because it configures its own SQLite data source on plain `spring-jdbc`. With that module absent the condition cannot be evaluated at all, and startup fails during context initialization with `IllegalStateException: Could not evaluate condition on ... DevToolsDataSourceAutoConfiguration`.

This affects every development run — an IDE launch and `./gradlew bootRun` alike — while the executable JAR is unaffected, since the Spring Boot plugin excludes DevTools from it. The failure therefore does not appear in any packaged smoke test, which is why it went unnoticed until the application was first started from an IDE.

Excluding that one auto-configuration keeps DevTools' restart-on-change and discards a feature that is meaningless here: the database is a file on disk, not an in-memory one needing rescue between restarts. The exclusion is harmless in production, because Spring Boot ignores an exclusion naming a class that is not on the classpath; both paths were verified after the change.

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

### How the policy is implemented

Stage 14 implemented the above. The configuration contract adds three required settings, supplied the same three ways as the database settings — configuration file, environment variables, or command-line arguments:

| Property | Meaning |
|----------|---------|
| `bookdb.security.operator-username` | The single provisioned operator account. |
| `bookdb.security.operator-password-hash` | That account's BCrypt password hash. |
| `bookdb.security.api-token-digest` | The SHA-256 digest, in lowercase hexadecimal, of the API bearer token. |

All three are required and there is no built-in account: a missing or malformed value fails startup with a message naming the provisioning command. This is deliberate and is the point of the arrangement — a deployment that refuses to start is a problem someone fixes in a minute, while one that starts unsecured is a problem nobody notices. Both values are stored only in derived form, so the configuration file never holds a credential that could be replayed, and both are checked for shape as well as presence because the realistic mistake is pasting the plain password or the raw token into the property and believing it was secured.

Provision them on the deployment host from the same artifact:

```bash
java -jar book-db-zz21.jar provision-credentials operator
```

The tool prompts for the password rather than accepting it as an argument, since an argument is visible to every process listing on the host and lands in shell history. It prints the three property lines plus a freshly generated token from 32 cryptographically random bytes; the token is shown once and cannot be recovered from the stored digest.

Two filter chains serve the two audiences. The API chain matches only `/books-brief` and `/books-full`, is stateless, authenticates the `Authorization: Bearer` header by comparing SHA-256 digests with a constant-time comparison, and answers an unauthenticated request with HTTP `401` and the `unauthorized` JSON error defined in the API contract — never a redirect. The GUI chain uses Vaadin's `VaadinSecurityConfigurer`, which keeps Vaadin's internal requests, framework resources and CSRF handling working while every route is access-checked; route access is expressed as annotations on the views, with `@AnonymousAllowed` on the login view alone and `@PermitAll` on the operator views, so a new view with no annotation fails closed rather than being published. The API token grants nothing in the GUI: presenting it to `/authors` still redirects to the login page.

### HTTPS and reverse proxies

TLS is configured directly in Spring Boot, with certificate material outside the JAR and outside version control:

```bash
java -jar book-db-zz21.jar \
  --server.ssl.enabled=true \
  --server.ssl.key-store=/etc/book-db-zz21/bookdb.p12 \
  --server.ssl.key-store-type=PKCS12 \
  --server.ssl.key-store-password=... \
  --server.ssl.key-alias=bookdb
```

This was verified with a generated PKCS12 keystore: the server reported `Tomcat started on port 18443 (https)`, negotiated TLSv1.3, and every access rule held over TLS — the GUI redirected to the login page, the login page served, the API returned `401` without a token and `200` with one.

The initial deployment exposes the embedded server directly, so no reverse proxy is involved. If one is introduced later and terminates TLS, set `server.forward-headers-strategy=framework` so Spring and Vaadin build absolute URLs and redirects from the forwarded headers rather than from the internal connection; without it a redirect to the login page can point at `http` or at the internal port. A proxy must also forward WebSocket upgrade headers if Vaadin push is ever enabled, and `.p12`, `.jks` and `.key` files are already covered by the repository ignore rules.

## Storing the weight

The specification's attribute table originally named the weight column `weight`, matching its JSON key. The schema stores `weight_grams INTEGER` instead, and the specification now names that column. This is the one place where the implementation departs from a column name the specification stated, so it is recorded here rather than left to a comment in a migration script.

The attribute is specified as a decimal in kilograms with at most three decimal places. SQLite has no decimal type. The two candidate columns were:

- `REAL`, which is IEEE-754 binary floating point. `0.544` has no exact binary representation, so a stored weight could not be guaranteed to come back as the operator entered it, and the API would publish a number such as `0.5440000000000001`.
- `TEXT`, which stores the decimal exactly but makes `weight > 0` and ordering depend on string comparison, and invites `'0.5'` and `'0.50'` to be stored as different values for the same weight.

Three decimal places of a kilogram is exactly one gram, so an integer count of grams represents every permitted value exactly, keeps the `> 0` check and any future ordering arithmetic, and has one representation per weight. The column is named for its unit so that no reader mistakes its contents for kilograms — which is the actual risk an integer column introduces, and the reason the name differs rather than silently redefining `weight`.

Conversion happens in the repository: kilograms in, grams out. The `BigDecimal` returned to the API is rescaled so that a whole number of kilograms serializes as `100` rather than `1E+2`.

## Local files and version control

Use the ignored root directories `data/`, `backups/`, and `logs/` for local runtime files. Use `config/`, `secrets/`, or ignored `.env`/`*.local.properties`/`*.local.yml`/`*.local.yaml` files for deployment settings and credentials. Environment files must be loaded by the deployment tooling; Spring Boot does not automatically read `.env` files.

The ignore rules also cover common SQLite file extensions and their companion files, private key files, and common keystore formats. `.env.example` remains eligible for tracking and must contain placeholders only. Source-controlled application defaults and SQL migration fixtures under `src/` remain eligible for tracking, as does `gradle/wrapper/gradle-wrapper.jar`.
