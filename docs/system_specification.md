# Book DB zz21

## Introduction
This document outlines the system specification for the project. This project is a DB about books with a RESTful API, main purpose being
to integrate with other systems and provide data. Also, it will have a web based GUI where the operator can manage the data. The name
zz21 has been added just to distinguish it from other projects.

## System attributes

The following attributes are supported:

| Attribute         | System attribute   | Data Type | Data Type Constraints      | Description           |
|-------------------|--------------------|-----------|----------------------------|-----------------------|
| Title             | `title`            | String    | Max 50 chars               | The title of the book |
| Author or authors | `authors`          | String    | Max 50 chars               | One or more authors who wrote the book                    |
| Publication date  | `publication_date` | Date      | May be NULL                | The date when the book was published                      |
| Edition           | `edition`          | Integer   | May be NULL, >0            | The edition of the book                                   |
| Language          | `language`         | String    | Max 50 chars               | The language in which the book is written                 |
| Book cover type   | `cover_type`       | String    | May be NULL, Max 30 chars  | The type of book cover                                    |
| Print length      | `print_length`     | Integer   | > 0                        | The number of pages in the book                           |
| ISBN-10           | `isbn_10`          | String    | May be NULL, max. 10 chars | The 10-digit International Standard Book Number           |
| ISBN-13           | `isbn_13`          | String    | May be NULL, max. 13 chars | The 13-digit International Standard Book Number           |
| Book weight       | `weight`           | Decimal   | May be NULL, > 0           | The weight of the book in kilograms                       |
| Dimensions        | `dimensions`       | String    | May be NULL, max. 50 chars | The book dimensions, such as 17.78 x 1.91 x 24.77 cm      |
| ID                | `id`               | String    | Required, unique, immutable; 8–19 chars | Internal identification of the book; see [Book ID format](#book-id-format) |
| Keywords          | `keywords`         | String    | Comma separated string     | Comma-separated keywords that describe the book's content |
| Cover image       | `cover_image`      | Image     | May be NULL                | Image of cover of the book                                |
| Publisher         | `publisher`        | String    | May be NULL                | The publisher of the book                                 |

The system will provide a RESTful API for providing data to other systems. The following two endpoints will be provided:
 - `/books-brief`: Retrieve a list of books, but only basic information:
   * title
   * authors
   * edition
   * id
 - `/books-full`: Retrieve full information about all books.

## Book ID format

The system generates an `id` for each book record when it is created. The ID combines a short, readable fragment of the title with a random suffix:

```text
<title-fragment>-<random-suffix>
```

The complete ID is 8–19 characters long and contains only lowercase ASCII letters, digits, and hyphens.

### Title fragment

Generate the title fragment as follows:

1. Convert the title to lowercase and normalize accented letters to their ASCII equivalents where possible (for example, `é` becomes `e`).
2. Replace each consecutive sequence of characters other than ASCII letters (`a-z`) or digits (`0-9`) with a single hyphen.
3. Remove leading and trailing hyphens, keep at most the first 12 characters, and remove any trailing hyphen left by truncation.
4. Use `book` if the resulting fragment is empty.

The resulting fragment is 1–12 characters long. It helps people recognize the book; it does not establish uniqueness.

### Random suffix and uniqueness

The suffix consists of exactly six independently and randomly selected characters from lowercase ASCII letters (`a-z`) and digits (`0-9`). A single hyphen separates it from the title fragment.

Enforce a unique constraint on the complete ID in the database. If an insertion fails because the generated ID already exists, generate a new suffix and retry the insertion. This must also handle collisions between simultaneous requests.

Each new book record receives its own ID, including records with the same title or different editions. The ID does not determine whether two records describe the same book.

### Stability and use

The ID is immutable after creation. Changes to the title or other book attributes must not regenerate it, because external systems may already reference the existing ID.

Integrations must treat the complete ID as an opaque string and read book information from the corresponding attributes. The title fragment reflects the title at creation time and may no longer match the current title.

Author, publication year, and edition are not included in the ID to keep it compact and avoid depending on information that may be missing or corrected later.

### Examples

The suffixes below are illustrative; actual suffixes are generated randomly.

| Book title | Example ID |
|------------|------------|
| Dune | `dune-7k3m9x` |
| Clean Code | `clean-code-4p8x2n` |
| The Hobbit | `the-hobbit-9w6r2c` |

## Workflows

The system supports two main activities: an operator manages book information through the web GUI, and external systems retrieve that information through the RESTful API.

### Manage authors and publishers

The GUI must provide separate views for managing authors and publishers. Books reference these reusable records so that the operator can select existing authors and publishers when entering a book.

1. The operator opens the authors or publishers view and browses or searches existing records by name.
2. To add a record, the operator selects **Add**, enters a name and any other supported details, and saves it. The name is required and must not contain only whitespace.
3. To edit a record, the operator selects an existing entry, updates its details, and saves the changes.
4. The system validates the input, saves valid changes, and displays a confirmation. The record is then available for selection when adding or editing books.

Editing an author or publisher updates the information displayed for all books linked to that record. Authors with the same name must be distinguishable as separate records; a matching name alone does not establish that they are the same person.

### Add a book

1. The operator opens the books view and selects **Add book**.
2. The operator enters the book details according to the [System attributes](#system-attributes), selects one or more authors, and optionally selects a publisher. Missing author or publisher records can be created through their management views.
3. The operator may provide optional information, including ISBNs and a cover image. Optional fields may be left empty and are stored as `NULL` where the attribute definition allows it.
4. The operator selects **Save**. The system validates all entered values against the attribute constraints and identifies any fields requiring correction.
5. If validation succeeds, the system generates an immutable ID according to the [Book ID format](#book-id-format) and saves the complete record and its author and publisher links.
6. The GUI displays the saved book and its ID. The book becomes available through both API endpoints.

The operator does not enter the book ID manually. A failed save must not leave a partially created book record or incomplete relationships.

### Find and edit a book

1. The operator opens the books view and browses or searches for a book by title, author, ISBN, or system ID.
2. The operator selects a result to view its full details and chooses **Edit** to make changes.
3. The operator updates the book attributes or its author and publisher selections and saves the changes.
4. The system validates and saves the changes, then displays the updated record. Subsequent API requests return the saved information.

The system ID is read-only and remains unchanged even when the title changes. If saving fails, the previously saved record remains unchanged.

### Form behavior and validation

- Required fields must be clearly marked. Required text values must not be empty or contain only whitespace.
- Validation must be enforced by the server, with clear field-specific messages in the GUI.
- If validation or saving fails, the form must retain the operator's input so it can be corrected or retried.
- Selecting **Cancel** discards unsaved changes and leaves stored records unchanged.
- The GUI must distinguish a successful save from a failed save and must not show a success confirmation before persistence completes.

### Retrieve books through the API

External systems retrieve saved book records using the following read-only endpoints:

| Method | Endpoint | Successful response |
|--------|----------|---------------------|
| `GET` | `/books-brief` | A JSON array of books containing only `title`, `authors`, `edition`, and `id`. |
| `GET` | `/books-full` | A JSON array of books containing all attributes defined in [System attributes](#system-attributes). |

1. The external system sends a request to the appropriate endpoint.
2. The system retrieves the saved records and returns HTTP `200 OK` with `Content-Type: application/json`.
3. If no books exist, the response is an empty array (`[]`). Optional attributes without values are represented as JSON `null`.

Both endpoints use the same immutable book IDs, allowing consumers to match brief and full records. They must not modify stored data. If a request cannot be completed because of a server error, the API returns an appropriate HTTP error status and a JSON error message instead of a successful empty list.

### Details to finalize before implementation

- **Author and publisher data model:** The attribute table currently describes `authors` and `publisher` as strings. Define the separate entity schemas, their identifiers, and their relationships to books, and clarify whether the API exposes names, identifiers, or nested objects. Update the attribute table to match that decision.
- **Cover images:** Define supported upload formats and size limits, storage behavior, and how `cover_image` is represented in JSON, such as an image URL.
- **Duplicate books:** Define how possible duplicates are detected and whether they trigger a warning or prevent saving. A unique system ID alone does not prevent duplicate book information.
- **API contract:** Define the error response structure. The endpoints above return all books; pagination, filtering, and sorting require a further contract if introduced.

## Technical details

### Technology stack

The application must use the following technologies:

| Component | Technology | Purpose |
|-----------|------------|---------|
| Language and runtime | Java 25 | Application development using JDK 25 and execution on a compatible Java 25 runtime. |
| Application framework | Spring Boot | Application startup, dependency injection, configuration, REST controllers, and an embedded web server. |
| Web GUI | Vaadin Flow | Browser-based management views implemented in Java and integrated with Spring Boot. |
| Database | SQLite | Embedded relational storage in an external database file. |
| Build system | Gradle Wrapper | Repeatable builds using the Gradle version defined in the repository. |
| Distribution | Executable Spring Boot JAR | A single application artifact containing runtime dependencies and production frontend assets. |

The current build selects Spring Boot `4.1.0`, Vaadin `25.2.5`, and Gradle `9.6.1`. Dependency and plugin versions must remain pinned in the build configuration; upgrades must be checked for compatibility with Java 25 and with each other.

### Application structure

The system is intended for approximately one or two operators and must run as a single application instance. The Vaadin GUI and REST API share the same Spring Boot process and database.

Separate the implementation into the following responsibilities:

- **Presentation:** Vaadin views implement the operator workflows; REST controllers expose `/books-brief` and `/books-full` to external consumers.
- **Application services:** Shared services enforce validation, book ID generation, and transaction boundaries. Both the GUI and API use these services where applicable.
- **Persistence:** Repositories read and write books, authors, publishers, and their relationships in SQLite.
- **API models:** Explicit response objects define the JSON contract independently of the database schema and internal entities.

Vaadin views may call application services directly. Additional public write endpoints are not required by the current workflows. Blocking database access is sufficient for the expected workload.

### Database and persistence

SQLite is suitable for the expected small workload and requires no separate database server. The application must access it through a SQLite JDBC driver. Spring JDBC with the Xerial SQLite JDBC driver is the recommended starting point; the exact persistence dependencies remain to be selected.

- Store the database outside the JAR, on a local persistent filesystem in a directory writable by the application. The application also needs permission to create SQLite journal and temporary companion files in that directory.
- Store books, authors, and publishers as separate records. Use a join table for the relationship between books and authors, and a nullable publisher reference on each book. Detailed entity fields and API representations remain subject to the decisions listed in [Details to finalize before implementation](#details-to-finalize-before-implementation).
- Enforce the book ID's uniqueness in the database. Apply appropriate foreign keys, non-null constraints, and checks for the attribute limits in addition to service-level validation. Enable SQLite foreign-key enforcement on every database connection.
- Save a book and its relationship changes in a single transaction. Keep write transactions short and never hold a transaction open while the operator is filling in a form.
- Configure write-ahead logging (WAL) to allow reads while a write is in progress. SQLite still permits only one writer at a time; configure a bounded busy timeout and return a clear failure if a write cannot acquire the required lock.
- Use parameterized SQL for values supplied by users or API clients.

The database must be managed through versioned migrations. On first startup, the application creates the schema in the configured database file. Later startups apply pending migrations while preserving existing data. Normal startup must never drop and recreate the schema. If migration fails, startup must fail with a useful diagnostic rather than serve requests against a partially initialized schema.

### Build and packaging

The release artifact must be an executable Spring Boot JAR produced by Gradle's `bootJar` task. It must include application classes, runtime dependencies, the embedded web server, and Vaadin's compiled production frontend assets.

The production build must enable Vaadin production mode. The intended build command is:

```bash
./gradlew clean build -Pvaadin.productionMode=true
```

The executable artifact is produced under `build/libs/`. Release verification must confirm that production frontend assets are included and development-only dependencies are excluded. If the build also produces a plain JAR, distribute the executable Spring Boot JAR.

Building may require Node.js and frontend tooling managed by the Vaadin Gradle plugin. Running the packaged application must not require Node.js, Gradle, or a separate servlet container. A compatible Java 25 runtime is required on the deployment machine and is not included in the JAR.

The database, backups, external configuration, and any separately stored cover-image files must remain outside the JAR so that replacing the application artifact does not replace user data.

### Runtime configuration

Use Spring Boot's external configuration support so deployment settings can be supplied through a configuration file, environment variables, or command-line arguments.

The following settings define the initial configuration contract. The custom `bookdb.*` properties must be implemented as part of the persistence setup; they are not provided by Spring Boot itself.

| Property | Initial default | Meaning |
|----------|-----------------|---------|
| `server.port` | `8080` | HTTP port used by the GUI and REST API. |
| `bookdb.database.path` | `./data/books.db` | Path to the external SQLite database file. Relative paths are resolved from the process working directory. |
| `bookdb.database.busy-timeout-ms` | `5000` | Maximum time a database connection waits for a lock before reporting that the database is busy. |

For example, after implementing these settings and naming the release artifact `book-db-zz21.jar`, the application can be started with:

```bash
java -jar book-db-zz21.jar --server.port=8080 --bookdb.database.path=/var/lib/book-db-zz21/books.db
```

Create the data directory if it does not exist and permissions allow it. If the configured path cannot be used, fail startup with a clear error; do not silently switch to another database. Log the resolved database path at startup. Production deployments should use an explicit absolute path so a change of working directory cannot accidentally select a new database.

### Operations and data durability

- Run one application process against the database on the same host. Sharing the WAL database over a network filesystem or running multiple application instances is outside the initial deployment model.
- Log startup, shutdown, migration results, and application errors using Spring Boot's logging facilities. API error responses must not expose stack traces or database internals.
- On shutdown, stop accepting new work and close database connections cleanly. After restarting, previously saved records and their IDs must remain available.
- Back up persistent data independently of the JAR. For a simple offline backup, stop the application cleanly, close all database connections, and copy the database. For a live backup, use SQLite's backup facilities; copying only the main database file while WAL writes are active is not a reliable backup.
- Include separately stored cover images in the backup if that storage approach is selected. Document and verify the restore procedure, and take a backup before applying schema upgrades to an existing deployment.

### Verification and remaining decisions

Implementation verification must cover validation and book ID generation, repository operations against a real SQLite database, migrations, transaction rollback, and the JSON contracts of both API endpoints. A release smoke test must start the production JAR, open the GUI, create and edit a book, retrieve it through both endpoints, and confirm that its data and ID survive an application restart.

Before deployment, finalize the persistence and migration libraries, cover-image storage, operator authentication, API access policy, and the hosting arrangement, including HTTPS if accessed over a network. These choices must be documented alongside the unresolved data-model and API details in the Workflows section.
