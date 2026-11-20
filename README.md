# book-db-zz21

A database of books with a read-only REST API for other systems to integrate with, and a web GUI
for an operator to manage the records. The name `zz21` only distinguishes it from other projects.

One operator works with the catalogue at a time; API clients may read while they do. The whole
application is a single executable JAR with an embedded web server, storing everything — including
cover images — in one SQLite file outside the JAR.

- [System specification](docs/system_specification.md) — what it does, and the exact JSON contract
- [Operating instructions](docs/operating_instructions.md) — install, configure, run, back up, restore, upgrade
- [Implementation decisions](docs/implementation_decisions.md) — why the build and deployment are arranged this way
- [Implementation tasks](docs/tasks.md) — the staged plan and the evidence for each stage

## Prerequisites

| | Building | Running |
|---|---|---|
| Java | JDK 25 | Java 25 runtime |
| Build tool | the tracked Gradle Wrapper (`./gradlew`) | — |
| Node.js | downloaded automatically by the Vaadin plugin | **not required** |
| Database server | — | **none**: SQLite is embedded |
| Servlet container | — | **none**: the server is embedded |

Nothing else needs installing. Do not install Gradle itself; the wrapper pins the version.

## Running locally

The application refuses to start without credentials, so a development run needs them just as a
deployment does. Provision them once into `config/application.properties` at the repository root —
Spring Boot reads that path by default, so no IDE run configuration, profile or environment variable
is involved, and `/config/` is git-ignored so the file cannot be committed.

```bash
./gradlew clean build -Pvaadin.productionMode=true
java -jar build/libs/book-db-zz21.jar provision-credentials operator
```

Copy the three property lines it prints into `config/application.properties`, keep the API token it
shows once, and add a development database path:

```properties
bookdb.security.operator-username=operator
bookdb.security.operator-password-hash=<from the tool>
bookdb.security.api-token-digest=<from the tool>
bookdb.database.path=./data/books.db
```

Then start it however you prefer:

```bash
./gradlew bootRun
```

The GUI is at <http://localhost:8080> and asks for the operator account. The API needs the token:

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/books-brief
```

See [Running locally from an IDE](docs/implementation_decisions.md#running-locally-from-an-ide) for
the detail, including why that file is picked up and the one place it can surprise you.

## Tests

```bash
./gradlew check
```

This runs the whole suite and then `verifyTestsExecuted`, which fails the build if no tests ran at
all — Gradle reports `test NO-SOURCE` when it discovers nothing, and that reads like success. A
passing run prints the count, currently:

```
Verified 588 executed tests across 38 test classes.
```

Tests never read `config/` or any other file-based configuration, and never open a database outside
the temporary directory. Both are enforced rather than conventional; see
[Keeping tests away from real data](docs/implementation_decisions.md#keeping-tests-away-from-real-data).

## Production build

```bash
./gradlew clean build -Pvaadin.productionMode=true
```

The release artifact is **`build/libs/book-db-zz21.jar`** — the only JAR the build produces, so
there is nothing to choose between. It contains the application, its runtime dependencies, the
embedded server and Vaadin's compiled production frontend. The version stays visible in the
manifest's `Implementation-Version` rather than in the filename.

Copy that one file to wherever it should run, and start it with the arguments for that machine:

```bash
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --server.port=8080 \
  --bookdb.database.path=/home/dima/tmp/data/books.db
```

The database, its backups and the credentials stay outside the JAR, so replacing the artifact never
replaces data. Full procedures are in the
[operating instructions](docs/operating_instructions.md).
