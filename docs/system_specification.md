# Book DB zz21

## Introduction
This document outlines the system specification for the project. This project is a DB about books with a RESTful API, main purpose being
to integrate with other systems and provide data. Also, it will have a web based GUI where the operator can manage the data. The name
zz21 has been added just to distinguish it from other projects.

## System attributes

Database attributes (SQL column names) must use lowercase words separated by underscores (`snake_case`), such as `publication_date`, `cover_type`, and `isbn_13`. Multiword table names follow the same convention. JSON attributes must use lowercase words separated by hyphens (`kebab-case`), such as `publication-date`, `cover-type`, and `isbn-13`. API response models explicitly map database storage to JSON keys.

The table below distinguishes database storage from the public JSON contract. Database columns belong to the books table unless a relationship is specified.

| Attribute | Database column or relationship | JSON key | Logical data type | Constraints and meaning |
|-----------|---------------------------------|----------|-------------------|-------------------------|
| Title | `title` | `title` | String | Required, nonblank, max. 50 characters. |
| Author or authors | `book_authors` join table: `book_id`, `author_id` | `authors` | Set of author references | Required, at least one distinct author. Each author name is nonblank and max. 50 characters. The API returns an array of names; author order has no meaning and is not preserved. |
| Publication date | `publication_date` | `publication-date` | Date | Optional; a valid calendar date, returned as `YYYY-MM-DD`, or `null`. |
| Edition | `edition` | `edition` | Integer | Optional; greater than zero, or `null`. |
| Language | `language` | `language` | String | Required, nonblank, max. 50 characters. A language name, such as `English`. |
| Book cover type | `cover_type` | `cover-type` | String | Optional, max. 30 characters, such as `Paperback`; otherwise `null`. |
| Print length | `print_length` | `print-length` | Integer | Required; greater than zero. Number of pages. |
| ISBN-10 | `isbn_10` | `isbn-10` | String | Optional; exactly 10 characters after normalization, valid ISBN-10 checksum, unique book identity; otherwise `null`. See [ISBN validation](#isbn-validation). |
| ISBN-13 | `isbn_13` | `isbn-13` | String | Optional; exactly 13 digits after normalization, valid ISBN-13 prefix and checksum, unique book identity; otherwise `null`. See [ISBN validation](#isbn-validation). |
| Book weight | `weight` | `weight` | Decimal | Optional; greater than zero, at most three decimal places, in kilograms; otherwise `null`. Returned as a JSON number. |
| Dimensions | `dimensions` | `dimensions` | String | Optional, max. 50 characters, such as `17.78 x 1.91 x 24.77 cm`; otherwise `null`. |
| ID | `id` | `id` | String | Required, unique, immutable, system-generated; 8–19 characters. See [Book ID format](#book-id-format). |
| Keywords | `keywords` | `keywords` | String | Optional comma-separated keywords, max. 500 characters; otherwise `null`. |
| Cover image | `cover_image`, `cover_image_content_type` | `cover-image` | Binary image and media type | Optional; image bytes stored as a SQLite BLOB. The API returns an object containing `content-type` and Base64 `data`, or `null`. See [Cover images](#cover-images). |
| Publisher | `publisher_id` reference | `publisher` | Publisher reference | Optional; the API returns the publisher name, or `null`. Publisher names are nonblank and max. 200 characters. |

The system will provide a RESTful API for providing data to other systems. The following two endpoints will be provided:
 - `/books-brief`: Retrieve a list of books, but only basic information:
   * title
   * authors
   * edition
   * id
 - `/books-full`: Retrieve full information about all books.

### Authors and publishers

Authors and publishers are reusable records, with the following initial schemas:

| Entity | Fields | Identity and relationships |
|--------|--------|----------------------------|
| Author | System-generated positive integer `id`; required `name`, max. 50 characters. | The normalized name is unique. Books reference authors through a join table. |
| Publisher | System-generated positive integer `id`; required `name`, max. 200 characters. | The normalized name is unique. A book has at most one publisher reference. |

Author and publisher IDs are immutable internal identifiers; the book-list APIs expose their names. No additional author or publisher details are required for the initial implementation.

Within this system, authors with the same name are the same author. For example, all books by `Gilbert Strang` refer to one author record named `Gilbert Strang`; the same applies to `Walter Rudin`. Publishers also have one record per normalized name.

For name uniqueness and the metadata comparisons below, normalize text to Unicode NFC, trim leading and trailing whitespace, collapse consecutive whitespace to one space, and compare after lowercasing with a locale-independent rule. Preserve capitalization in display values. Accents and punctuation remain significant. Thus ` Gilbert  Strang ` and `gilbert strang` identify the same author.

A book's authors form a set: the same author cannot be linked twice, and neither storage nor API consumers may rely on author order. Adding an existing author or publisher name must direct the operator to the existing record. Renaming a record to another record's normalized name is rejected; it does not silently merge records.

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

Each accepted new book record receives its own ID. Different editions, or other distinct books sharing a title, receive separate IDs only when they satisfy the [duplicate-book rules](#duplicate-books). A new random ID must never be used to bypass duplicate detection.

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
2. To add a record, the operator selects **Add**, enters a name, and saves it. The system validates the name and rejects an existing normalized name with a message identifying the existing record.
3. To edit a record, the operator selects an existing entry, updates its details, and saves the changes.
4. The system validates the input, saves valid changes, and displays a confirmation. The record is then available for selection when adding or editing books.

Editing an author or publisher updates the information displayed for all books linked to that record. Same-name authors are represented by one reusable record according to [Authors and publishers](#authors-and-publishers). Unreferenced records can be deleted according to [Delete records](#delete-records).

### Add a book

1. The operator opens the books view and selects **Add book**.
2. The operator enters the book details according to the [System attributes](#system-attributes), selects one or more authors, and optionally selects a publisher. Missing author or publisher records can be created through their management views.
3. The operator may provide optional information, including ISBNs and a cover image. Optional fields may be left empty and are stored as `NULL` where the attribute definition allows it.
4. The operator selects **Save**. The system validates all entered values against the attribute constraints, checks for duplicate books and ISBNs, and identifies any fields requiring correction.
5. If validation succeeds, the system generates an immutable ID according to the [Book ID format](#book-id-format) and saves the complete record and its author and publisher links.
6. The GUI displays the saved book and its ID. The book becomes available through both API endpoints.

The operator does not enter the book ID manually. A failed save must not leave a partially created book record, incomplete relationships, or an orphaned cover image.

### Find and edit a book

1. The operator opens the books view and browses or searches for a book by title, author, ISBN, or system ID.
2. The operator selects a result to view its full details and chooses **Edit** to make changes.
3. The operator updates the book attributes or its author and publisher selections and saves the changes.
4. The system validates and saves the changes, then displays the updated record. Subsequent API requests return the saved information.

The system ID is read-only and remains unchanged even when the title changes. If saving fails, the previously saved record remains unchanged.

Title and author searches use partial matching with the text normalization defined above. ISBN searches use the normalized ISBN, including its equivalent ISBN-10 or ISBN-13 form, and system-ID searches use an exact match. A single search field may match any of these attributes.

Only one operator works with the database at a time. Simultaneous editing by multiple operators, conflict-resolution workflows, and edit locks are outside the initial scope. API consumers may continue reading while the operator makes changes; transaction and integrity requirements still apply.

### Delete records

1. The operator selects a book, author, or publisher and chooses **Delete**.
2. The GUI asks for confirmation and identifies the record being deleted. Cancelling leaves all records unchanged.
3. The server validates whether deletion is allowed. A referenced author or publisher cannot be deleted; the GUI explains that linked books must first be edited or deleted.
4. If validation succeeds, the system performs the deletion in one transaction and displays success only after persistence completes. If it fails, all existing data remains unchanged and the GUI displays an error.

Deleting a book also deletes its author links and its stored cover image. It does not delete its authors or publisher. An unreferenced author or publisher may be deleted. Deletions are permanent; restoring deleted data requires a backup. Deleted books no longer appear in either API response.

### Form behavior and validation

- Required fields must be clearly marked. Required text values must not be empty or contain only whitespace.
- Normalize Unicode and whitespace as described above before checking text lengths and saving values. Preserve display capitalization. Empty optional fields are stored as `NULL`; zero is not a substitute for an absent numeric value.
- Validate calendar dates, whole-number fields, and positive decimal weights on the server. Reject excess weight precision rather than silently rounding it.
- Trim individual keywords, discard empty items, remove case-insensitive duplicates, and store the remaining values separated by a comma and a space. An empty result is `NULL`.
- Author and publisher selections must reference existing records. At least one distinct author must remain after editing a book.
- Validation must be enforced by the server, with clear field-specific messages in the GUI.
- If validation or saving fails, the form must retain the operator's input so it can be corrected or retried.
- Selecting **Cancel** discards unsaved changes and leaves stored records unchanged.
- The GUI must distinguish a successful save from a failed save and must not show a success confirmation before persistence completes.

### ISBN validation

- Both ISBN fields are optional, but every supplied ISBN must be valid. Remove whitespace and hyphens before validation and storage; convert a final ISBN-10 `x` to uppercase `X`. Preserve leading zeros by storing ISBNs as strings.
- ISBN-10 consists of nine digits followed by a digit or `X`, and must pass the standard modulo-11 checksum. ISBN-13 consists of 13 digits, begins with `978` or `979`, and must pass the standard alternating-weight modulo-10 checksum.
- If both ISBN forms are supplied, they must identify the same edition. Convert ISBN-10 to its `978` ISBN-13 equivalent and compare it with the supplied ISBN-13. A `979` ISBN-13 has no ISBN-10 equivalent.
- An ISBN cannot belong to more than one book. Enforce uniqueness of supplied ISBNs and of a canonical ISBN-13 identity derived from either field, so entering an ISBN-10 cannot bypass an existing equivalent ISBN-13, or vice versa. Multiple books may have `NULL` ISBNs.
- Apply these checks when adding and editing, excluding the current record from duplicate comparisons. Keep an omitted API ISBN field `null` even if its equivalent can be derived internally.

### Duplicate books

Duplicate books are rejected, rather than merely producing a warning. A proposed book is a duplicate if either of the following matches another stored book:

1. Its canonical ISBN identity, as defined in [ISBN validation](#isbn-validation).
2. Its normalized title, unordered set of author IDs, edition, normalized language, and publisher ID all match. For this comparison, two absent editions are equal and two absent publishers are equal. This metadata rule applies whether or not ISBNs are supplied and prevents duplicate records without ISBNs or with different ISBN entries.

The metadata combination defines the initial catalog's notion of a distinct book. A different edition, language, or publisher can identify a separate record, provided its ISBN is not already assigned. A different cover image, weight, page count, keywords, or randomly generated system ID does not by itself establish a distinct book.

Check for duplicates on both creation and editing, identify the existing book in the GUI error, and leave saved data unchanged after rejection. Back the ISBN checks and a stored canonical metadata key with database unique constraints. Canonical metadata must encode the author set without depending on selection order and must handle absent values explicitly; it is updated atomically with the book and its relationships.

### Cover images

Cover images are stored inside the SQLite database as binary BLOBs, with their validated media type. Persistent cover-image files outside the database are not used.

- Accept JPEG (`image/jpeg`) and PNG (`image/png`) uploads, with a maximum original file size of 5 MiB (5,242,880 bytes).
- Validate the actual image content and confirm it can be decoded; a filename extension or client-supplied content type alone is insufficient. Reject empty, corrupt, unsupported, or oversized uploads with a field-specific error.
- The operator can preview an image, replace it, or explicitly remove it. Editing other fields without supplying a new image retains the existing image.
- Save or replace the image bytes and media type in the same transaction as the book. Removing a cover clears both values. Cancelling or failing a save leaves the previous stored image unchanged.
- Store the validated uploaded bytes without Base64 encoding in the database. Base64 encoding is used only when serializing the image into JSON. A database backup includes the images.

### Retrieve books through the API

External systems retrieve saved book records using the following read-only endpoints:

| Method | Endpoint | Successful response |
|--------|----------|---------------------|
| `GET` | `/books-brief` | A JSON array of books containing only `title`, `authors`, `edition`, and `id`. |
| `GET` | `/books-full` | A JSON array of books containing all attributes defined in [System attributes](#system-attributes). |

1. The external system sends a request to the appropriate endpoint.
2. The system retrieves the saved records and returns HTTP `200 OK` with `Content-Type: application/json`.
3. If no books exist, the response is an empty array (`[]`). Optional attributes without values are represented as JSON `null`.

Both endpoints use the same immutable book IDs, allowing consumers to match brief and full records. They must not modify stored data. Book order and author order are unspecified. The initial endpoints return all books; API pagination, filtering, and sorting are outside the initial contract.

### JSON representations

All JSON object keys, including nested objects and error responses, follow the lowercase, hyphen-separated convention. Database column names retain underscores; for example, `publication_date` maps to `publication-date` and `isbn_13` maps to `isbn-13`. Dates use `YYYY-MM-DD` without a time or time zone; integer and decimal attributes are JSON numbers; ISBNs and IDs are strings. Optional keys are included with `null` when unset.

`authors` is an array of unique author display names. `publisher` is a display name or `null`. Internal author and publisher IDs are not included. `keywords` is a comma-separated string or `null`.

Example `/books-brief` response:

```json
[
  {
    "title": "Dune",
    "authors": ["Frank Herbert"],
    "edition": null,
    "id": "dune-7k3m9x"
  }
]
```

Example `/books-full` response:

```json
[
  {
    "title": "Dune",
    "authors": ["Frank Herbert"],
    "publication-date": null,
    "edition": null,
    "language": "English",
    "cover-type": "Paperback",
    "print-length": 896,
    "isbn-10": "0441172717",
    "isbn-13": "9780441172719",
    "weight": null,
    "dimensions": null,
    "id": "dune-7k3m9x",
    "keywords": "science fiction",
    "cover-image": null,
    "publisher": "Ace"
  }
]
```

When a cover exists, `cover-image` is an object with exactly two keys: `content-type`, containing `image/jpeg` or `image/png`, and `data`, containing the complete image bytes encoded as standard Base64 with padding and without line breaks or a data-URL prefix. The brief endpoint never includes image data.

If an API request fails, return a JSON error object with exactly `error-code` and `message`. Use HTTP `401` with `unauthorized` when the request carries no valid API token, HTTP `500` with `internal-error` for unexpected failures, or HTTP `503` with `database-busy` when database lock waiting exceeds the configured timeout. An unauthenticated API request must never be answered with a redirect or with the GUI's HTML login page, because a consumer following a redirect would receive HTML with a success status and could not distinguish it from data. Messages must be suitable for consumers and must not expose stack traces, SQL, or database paths. For example:

```json
{
  "error-code": "database-busy",
  "message": "The database is busy. Please retry later."
}
```

An error must never be returned as HTTP `200` with an empty list.

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

The system must run as a single application instance, with only one operator working with the database at a time. The Vaadin GUI and REST API share the same Spring Boot process and database. External systems may read through the API while the operator works.

Separate the implementation into the following responsibilities:

- **Presentation:** Vaadin views implement the operator workflows; REST controllers expose `/books-brief` and `/books-full` to external consumers.
- **Application services:** Shared services enforce validation, book ID generation, and transaction boundaries. Both the GUI and API use these services where applicable.
- **Persistence:** Repositories read and write books, authors, publishers, and their relationships in SQLite.
- **API models:** Explicit response objects define the JSON contract independently of the database schema and internal entities.

Vaadin views may call application services directly. Additional public write endpoints are not required by the current workflows. Blocking database access is sufficient for the expected workload.

### Database and persistence

SQLite is suitable for the expected small workload and requires no separate database server. Use Spring JDBC `7.0.8`, Xerial SQLite JDBC `3.53.2.0`, and Flyway `12.4.0` for versioned SQL migrations. These versions are pinned through the Spring Boot `4.1.0` dependency BOM. Selection rationale and compatibility checks are recorded in [Implementation decisions](implementation_decisions.md#dependency-selection).

- Store the database outside the JAR, on a local persistent filesystem in a directory writable by the application. The application also needs permission to create SQLite journal and temporary companion files in that directory.
- Use lowercase, underscore-separated SQL column names throughout the schema, including foreign keys and internal validation columns. Apply the database-to-JSON mappings defined in [System attributes](#system-attributes) when creating API response objects.
- Store books, authors, and publishers as separate records using the fields and relationships defined in [System attributes](#system-attributes). Use the `book_authors` join table with a unique (`book_id`, `author_id`) pair and no author-order column, and a nullable `publisher_id` reference on each book.
- Store each cover's bytes in the `cover_image` BLOB column and its media type in `cover_image_content_type`. Both are present or both are `NULL`. Map these columns to the JSON `cover-image` object's `data` and `content-type` keys respectively. Cover changes participate in the book's transaction.
- Enforce unique book IDs, normalized author and publisher names, supplied ISBNs, canonical ISBN identities, and canonical book metadata keys in the database. Apply appropriate foreign keys, non-null constraints, and checks for the attribute limits in addition to service-level validation. Enable SQLite foreign-key enforcement on every database connection.
- Use restrictive foreign-key deletion rules for referenced authors and publishers; deleting them must not delete books or silently clear their relationships. Deleting a book removes its author links and cover data in the same transaction.
- Save a book, its duplicate-detection keys, cover image, and relationship changes in a single transaction. Keep write transactions short and never hold a transaction open while the operator is filling in a form.
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

The database, including all cover-image BLOBs, backups, and external configuration must remain outside the JAR so that replacing the application artifact does not replace user data.

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
- Cover images are included in the database backup. Document and verify the restore procedure for book records, relationships, and image BLOBs, and take a backup before applying schema upgrades to an existing deployment.

### Verification and remaining decisions

Implementation verification must cover:

- Attribute validation, ISBN checksums and equivalent ISBN forms, and immutable book ID generation.
- Same-name author/publisher rejection, unordered author relationships, and duplicate-book rejection with and without ISBNs on both creation and editing.
- Repository operations against a real SQLite database, migrations, database constraints, and complete transaction rollback, including cover images and duplicate-detection keys.
- Valid and invalid cover uploads, image replacement/removal, and exact recovery of stored image bytes through the full API's Base64 representation.
- Allowed book deletion, rejected deletion of referenced authors/publishers, and allowed deletion of unreferenced records.
- The exact JSON keys, value types, nulls, empty arrays, and error responses of both API endpoints.

A release smoke test must start the production JAR, open the GUI, create and edit a book with a cover image, retrieve it through both endpoints, and confirm that its data, ID, relationships, and image survive an application restart. It must also verify deletion behavior and restoration from a database backup.

The initial deployment plan uses one private Linux host and exposes the embedded Spring Boot web server directly from the executable JAR. It also uses an operator GUI login and a separate bearer token for API reads. When HTTPS is required, TLS is configured directly in Spring Boot using certificate material stored outside the JAR and repository. Credential provisioning, external storage, and access boundaries are defined in [Implementation decisions](implementation_decisions.md#initial-deployment-and-access-policy). Implement and verify these controls in stage 14 before network deployment; selecting the policy in stage 1 does not enable it in the starter application.
