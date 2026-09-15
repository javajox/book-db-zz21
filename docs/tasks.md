# Book DB zz21 implementation tasks

This checklist describes the remaining work required to implement and deliver the project defined in [system_specification.md](system_specification.md). Execute the numbered stages in order and complete each stage's verification before relying on it in later stages. The specification is authoritative; update this checklist when requirements change.

This document tracks implementation progress. Checked tasks have been completed and verified; unchecked tasks remain to be performed.

The existing foundation includes the Java 25 toolchain, pinned Spring Boot 4.1.0 and Vaadin 25.2.5 versions, Gradle Wrapper 9.6.1 with its JAR tracked, JUnit Platform configuration, a Spring Boot entry point, and a placeholder Vaadin view. Extend this foundation rather than recreating it.

## 1. Confirm the baseline and resolve implementation dependencies

Specification: [Technology stack](system_specification.md#technology-stack), [Application structure](system_specification.md#application-structure), [Verification and remaining decisions](system_specification.md#verification-and-remaining-decisions).

- [x] **1.1** Verify the current project builds from a fresh checkout with the committed Gradle Wrapper and Java 25. Preserve the pinned framework and plugin versions unless a documented compatibility issue requires a change.
- [x] **1.2** Select compatible, pinned Spring JDBC and Xerial SQLite JDBC dependencies and a migration library with verified SQLite support. Confirm that the migration library can initialize a new database, upgrade an existing database, and stop startup on migration failure.
- [x] **1.3** Add the selected persistence and migration dependencies and any required validation or testing dependencies to Gradle. Keep development-only dependencies separate from the production runtime.
- [x] **1.4** Decide and document the initial hosting arrangement, GUI operator authentication, and API access policy. Specify how any required credentials are provisioned and how HTTPS is provided when accessed over a network. Implement the chosen policy in stage 14.
- [x] **1.5** Record the deployment assumptions: one application process, one operator working at a time, local persistent SQLite storage, and API readers allowed during operator activity. Keep simultaneous-operator editing, edit locks, public write APIs, and API pagination/filtering/sorting outside the initial scope.
- [x] **1.6** Exclude generated databases, WAL/journal/companion files, backups, logs, and local deployment credentials from Git before creating local application data. Keep the Gradle Wrapper JAR tracked.

Completion criterion: the build foundation works, persistence tooling is selected, and the access/deployment decisions needed for implementation are documented.

Stage 1 evidence: [Implementation decisions](implementation_decisions.md) records the selected versions, migration checks, and initial deployment policy. The baseline and updated production builds passed in clean directories on Java 25; all three migration compatibility tests passed, the production JAR excluded development/test artifacts, and packaged GUI startup/shutdown passed. Ignore rules were checked for local data, configuration, the wrapper JAR, and source fixtures. Production persistence wiring and access controls remain in their later stages.

## 2. Establish application structure and test infrastructure

Specification: [Application structure](system_specification.md#application-structure), [System attributes](system_specification.md#system-attributes).

- [ ] **2.1** Organize the application into domain models, application services, persistence repositories, API response models/controllers, Vaadin views, and runtime configuration. Keep SQL and database access out of views and controllers.
- [ ] **2.2** Define book, author, publisher, and cover-image models and create/update inputs. Represent authors as a set of references, the publisher as an optional reference, and cover replacement/removal as explicit operations. Do not allow create/update inputs to set or change the book ID.
- [ ] **2.3** Define shared application errors for field validation, duplicate records, missing references, prohibited deletion, database busy conditions, and unexpected persistence failures. Allow the GUI to identify the affected field and, when appropriate, the conflicting record.
- [ ] **2.4** Prepare JUnit Jupiter fixtures for valid books, authors, publishers, ISBN pairs, and image uploads. Use isolated temporary SQLite files for persistence tests so foreign keys, WAL, migrations, and file durability are exercised against real SQLite.
- [ ] **2.5** Establish cleanup of test databases, connections, and temporary files. Ensure tests never use an operator's configured database. Confirm Gradle actually discovers and executes the tests added in subsequent stages.

Completion criterion: the application has clear component boundaries and repeatable tests can use a real, isolated SQLite database.

## 3. Implement text normalization and attribute validation

Specification: [System attributes](system_specification.md#system-attributes), [Authors and publishers](system_specification.md#authors-and-publishers), [Form behavior and validation](system_specification.md#form-behavior-and-validation).

- [ ] **3.1** Implement Unicode NFC normalization, leading/trailing whitespace removal, and collapsing consecutive whitespace to a single space. Preserve display capitalization and retain accents and punctuation.
- [ ] **3.2** Implement locale-independent lowercase comparison values for name uniqueness, searching, and duplicate-book metadata. Use the same comparison rules throughout services and persistence.
- [ ] **3.3** Validate required title and language values as nonblank strings of at most 50 characters. Validate author names as nonblank strings of at most 50 characters and publisher names as nonblank strings of at most 200 characters.
- [ ] **3.4** Validate at least one distinct author, a required positive integer print length, an optional positive integer edition, and an optional valid calendar publication date.
- [ ] **3.5** Validate optional cover type and dimensions with limits of 30 and 50 characters respectively. Convert empty optional input to `NULL`; do not substitute zero for missing numeric values.
- [ ] **3.6** Validate optional positive weight in kilograms with at most three decimal places. Reject excess precision without silently rounding.
- [ ] **3.7** Normalize optional keywords by trimming items, removing empty items and case-insensitive duplicates, and joining remaining items with a comma and a space. Enforce the 500-character limit on the stored value and use `NULL` for an empty result.
- [ ] **3.8** Add boundary and invalid-input tests for every attribute rule, including whitespace-only required values, normalization before length checks, numeric zero/negative/fractional cases, excess weight precision, invalid dates, and optional empty values.

Completion criterion: shared server-side validation produces consistent stored values and field-specific errors independently of the GUI.

## 4. Implement ISBN, duplicate identity, and book ID rules

Specification: [ISBN validation](system_specification.md#isbn-validation), [Duplicate books](system_specification.md#duplicate-books), [Book ID format](system_specification.md#book-id-format).

- [ ] **4.1** Normalize ISBN input by removing whitespace and hyphens and uppercasing a final ISBN-10 `x`. Preserve leading zeros and keep ISBN values as strings.
- [ ] **4.2** Validate ISBN-10 length, permitted characters, and modulo-11 checksum. Validate ISBN-13 length, digits, `978`/`979` prefix, and alternating-weight modulo-10 checksum.
- [ ] **4.3** Derive a canonical ISBN-13 identity from either supplied ISBN form. Require supplied ISBN-10 and ISBN-13 values to be equivalent; handle `979` ISBN-13 values as having no ISBN-10 equivalent. Retain `NULL` for an omitted public ISBN attribute even when an equivalent can be derived internally.
- [ ] **4.4** Define an unambiguous canonical metadata key containing normalized title, the unordered set of author IDs, edition, normalized language, and publisher ID. Sort author IDs only for canonical encoding, represent absent values explicitly, and prevent ambiguous combinations caused by separators in text.
- [ ] **4.5** Implement duplicate comparisons using either canonical ISBN identity or canonical metadata. Treat two absent editions and two absent publishers as equal. Apply the metadata rule with or without ISBNs and exclude the current book when validating an update.
- [ ] **4.6** Implement the title fragment for book IDs: lowercase, normalize accented letters to ASCII where possible, replace runs of non-ASCII-letter/digit characters with a hyphen, trim boundary hyphens, truncate to 12 characters, remove any new trailing hyphen, and fall back to `book` when empty.
- [ ] **4.7** Generate exactly six independently random lowercase ASCII letters or digits and append them with one hyphen. Produce complete IDs of 8–19 characters and keep suffix generation replaceable in tests so collisions can be reproduced reliably.
- [ ] **4.8** Test valid/invalid ISBNs, leading zeros, `X`, equivalent ISBN pairs, mismatched pairs, and `979` values. Test metadata equality for reordered authors and absent values, and inequality for distinct editions, languages, or publishers.
- [ ] **4.9** Test book-ID character rules, short/long/accented titles, punctuation, non-Latin titles, truncation boundaries, fallback behavior, and suffix length. Reserve database collision retry and ID immutability verification for the transactional services in stage 10.

Completion criterion: ISBN identities, duplicate metadata, and generated book IDs have deterministic validation and tested behavior.

## 5. Configure SQLite startup and external settings

Specification: [Database and persistence](system_specification.md#database-and-persistence), [Runtime configuration](system_specification.md#runtime-configuration).

- [ ] **5.1** Implement and validate configuration binding for `bookdb.database.path`, defaulting to `./data/books.db`, and `bookdb.database.busy-timeout-ms`, defaulting to `5000`. Preserve Spring Boot's `server.port` support and default of `8080`.
- [ ] **5.2** Support configuration files, environment variables, and command-line overrides. Define and document the valid timeout range; reject invalid values instead of silently replacing them.
- [ ] **5.3** Resolve relative database paths against the process working directory and log the resolved path. Create the parent data directory when absent and permissions allow it.
- [ ] **5.4** Fail startup clearly for an unusable path, missing write permissions, or inability to create SQLite journal/companion files. Never silently switch to a different database.
- [ ] **5.5** Configure the JDBC data source and transaction manager. Enable SQLite foreign keys on every connection, including newly created or replacement connections, and apply the configured bounded busy timeout.
- [ ] **5.6** Enable and verify WAL for the database. Keep the database on a local persistent filesystem and arrange clean connection closure on shutdown.
- [ ] **5.7** Integrate migration execution into startup before the application serves requests. Ensure initialization or migration failures stop application startup with useful diagnostics.
- [ ] **5.8** Test default and overridden settings, relative/absolute paths, directory creation, invalid paths, per-connection foreign keys, busy timeout, WAL mode, and resource cleanup using temporary database files.

Completion criterion: startup opens exactly the configured SQLite file with the required connection behavior and fails clearly when initialization cannot succeed.

## 6. Create versioned schema migrations

Specification: [System attributes](system_specification.md#system-attributes), [Authors and publishers](system_specification.md#authors-and-publishers), [Database and persistence](system_specification.md#database-and-persistence).

- [ ] **6.1** Create the initial versioned migration for books, authors, publishers, and `book_authors`. Use lowercase, underscore-separated SQL names for all multiword tables and columns.
- [ ] **6.2** Store books with their immutable string ID and all specified scalar fields: `title`, `publication_date`, `edition`, `language`, `cover_type`, `print_length`, `isbn_10`, `isbn_13`, `weight`, `dimensions`, and `keywords`.
- [ ] **6.3** Add nullable `publisher_id`, `cover_image` BLOB, and `cover_image_content_type` columns. Enforce that image bytes and media type are either both present or both absent, and apply the specified supported-type and size limits at the appropriate validation layers.
- [ ] **6.4** Give authors and publishers system-generated positive integer IDs, display names, and stored normalized comparison values with unique constraints. Do not make capitalization or extra whitespace create a second author/publisher record.
- [ ] **6.5** Give `book_authors` a unique (`book_id`, `author_id`) pair and foreign keys. Do not add an author-order field. Restrict deletion of referenced authors and publishers; arrange removal of author links when a book is deleted.
- [ ] **6.6** Add unique constraints for supplied ISBNs, canonical ISBN identity, and a non-null canonical book metadata key. Allow multiple absent ISBNs while enforcing equivalent-ISBN and metadata uniqueness.
- [ ] **6.7** Add non-null and check constraints for attribute lengths, positive values, allowed ID form, and other applicable stored-value rules. Choose explicit SQLite representations for dates and decimal kilograms that preserve the specified values and precision. Enforce relationship cardinality that cannot be expressed as a simple constraint in the transactional services.
- [ ] **6.8** Add indexes for foreign-key lookups, normalized name lookup, canonical identities, and required book searches. Document the correspondence between stored fields and logical models.
- [ ] **6.9** Test first-start schema creation, repeated startup without data loss, application of a pending migration to populated data, and startup failure for a deliberately failing migration. Use test migration fixtures for upgrade/failure cases.
- [ ] **6.10** Test database constraint violations directly, including duplicate names, repeated author links, invalid references, duplicate ISBN identities, metadata collisions with absent values, and inconsistent cover columns. Confirm normal startup never drops and recreates the schema.

Completion criterion: versioned migrations create and preserve a schema that enforces the required storage constraints and relationship rules.

## 7. Implement repositories and search queries

Specification: [Find and edit a book](system_specification.md#find-and-edit-a-book), [Database and persistence](system_specification.md#database-and-persistence).

- [ ] **7.1** Implement author and publisher repository operations for create, retrieve, update, list/search by name, normalized-name lookup, reference checks, and deletion.
- [ ] **7.2** Implement book repository operations for insertion, retrieval, updates, deletion, canonical ISBN lookup, and canonical metadata lookup. Prevent ordinary updates from changing IDs.
- [ ] **7.3** Persist and load the unordered author set, optional publisher, nullable scalar fields, cover bytes/media type, and internal duplicate keys without exposing internal fields as API attributes.
- [ ] **7.4** Implement normalized partial matching for book titles and author names, exact system-ID matching, and normalized ISBN searches accepting equivalent ISBN-10/ISBN-13 forms. Use parameterized SQL for all supplied values and handle SQL wildcard characters consistently as search input.
- [ ] **7.5** Provide separate brief and full read paths. Brief reads should obtain only the required fields and author names without loading cover-image BLOBs. Full reads must assemble every specified attribute.
- [ ] **7.6** Resolve current author and publisher display names from their linked records, so a rename is reflected in all linked books. Keep multi-query reads consistent while an operator transaction is committing changes.
- [ ] **7.7** Verify repository operations against real SQLite, including nullable values, exact date/weight round trips, BLOB byte equality, author sets without duplicates, searching, relationship updates, and missing records.

Completion criterion: repositories correctly read and write the complete book model and provide the specified search and API read data.

## 8. Implement author and publisher application services

Specification: [Authors and publishers](system_specification.md#authors-and-publishers), [Manage authors and publishers](system_specification.md#manage-authors-and-publishers), [Delete records](system_specification.md#delete-records).

- [ ] **8.1** Implement list/search, get, create, rename, and delete service operations using shared normalization and validation.
- [ ] **8.2** Reject creation of an existing normalized name and identify the existing record. Reject renaming to another record's name without silently merging records. Allow updating the current record's display spelling when uniqueness is preserved.
- [ ] **8.3** Keep IDs stable on rename and verify that linked book read results use the updated display name. Verify the visible GUI/API behavior after those interfaces are implemented.
- [ ] **8.4** Reject deletion of referenced authors or publishers with a useful application error. Delete unreferenced records in a transaction and translate foreign-key failures consistently if a database constraint prevents deletion.
- [ ] **8.5** Add tests for names differing only by case/spacing, significant accents/punctuation, rename conflicts, unchanged IDs, reference restrictions, successful unreferenced deletion, and rollback on persistence failure.

Completion criterion: author and publisher management enforces one normalized name per record and preserves all book references.

## 9. Implement cover-image validation and handling

Specification: [Cover images](system_specification.md#cover-images).

- [ ] **9.1** Accept JPEG and PNG input with a maximum original size of 5 MiB (5,242,880 bytes). Enforce the bound during upload handling and verify it on the server.
- [ ] **9.2** Inspect actual bytes, confirm the image can be decoded, and determine the validated `image/jpeg` or `image/png` media type. Reject empty, corrupt, unsupported, oversized, or falsely labelled input.
- [ ] **9.3** Preserve the validated original bytes for BLOB storage. Define keep, replace, and remove actions so editing another book field cannot accidentally clear a cover.
- [ ] **9.4** Prepare image preview data without prematurely persisting the book or creating persistent image files outside SQLite. Retain pending image input after a rejected save so the operator can retry; discard temporary uploads when the operator cancels or abandons the form.
- [ ] **9.5** Test accepted formats, size boundaries, invalid content, mismatched extensions/types, and byte preservation. Verify that Base64 encoding is reserved for API serialization rather than database storage.

Completion criterion: cover inputs are validated and ready to participate in book transactions, with distinct keep/replace/remove behavior.

## 10. Implement transactional book application services

Specification: [Add a book](system_specification.md#add-a-book), [Find and edit a book](system_specification.md#find-and-edit-a-book), [Duplicate books](system_specification.md#duplicate-books), [Delete records](system_specification.md#delete-records).

- [ ] **10.1** Implement shared get/list/search operations and a create operation that validates all attributes, confirms referenced authors/publisher exist, checks both duplicate identities, and generates the book ID.
- [ ] **10.2** Save the book, canonical identity values, author links, optional publisher, and optional cover bytes/media type in one transaction. Never leave a partially created record after any failure.
- [ ] **10.3** Handle a database uniqueness failure caused specifically by a generated book-ID collision by generating a new suffix and retrying safely. Keep the database constraint authoritative, including for overlapping insert attempts. Do not retry ISBN or metadata duplicates as if they were random ID collisions.
- [ ] **10.4** Implement editing with an immutable ID, current-record exclusion during duplicate checks, revalidation of references, and atomic updates to attributes, author links, publisher, identity keys, and cover actions.
- [ ] **10.5** Implement book deletion in one transaction, removing the book, author links, and image while retaining author/publisher records. Convert missing-record and deletion failures into clear application errors.
- [ ] **10.6** Translate duplicate constraints into errors identifying the existing book. Translate exhausted lock waits into a database-busy error. Keep transactions short and outside the period when the operator fills in forms.
- [ ] **10.7** Test successful creation/editing/deletion, unchanged IDs after title edits, duplicates with/without ISBNs, equivalent ISBN collisions, unordered author matching, optional identity values, and valid distinct editions/languages/publishers.
- [ ] **10.8** Force failures after partial work to verify complete rollback of scalar fields, author links, publisher, identity keys, and cover bytes. Test cover retention, replacement, removal, and rejected updates that preserve the old cover.
- [ ] **10.9** Reproduce generated-ID collisions and a lock timeout using isolated test connections. Verify that reads can continue during a WAL write transaction and never return a partially saved book.

Completion criterion: all book writes are validated, duplicate-safe, atomic, and preserve stable IDs and existing data when rejected.

## 11. Implement JSON models and read-only REST endpoints

Specification: [Retrieve books through the API](system_specification.md#retrieve-books-through-the-api), [JSON representations](system_specification.md#json-representations).

- [ ] **11.1** Create explicit API response models and mappings. Keep SQL attributes in `snake_case` and JSON keys in `kebab-case`; do not serialize persistence models directly.
- [ ] **11.2** Implement `GET /books-brief` as an array containing exactly `title`, `authors`, `edition`, and `id` for each book.
- [ ] **11.3** Implement `GET /books-full` as an array containing exactly the 15 specified attributes: `title`, `authors`, `publication-date`, `edition`, `language`, `cover-type`, `print-length`, `isbn-10`, `isbn-13`, `weight`, `dimensions`, `id`, `keywords`, `cover-image`, and `publisher`.
- [ ] **11.4** Return unique author names as an array and the publisher as a name or `null`. Exclude internal author/publisher IDs and canonical identity columns. Do not promise book or author order.
- [ ] **11.5** Serialize dates as `YYYY-MM-DD`, integers/decimals as JSON numbers, IDs/ISBNs as strings, and keywords as the stored comma-separated string. Include unset optional attributes as explicit JSON `null`, including omitted ISBN forms.
- [ ] **11.6** Serialize an existing cover as exactly `content-type` and `data`, using standard padded Base64 without line breaks or a data-URL prefix. Return `cover-image: null` when absent and never include cover data in brief results.
- [ ] **11.7** Return HTTP `200` with `Content-Type: application/json` for successful reads and `[]` for an empty database. Ensure these routes reach REST controllers rather than returning the Vaadin HTML bootstrap page.
- [ ] **11.8** Return errors as exactly `error-code` and `message`: HTTP `500`/`internal-error` for unexpected failures and HTTP `503`/`database-busy` for exhausted database lock waits. Log diagnostic details server-side and exclude stack traces, SQL, and database paths from responses.
- [ ] **11.9** Add HTTP contract tests for exact keys and types, hyphenated names, nulls, empty arrays, matching IDs across endpoints, current linked names, Base64 byte recovery, and error status/body combinations. Verify GET operations do not change stored data.

Completion criterion: both endpoints satisfy the documented JSON contract and return meaningful errors instead of HTML or successful empty lists on failure.

## 12. Build GUI navigation and author/publisher management

Specification: [Manage authors and publishers](system_specification.md#manage-authors-and-publishers), [Form behavior and validation](system_specification.md#form-behavior-and-validation).

- [ ] **12.1** Replace the placeholder landing view with navigation to books, authors, and publishers. Implement the views in Vaadin Flow and call application services directly.
- [ ] **12.2** Add separate author and publisher browse/search views with add, edit, and delete actions. Identify records by their internal IDs while displaying names.
- [ ] **12.3** Implement name forms with required indicators, specified length limits, Save and Cancel actions, and server-provided field errors. Retain input on validation or persistence failure.
- [ ] **12.4** Show an existing record when a duplicate name is rejected and prevent duplicate creation or accidental merges. Refresh lists and book selections after a successful save.
- [ ] **12.5** Require deletion confirmation identifying the selected record. Explain why referenced records cannot be deleted, allow valid unreferenced deletion, and leave data unchanged after cancellation or failure.
- [ ] **12.6** Verify the management flows in the browser, including name search, duplicate names, rename visibility on linked books, rejected referenced deletion, successful unreferenced deletion, and correct success/error notifications.

Completion criterion: operators can manage reusable authors and publishers entirely through the GUI with the specified validation and deletion restrictions.

## 13. Build book browse, detail, create, edit, and delete workflows

Specification: [Add a book](system_specification.md#add-a-book), [Find and edit a book](system_specification.md#find-and-edit-a-book), [Delete records](system_specification.md#delete-records), [Form behavior and validation](system_specification.md#form-behavior-and-validation).

- [ ] **13.1** Build a books list with an empty state, the specified title/author/ISBN/system-ID search, a details action, and Add book. Display the full saved record and cover image in the details view.
- [ ] **13.2** Build a book form containing every editable attribute, an unordered multi-author selector, and an optional publisher selector. Provide access to the management views for missing authors/publishers and refresh available choices without losing the book form's pending input.
- [ ] **13.3** Mark required fields, present appropriate date/numeric/text controls, and map service errors to the relevant fields. Display the system-generated ID after creation and keep it read-only during editing.
- [ ] **13.4** Add JPEG/PNG upload, preview, replacement, and explicit removal. Enforce the upload limit, report invalid images, and preserve the existing cover when no cover change is requested.
- [ ] **13.5** Connect Save to transactional creation/editing. Display the saved record and success confirmation only after the service succeeds. Preserve entered values, selections, and pending image choices after a rejected save so the operator can correct or retry them.
- [ ] **13.6** Implement Cancel so pending changes and uploads are discarded without changing stored data. Reload persisted values when opening a fresh edit session.
- [ ] **13.7** Identify the existing book when duplicate metadata or ISBN validation rejects a save. Explain database-busy and other save failures clearly without showing a success notification.
- [ ] **13.8** Implement confirmed book deletion, refresh the list after success, and show failures without removing a record that was not deleted. Retain reusable authors and publishers.
- [ ] **13.9** Verify all book workflows in the browser, including absent optional fields, multiple authors, creation of missing selections, edits preserving the ID, searches in equivalent ISBN forms, image keep/replace/remove, duplicates, cancellation, failures, and deletion.

Completion criterion: an operator can complete every specified book workflow through the GUI, and subsequent API reads reflect only successfully saved changes.

## 14. Implement the selected access policy

Specification: [Verification and remaining decisions](system_specification.md#verification-and-remaining-decisions).

- [ ] **14.1** Implement the GUI authentication and API access decisions recorded in stage 1. If the chosen deployment deliberately has no application login, document and apply the network/local-access restrictions that define that deployment.
- [ ] **14.2** When authentication is required, implement the chosen credential provisioning, login/logout or API credential flow, and access checks. Keep deployment credentials in external configuration rather than the JAR or repository.
- [ ] **14.3** Configure HTTPS for network access according to the selected hosting arrangement and document any reverse-proxy settings required by Spring Boot and Vaadin.
- [ ] **14.4** Verify authorized and unauthorized GUI/API behavior, any required session behavior, and continued access to necessary frontend resources. Document access-related API status/error responses while preserving the JSON key convention and avoiding leaked internals.

Completion criterion: the implementation and deployment configuration enforce the explicitly chosen access policy.

## 15. Complete integration and failure-path verification

Specification: [Verification and remaining decisions](system_specification.md#verification-and-remaining-decisions), [Operations and data durability](system_specification.md#operations-and-data-durability).

- [ ] **15.1** Connect the stage-level tests into the normal Gradle verification workflow and confirm meaningful test counts. Resolve failures; a build with `test NO-SOURCE` is not verification of the implemented application.
- [ ] **15.2** Exercise full author/publisher/book service flows against migrated SQLite, then read the same records through HTTP. Confirm consistent IDs, names, references, optional fields, and image contents.
- [ ] **15.3** Verify that GUI save failures retain input and that failed create/edit/delete operations leave database and API-visible state unchanged. Cover validation errors, duplicate constraints, invalid references, lock waits, and injected persistence failures.
- [ ] **15.4** Confirm deletion removes book relationships and BLOBs, prevents referenced author/publisher deletion, and removes deleted books from both APIs without deleting shared records.
- [ ] **15.5** Restart the application against a populated database and verify that migrations, IDs, relationships, canonical keys, and BLOBs remain intact. Confirm no startup path resets the schema.
- [ ] **15.6** Verify startup, migration, application-error, and shutdown logging. Confirm graceful shutdown stops new work and closes database resources cleanly, and that client errors do not expose internals.
- [ ] **15.7** Check the completed implementation against every specification section and close any uncovered requirement before release packaging.

Completion criterion: the integrated application passes normal, boundary, and failure scenarios using the real database and the defined interfaces.

## 16. Verify production build and executable packaging

Specification: [Build and packaging](system_specification.md#build-and-packaging).

- [ ] **16.1** Configure and verify the production build command `./gradlew clean build -Pvaadin.productionMode=true`. Keep the production frontend build part of the release process and run the automated verification tasks.
- [ ] **16.2** Inspect the executable Spring Boot JAR under `build/libs/` for application classes, runtime dependencies, the embedded server, Vaadin compiled frontend assets, and production-mode configuration.
- [ ] **16.3** Confirm development-only dependencies, including Spring Boot DevTools and Vaadin development tooling, are excluded from the release runtime. Identify the executable artifact clearly and distinguish it from any plain JAR.
- [ ] **16.4** Build from a fresh checkout and launch the executable JAR from a directory outside the source tree using a Java 25 runtime. Verify production GUI resources load without Node.js, Gradle, frontend build tools, or a separate servlet container on the runtime host.
- [ ] **16.5** Verify external port and database-path overrides on the packaged application. Confirm the database, BLOBs, backups, and deployment configuration remain outside the JAR.

Completion criterion: a reproducible executable production artifact runs independently of the development workspace with the required external data configuration.

## 17. Write operating instructions and verify backup/restore

Specification: [Runtime configuration](system_specification.md#runtime-configuration), [Operations and data durability](system_specification.md#operations-and-data-durability).

- [ ] **17.1** Expand the README with prerequisites, development startup, automated tests, production build, executable JAR selection, and links to the specification and operating instructions.
- [ ] **17.2** Document configuration-file, environment-variable, and command-line examples for port, database path, and busy timeout. Explain working-directory-relative paths and use an explicit absolute database path in production examples.
- [ ] **17.3** Document installation for the selected host: Java 25, a writable local data directory, one application process, startup/shutdown procedure, logging locations, selected access policy, and HTTPS where applicable.
- [ ] **17.4** Document and perform an offline backup: stop the application cleanly, close database connections, and copy the database to a separate backup location. Confirm the backup contains the image BLOBs without separate image files.
- [ ] **17.5** Document that a live backup, when needed, must use SQLite backup facilities. Never prescribe copying only the main database file while WAL writes are active. Verify any live-backup procedure supplied with the project.
- [ ] **17.6** Document and perform restoration into a separate data directory. Start the application against the restored database and verify book counts, IDs, author/publisher relationships, ISBN/metadata uniqueness, and exact cover bytes through the GUI/API.
- [ ] **17.7** Document the upgrade procedure: take and verify a backup before schema upgrades, replace the executable artifact without replacing data, apply migrations on startup, and inspect diagnostics. Document recovery from a failed upgrade using the backed-up database and compatible application artifact.
- [ ] **17.8** Document the supported operator workflows, delete permanence, duplicate-book policy, JSON examples, error responses, and the absence of guaranteed book/author ordering. Keep these instructions consistent with the final implementation.

Completion criterion: another operator can install, run, back up, restore, and upgrade the application using documented and verified procedures.

## 18. Run the release acceptance workflow and deliver

Specification: [Verification and remaining decisions](system_specification.md#verification-and-remaining-decisions).

- [ ] **18.1** Start the production JAR with a new external database and the selected deployment/access configuration. Confirm schema creation, production-mode startup, and an empty books view and empty JSON arrays.
- [ ] **18.2** Use the GUI to create authors and a publisher, then create a book with multiple authors, valid ISBNs, optional values, and a cover image. Confirm the displayed generated ID and both API responses.
- [ ] **18.3** Rename an author/publisher and edit the book's title, attributes, relationships, and cover. Verify linked names update, the book ID stays unchanged, and the APIs expose the saved changes with the exact key conventions.
- [ ] **18.4** Attempt duplicate names, duplicate books with and without ISBNs, invalid ISBNs, invalid attributes, and invalid image uploads. Confirm rejection, useful messages, retained form input, and no partial writes. Verify cancellation also leaves stored data unchanged.
- [ ] **18.5** Stop and restart the JAR against the same database. Confirm the record, ID, image, relationships, and duplicate checks survive the restart.
- [ ] **18.6** Verify rejected deletion of referenced authors/publishers, successful book deletion, and subsequent deletion of unreferenced author/publisher records. Confirm both APIs stop returning deleted books.
- [ ] **18.7** Complete a backup/restore acceptance run with representative data and images, and verify the documented schema-upgrade procedure on a copy of populated data.
- [ ] **18.8** Record the build/runtime versions, automated test results, browser checks, packaged smoke-test results, and backup/restore results. Resolve any failures before marking the release complete.
- [ ] **18.9** Deliver the executable production JAR and current installation, configuration, access, backup/restore, and API documentation. Verify startup and basic GUI/API operation in the selected deployment environment.

Completion criterion: the production artifact and documented deployment satisfy the complete specification, with evidence that persistence, images, deletion, restart, and restore work correctly.
