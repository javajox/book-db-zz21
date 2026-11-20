# Operating instructions

How to install, configure, run, back up, restore and upgrade book-db-zz21. Functional behaviour is
defined in [system_specification.md](system_specification.md); the reasoning behind the build and
deployment arrangement is in [implementation_decisions.md](implementation_decisions.md).

Every procedure below has been performed against the packaged artifact, not only written down. Where
a step produced evidence worth keeping, it is quoted.

- [Installation](#installation)
- [Configuration](#configuration)
- [Starting and stopping](#starting-and-stopping)
- [Logging](#logging)
- [Access and credentials](#access-and-credentials)
- [HTTPS](#https)
- [Backups](#backups)
- [Restoring](#restoring)
- [Upgrading](#upgrading)
- [What the operator can do](#what-the-operator-can-do)
- [The API for integrators](#the-api-for-integrators)

## Installation

The application is one file. There is no installer, no service definition and no fixed layout: copy
`book-db-zz21.jar` to any directory you can write to and run it.

Requirements on the machine:

- **A Java 25 runtime.** Not a JDK, not Gradle, not Node.js, and no servlet container or database
  server. Verify with `java -version`.
- **A writable directory for the database.** The application creates it if its parent exists and
  permissions allow. SQLite also writes `-wal` and `-shm` companion files beside the database, so
  the directory itself must be writable, not just the file.
- **A local filesystem.** Not NFS, SMB or any other network filesystem. Write-ahead logging depends
  on shared-memory locking that network filesystems do not implement correctly. The application
  checks this at startup and refuses to run if write-ahead logging did not engage, rather than
  running slowly and silently without it.
- **One process per database.** Running two instances against one file is not supported.

A minimal install:

```bash
mkdir -p ~/tmp/data
cp build/libs/book-db-zz21.jar ~/tmp/
cd ~/tmp
java -jar book-db-zz21.jar provision-credentials operator   # see Access and credentials
```

### Why `--enable-native-access=ALL-UNNAMED`

SQLite is a native library, and Java 25 warns when it is loaded without this flag:

```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by org.sqlite.SQLiteJDBCLoader ...
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

The application works without the flag today but will not in a future Java release, so include it:

```bash
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar ...
```

## Configuration

Three settings define the deployment, plus three credentials covered under
[Access and credentials](#access-and-credentials).

| Property | Default | Meaning |
|----------|---------|---------|
| `server.port` | `8080` | The port serving both the GUI and the API. |
| `bookdb.database.path` | `./data/books.db` | The SQLite database file. |
| `bookdb.database.busy-timeout-ms` | `5000` | How long a write waits for the database lock before reporting the database busy. Must be between 100 and 60000. |

Any of the three mechanisms below works, and they can be mixed. Later ones override earlier ones.

### Command-line arguments

The simplest form, and the one these instructions use throughout:

```bash
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --server.port=8080 \
  --bookdb.database.path=/home/dima/tmp/data/books.db \
  --bookdb.database.busy-timeout-ms=5000
```

### A configuration file

Put an `application.properties` in a `config/` directory beside the JAR, or anywhere and point at it
with `--spring.config.additional-location`:

```properties
server.port=8080
bookdb.database.path=/home/dima/tmp/data/books.db
bookdb.database.busy-timeout-ms=5000
```

```bash
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --spring.config.additional-location=file:/home/dima/tmp/etc/
```

Note the trailing slash: a location ending in `/` is a directory to search, without one it is a
single file.

### Environment variables

Upper-case, with dots and dashes replaced by underscores:

```bash
export SERVER_PORT=8080
export BOOKDB_DATABASE_PATH=/home/dima/tmp/data/books.db
export BOOKDB_DATABASE_BUSY_TIMEOUT_MS=5000
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar
```

These names were checked against the packaged artifact rather than derived from the binding rules;
`BOOKDB_DATABASE_BUSYTIMEOUTMS` without the underscores works too. This is the mechanism to prefer
for the three credentials, since environment variables are not visible in process listings.

### Use an absolute database path

`bookdb.database.path` accepts a relative path, resolved against **the process working directory** —
not the JAR's location. `./data/books.db` started from `/home/dima/tmp` means
`/home/dima/tmp/data/books.db`; the same command run from elsewhere silently opens, or creates, a
different database. An empty catalogue where the records should be is the usual symptom.

Use an absolute path in anything other than a development run. The resolved path is logged at
startup precisely so this can be checked:

```
SQLite ready at /home/dima/tmp/data/books.db with journal_mode=wal, foreign_keys=on, busy_timeout=5000ms
```

If the configured path cannot be used — the parent is missing, the file is a directory, the
directory is read-only — startup fails with a message naming the path. It never falls back to
another database.

## Starting and stopping

Start it in the foreground and watch the log, or in the background with the output redirected:

```bash
cd /home/dima/tmp
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --bookdb.database.path=/home/dima/tmp/data/books.db >> logs/book-db.log 2>&1 &
```

It is ready when the log says so:

```
Tomcat started on port 8080 (http) with context path '/'
Started BookDbZz21Application in 1.9 seconds (process running for 2.3)
```

**Stop it with Ctrl-C, or `kill <pid>`** — a plain `SIGTERM`. Never `kill -9`. A clean stop finishes
in-flight requests, folds the write-ahead log back into the database file, and leaves a single
complete file behind:

```
Commencing graceful shutdown. Waiting for active requests to complete
Graceful shutdown complete
Checkpointed the write-ahead log for /home/dima/tmp/data/books.db on shutdown
```

After that the `-wal` and `-shm` files are gone and `books.db` alone is the whole database — which is
what makes the offline backup below trustworthy. `kill -9` skips the checkpoint, leaving committed
data in the `-wal` file; nothing is lost, because the next startup replays it, but a backup taken in
that state must include the companion files or it will be incomplete.

## Logging

Log output goes to the console. There is no log file unless you redirect to one, as above.

What is logged, and worth reading:

| When | What |
|------|------|
| Startup | The resolved database path with the journal mode, foreign-key enforcement and busy timeout read back from SQLite — not echoed from configuration |
| Startup | Migration results: `Applied 1 migration(s) to … now at schema version 1`, or `Database schema at … is up to date` |
| Startup | `Vaadin is running in production mode`, the port, and the ready message |
| Failures | Application errors with their diagnostic and stack trace |
| Shutdown | The graceful-shutdown messages and the write-ahead-log checkpoint |

API error responses deliberately carry none of this. A failed read returns an error code and a short
message with no SQL, no file path, no table name and no stack trace, while the full diagnostic goes
to the log. If an integrator reports an opaque `internal-error`, the explanation is in the log at the
matching timestamp.

## Access and credentials

The application will not start without credentials. There is no built-in account and no default
password, so a deployment cannot accidentally come up with anonymous access to the management GUI.

| Property | Meaning |
|----------|---------|
| `bookdb.security.operator-username` | The single operator account. |
| `bookdb.security.operator-password-hash` | That account's BCrypt password hash. |
| `bookdb.security.api-token-digest` | The SHA-256 digest, lowercase hex, of the API bearer token. |

Generate all three from the artifact itself:

```bash
java -jar book-db-zz21.jar provision-credentials operator
```

It prompts for the password rather than taking it as an argument, because an argument is visible in
process listings and shell history. It prints the three property lines and a freshly generated API
token.

**The token is shown once.** It cannot be recovered from the stored digest. Give it to the
integration client through whatever secure channel you use, and if it is lost, run the tool again and
replace the digest.

Only derived values are ever stored, so the configuration file never holds a credential that could be
replayed. The application also checks their shape: pasting the plain password or the raw token into
these properties is rejected at startup with a message saying so, because that mistake otherwise
looks like a working configuration.

Keep the credentials outside the JAR and out of version control. Put them in the configuration file
or supply them as environment variables; avoid command-line arguments for these three specifically,
for the process-listing reason above.

To change the password or rotate the token, run the tool again, update the configuration, and
restart. There is no account-management screen and no registration endpoint.

### Who can reach what

- The **GUI** needs an operator session. Any route reached without one redirects to the login page.
- The **API** (`/books-brief`, `/books-full`) needs `Authorization: Bearer <token>`. Without a valid
  token it answers `401` with a JSON error — never a redirect, because a client following one would
  receive the HTML login page with a success status and could not tell it from data.
- The API token grants **nothing** in the GUI; presenting it to a management route still redirects to
  the login page.

## HTTPS

The initial deployment serves plain HTTP on a trusted private network. Enable TLS before exposing it
anywhere less trusted. It is configured directly in Spring Boot, with certificate material kept
outside the JAR and outside version control:

```bash
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --server.ssl.enabled=true \
  --server.ssl.key-store=/home/dima/tmp/etc/bookdb.p12 \
  --server.ssl.key-store-type=PKCS12 \
  --server.ssl.key-store-password=... \
  --server.ssl.key-alias=bookdb
```

This was verified with a generated PKCS12 keystore: the server reported `(https)`, negotiated
TLSv1.3, and every access rule held unchanged over TLS. Two things remain specific to your
environment: a certificate the operator's browser and the integration client both trust, and a
renewal process. Supply the keystore password through the configuration file or an environment
variable rather than on the command line.

If a reverse proxy is ever put in front and terminates TLS, add
`--server.forward-headers-strategy=framework` so redirects are built from the forwarded headers;
without it a redirect to the login page can point at `http` or at the internal port. The proxy must
also forward WebSocket upgrade headers if Vaadin push is enabled later.

## Backups

Everything is in the one SQLite file — records, relationships, duplicate-detection keys and **the
cover images**. Cover images are stored as BLOBs inside the database; the application never writes
image files. So backing up that one file backs up everything, and there is no second location to
remember.

Back the database up independently of the JAR. Replacing the artifact must never involve replacing
data, which is why the two live in separate directories.

### Offline backup — the simple case

With the application stopped, the database is a single self-contained file and copying it is enough.

```bash
# 1. Stop the application cleanly (Ctrl-C or kill, never kill -9) and wait for:
#      Checkpointed the write-ahead log for … on shutdown

# 2. Confirm no companion files remain. Only books.db should be listed.
ls ~/tmp/data/

# 3. Copy it, with a timestamp so backups do not overwrite each other.
mkdir -p ~/tmp/backups
cp -p ~/tmp/data/books.db ~/tmp/backups/books-$(date +%Y%m%dT%H%M%S).db

# 4. Verify the copy.
sha256sum ~/tmp/data/books.db ~/tmp/backups/books-*.db
```

Performed on this project's own database. Step 2 listed `books.db` alone, step 4 produced identical
digests, and reading the backup independently found the two books, three authors, three publishers,
four author links and the one cover image — a 155,849-byte JPEG whose SHA-256 matched the original.
No image file existed anywhere outside the database.

If `-wal` or `-shm` files are still present at step 2, the application did not stop cleanly. Either
start it and stop it properly, or copy all three files together.

### Live backup — when it cannot be stopped

**Do not copy only `books.db` while the application is running.** Under write-ahead logging,
committed data lives in the `-wal` file until a checkpoint folds it in, so a copy of the main file
alone is missing the most recent records — and looks perfectly healthy, which is what makes it
dangerous.

Use SQLite's own facility, which writes a consistent snapshot of a live database:

```bash
sqlite3 ~/tmp/data/books.db "VACUUM INTO '/home/dima/tmp/backups/books-live-$(date +%Y%m%dT%H%M%S).db'"
```

This was demonstrated rather than assumed. With the application running, a record was committed and
both methods were used on the same database:

| Backup method | The committed record | `PRAGMA integrity_check` |
|---------------|----------------------|--------------------------|
| `cp` of `books.db` only | **lost** | `ok` |
| `VACUUM INTO` | present | `ok` |

The `-wal` file held 12,392 bytes of committed data at that moment. Note that the incomplete backup
passed the integrity check: SQLite confirms the file is a structurally valid database, not that it
contains everything that was committed. An integrity check cannot detect this mistake, so the method
has to be right in the first place.

`VACUUM INTO` needs free space for a full copy and briefly adds read load. The `sqlite3` command-line
tool is not shipped with this application; install it from your distribution (`sqlite3` package), or
stop the application and take an offline backup instead.

### Before any upgrade

Take a backup and verify it before applying a new artifact to an existing deployment. The
[upgrade procedure](#upgrading) depends on it.

## Restoring

Restoring means putting a backup file where the database belongs and starting the application. There
is no import step, because the backup is the database.

```bash
# 1. Stop the application.

# 2. Restore into a directory of its own first, rather than over the live database,
#    so the current file is still there if the backup turns out to be the wrong one.
mkdir -p ~/tmp/restored/data
cp ~/tmp/backups/books-20261120T213555.db ~/tmp/restored/data/books.db

# 3. Start the application against it on a spare port.
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --server.port=18099 \
  --bookdb.database.path=/home/dima/tmp/restored/data/books.db

# 4. Check it, then stop it and move the file into place.
```

What to check at step 4, and what checking it proved here:

| Check | How | Result |
|-------|-----|--------|
| It needs no migration | the startup log | `Database schema at … is up to date` |
| Book count and identifiers | `GET /books-brief` and `/books-full` | 2 books, identical id sets in both |
| Relationships | the `authors` and `publisher` of each book | `Adsfsd` → John Travolta, Ion Vacarii, Super media 122; `Mathematics` → John Travolta, Ion Bobei, Zoro |
| Cover images, exactly | Base64-decode `cover-image.data` and compare | 155,849 bytes, byte-identical, SHA-256 matching the pre-backup image |
| Uniqueness guarantees | attempt duplicates against the restored file | all rejected (below) |

The last check matters because duplicate detection is enforced by database constraints and stored
keys, so a restore that lost them would accept duplicate books later. Against the restored database,
every one was refused:

| Attempt | Rejected by |
|---------|-------------|
| A duplicate book id | `books.id` primary key |
| A duplicate ISBN-13 | `books.canonical_isbn_13` unique constraint |
| An ISBN-10 equivalent to a stored ISBN-13 | `books.canonical_isbn_13` unique constraint |
| A duplicate title/authors/edition/language/publisher combination | `books.canonical_metadata_key` unique constraint |
| A reference to a publisher that does not exist | foreign key |

The third is the interesting one: the two forms are different strings, and only the stored canonical
identity makes them the same book. Restoring preserved that.

Restoring is the recovery path for accidental deletion, because **deletions are permanent**. There is
no undo and no recycle bin.

## Upgrading

The database is managed by versioned migrations. A new artifact applies whatever is outstanding when
it starts, and preserves existing data. Normal startup never drops and recreates the schema.

```bash
# 1. Back up and verify. Not optional: this is the recovery path if the upgrade fails.
#    Stop the application, then copy and check as under Offline backup.

# 2. Replace only the artifact. Leave the data and configuration directories alone.
cp /path/to/new/book-db-zz21.jar ~/tmp/book-db-zz21.jar

# 3. Start it and read the log before announcing success.
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar \
  --bookdb.database.path=/home/dima/tmp/data/books.db
```

At step 3 expect either `Applied N migration(s) to … now at schema version N`, or
`Database schema at … is up to date` when the new artifact has no new migrations. Then confirm the
records are there, through the GUI or `GET /books-brief`.

### If the upgrade fails

Startup stops. The application will not serve requests against a partially migrated schema, and the
failed migration is rolled back.

This was rehearsed with an artifact carrying a deliberately broken migration, run against a populated
database:

```
Migrating schema "main" to version "2 - add reading status"
Migration of schema "main" to version "2 - add reading status" failed! Changes successfully rolled back.
Message    : [SQLITE_ERROR] SQL error or missing database (duplicate column name: reading_status)
Location   : db/migration/V2__add_reading_status.sql
```

The process exited with status 1. Afterwards the database was **byte-identical to the backup taken
before the upgrade** — same SHA-256 — with no version 2 row in the migration history, no
half-applied column, and all records intact.

So recovery is simply to put the previous artifact back:

```bash
cp ~/tmp/book-db-zz21.jar.previous ~/tmp/book-db-zz21.jar
java --enable-native-access=ALL-UNNAMED -jar book-db-zz21.jar --bookdb.database.path=...
```

Verified: the previous artifact started against that same database, reported
`Database schema at … is up to date`, and served every record. No restore was needed.

Keep the previous artifact until the upgrade is confirmed working — that is what makes this one
command. Restore from the backup only if the database itself was damaged, which a failed migration
alone does not do. Read the diagnostic, which names the migration file and the SQL error, and report
it rather than retrying: the same artifact will fail the same way.

## What the operator can do

The GUI has three views, reached from the navigation drawer: **Books**, **Authors** and
**Publishers**. Authors and publishers are separate because they are reusable — an author is entered
once and then selected for many books.

### Authors and publishers

Browse or search by name, add, rename, delete. Within this system **authors with the same name are
the same author**: one record per name, referenced by every one of their books. Renaming a record
updates every book that references it, because the name is joined rather than copied.

Name comparison ignores surrounding and repeated spaces and ignores case, so ` Gilbert  Strang ` and
`gilbert strang` are the same author. Accents and punctuation are significant, so `Émile Zola` and
`Emile Zola` are different authors, as are `J.R.R. Tolkien` and `JRR Tolkien`. Adding a name that
already exists is refused with a message identifying the existing record, and renaming onto another
record's name is refused rather than merging them.

### Books

Add, search, view, edit and delete. Search matches a partial title, a partial author name, an ISBN
in either form, or an exact system id — one field covers all of them.

The system assigns each book an **immutable id** such as `dune-7k3m9x`, from a fragment of the title
plus a random suffix. The operator never types it. It never changes, not even when the title does,
because integrators may already reference it — so the fragment reflects the title at creation time
and may no longer match. Treat it as an opaque string.

Required: title, at least one author, language, print length. Everything else is optional and stored
as nothing at all when left empty; zero is not a substitute for an absent number.

The limits, which the server enforces whatever the form allows:

| Attribute | Limit |
|-----------|-------|
| Title, language, dimensions, author name | 50 characters |
| Publisher name | 200 characters |
| Cover type | 30 characters |
| Keywords | 500 characters in total |
| Edition, print length | whole numbers greater than zero |
| Weight | greater than zero, kilograms, at most three decimal places |
| Cover image | JPEG or PNG, at most 5 MiB (5,242,880 bytes) |

Weight precision is rejected rather than rounded, so `0.5445` is an error rather than silently
becoming `0.544`. Keywords are tidied on save: each is trimmed, empties are dropped,
case-insensitive duplicates are removed, and the rest are stored separated by a comma and a space.

Both ISBN fields are optional, but a supplied ISBN must be valid: spaces and hyphens are removed,
checksums are verified, and if both forms are given they must identify the same edition. An ISBN
cannot belong to two books, in either form — entering an ISBN-10 whose ISBN-13 equivalent is already
assigned is refused.

A cover image is optional: JPEG or PNG, at most 5 MiB. The content is decoded to confirm it is
really an image, so renaming `notes.txt` to `cover.jpg` is rejected. Editing other fields without
supplying a new image keeps the existing one; removing a cover is an explicit action.

### Duplicate books are refused, not warned about

A proposed book is a duplicate if either its ISBN identity or its combination of **title, set of
authors, edition, language and publisher** already exists. Two missing editions count as equal, as do
two missing publishers.

A different edition, language or publisher can therefore be a separate record, provided its ISBN is
not already assigned. A different cover, weight, page count or keyword list does **not** make a
distinct book — if that is the only difference, it is the same book and the save is refused, naming
the existing one.

### Failed saves keep your input

If a save is rejected, the form stays open with everything typed still in it, including a pending
image upload, and every rejected field is marked at once rather than one error at a time. Nothing is
stored until the whole record validates, so a failed save never leaves a partial book, a broken
relationship or an orphaned image. **Cancel** discards unsaved changes and leaves stored records
untouched.

A success confirmation appears only after the change is committed, so seeing one means the data is
saved.

### Deleting

Deletion asks for confirmation and names the record. Deleting a book also removes its author links
and its cover image, but not its authors or its publisher — those remain for other books.

An author or publisher that is still referenced by any book **cannot** be deleted; the GUI explains
that the linked books must be edited or deleted first. Once nothing references it, it can be deleted.

**Deletions are permanent.** Recovering a deleted record means [restoring a backup](#restoring).

## The API for integrators

Two read-only endpoints. They never modify stored data.

| Method | Endpoint | Returns |
|--------|----------|---------|
| `GET` | `/books-brief` | every book as `title`, `authors`, `edition`, `id` |
| `GET` | `/books-full` | every book with all fifteen attributes |

Both need the bearer token:

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/books-brief
```

JSON keys are lowercase and hyphen-separated. Dates are `YYYY-MM-DD` with no time or zone. An unset
optional attribute is present as `null` rather than omitted. An empty catalogue is `[]`.

```json
[
  {
    "title": "Mathematics",
    "authors": ["John Travolta", "Ion Bobei"],
    "publication-date": "2027-05-12",
    "edition": 4,
    "language": "English",
    "cover-type": "Softcover",
    "print-length": 555,
    "isbn-10": null,
    "isbn-13": null,
    "weight": null,
    "dimensions": null,
    "id": "mathematics-ugx0rz",
    "keywords": null,
    "cover-image": {"content-type": "image/jpeg", "data": "/9j/4AAQSkZJRg..."},
    "publisher": "Zoro"
  }
]
```

`authors` carries display names, not internal identifiers, and `publisher` is a name or `null`.
`cover-image` is `null` or an object with exactly `content-type` and `data`, the image bytes as
standard Base64 with padding and no line breaks or data-URL prefix. The brief endpoint never
includes image data, which is the point of having it.

**No ordering is guaranteed** — not of books, and not of the names within `authors`. The two
endpoints may return them in different orders, and a future version may change either. Sort by
whatever you need. The `id` is the same in both endpoints and is stable forever, so it is what to
match brief and full records on, and what to store as a reference.

The initial contract returns all books. There is no pagination, filtering or sorting.

### Errors

A failure returns a JSON object with exactly `error-code` and `message`:

| Status | `error-code` | Meaning |
|--------|--------------|---------|
| `401` | `unauthorized` | No valid token. Check the `Authorization` header. |
| `503` | `database-busy` | A write held the lock longer than the busy timeout. **Retryable** — wait and try again. |
| `500` | `internal-error` | Something unexpected. Not retryable; ask the operator to check the log. |

```json
{
  "error-code": "database-busy",
  "message": "The database is busy. Please retry later."
}
```

Messages are deliberately short and carry no SQL, paths or stack traces; the detail is in the
server's log at the same timestamp.

A failure is **never** reported as `200` with an empty list. If you receive `200 []`, the catalogue is
genuinely empty — do not treat an error as a signal that every book was deleted.
