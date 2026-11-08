-- Initial schema for Book DB zz21.
--
-- Correspondence between stored fields and the logical models
-- ============================================================
--
-- Public attributes map one-to-one onto the system-attributes table in the specification: a
-- snake_case column here becomes a kebab-case JSON key in the API (publication_date ->
-- publication-date, isbn_13 -> isbn-13), and the api package performs that mapping explicitly.
--
-- Four columns are internal. They exist so the database, rather than a prior lookup, is the
-- authority on identity, and they are never exposed as API attributes:
--
--   books.normalized_title        comparison key of the title, for normalized partial search
--   books.canonical_isbn_13       edition identity derived from either supplied ISBN form
--   books.canonical_metadata_key  the duplicate-book key (CanonicalMetadata in domain.identity)
--   authors.normalized_name,
--   publishers.normalized_name    comparison keys that make one record per normalized name
--
-- Services compute all of these with domain.text.TextNormalizer and domain.identity, and write
-- them in the same transaction as the record they describe. The metadata key in particular cannot
-- be a generated column: it depends on the book_authors rows, which SQLite cannot see from a
-- column expression.
--
-- Rules that are deliberately NOT constraints
-- ===========================================
--
-- "At least one author per book" is a cardinality a table constraint cannot express, because the
-- join rows are inserted after the book row. The transactional services in stage 10 enforce it.
--
-- Image format and size are checked here as a backstop, but the real check is decoding the bytes
-- in stage 9: a file can be 5 MiB of valid-looking header and still not be an image.
--
-- Blankness checks use trim(), which strips ASCII whitespace only. A value consisting solely of
-- non-breaking spaces would satisfy them; service normalization is what rules that out, since it
-- collapses all Unicode whitespace before storing. These checks are defence in depth, not the
-- primary guard.

-- Note: foreign-key enforcement is not set here. SQLite ignores "PRAGMA foreign_keys" inside a
-- transaction, which is where Flyway runs a migration, so a statement here would silently do
-- nothing. Enforcement is applied per connection by the data source instead.

-- Publishers -----------------------------------------------------------------------------------

CREATE TABLE publishers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,

    CONSTRAINT publishers_id_positive CHECK (id > 0),
    CONSTRAINT publishers_name_length CHECK (length(name) BETWEEN 1 AND 200 AND trim(name) = name),
    CONSTRAINT publishers_normalized_name_length CHECK (length(normalized_name) BETWEEN 1 AND 200),
    -- One record per normalized name: capitalization or extra whitespace must not create a second.
    CONSTRAINT publishers_normalized_name_unique UNIQUE (normalized_name)
);

-- Authors --------------------------------------------------------------------------------------

CREATE TABLE authors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,

    CONSTRAINT authors_id_positive CHECK (id > 0),
    CONSTRAINT authors_name_length CHECK (length(name) BETWEEN 1 AND 50 AND trim(name) = name),
    CONSTRAINT authors_normalized_name_length CHECK (length(normalized_name) BETWEEN 1 AND 50),
    CONSTRAINT authors_normalized_name_unique UNIQUE (normalized_name)
);

-- Books ----------------------------------------------------------------------------------------

CREATE TABLE books (
    -- Immutable, system-generated: <title-fragment>-<six random characters>.
    id TEXT NOT NULL PRIMARY KEY,

    title TEXT NOT NULL,
    normalized_title TEXT NOT NULL,
    -- Stored as ISO text rather than a numeric date so the exact calendar day round-trips.
    publication_date TEXT,
    edition INTEGER,
    language TEXT NOT NULL,
    cover_type TEXT,
    print_length INTEGER NOT NULL,

    isbn_10 TEXT,
    isbn_13 TEXT,
    canonical_isbn_13 TEXT,

    -- Kilograms are specified to at most three decimal places, which is exactly whole grams.
    -- SQLite has no decimal type and REAL is binary floating point, so 0.544 kg could not be
    -- stored and returned unchanged. Integer grams preserves every permitted value exactly.
    -- The column is named for its unit so no reader mistakes it for kilograms.
    weight_grams INTEGER,

    dimensions TEXT,
    keywords TEXT,

    -- Validated image bytes, stored directly. Base64 belongs only in JSON serialization.
    cover_image BLOB,
    cover_image_content_type TEXT,

    publisher_id INTEGER REFERENCES publishers (id) ON DELETE RESTRICT,

    canonical_metadata_key TEXT NOT NULL,

    CONSTRAINT books_id_form CHECK (
        length(id) BETWEEN 8 AND 19
        AND id NOT GLOB '*[^a-z0-9-]*'
        AND id NOT GLOB '-*'
        AND id NOT GLOB '*-'
    ),
    CONSTRAINT books_title_length CHECK (length(title) BETWEEN 1 AND 50 AND trim(title) = title),
    CONSTRAINT books_normalized_title_length CHECK (length(normalized_title) BETWEEN 1 AND 50),
    -- date() rolls an impossible day over to the following month, so comparing its result with the
    -- stored text rejects 2026-02-30 as well as unpadded and non-ISO forms. date(NULL) is NULL, so
    -- an absent date satisfies this without a separate branch.
    CONSTRAINT books_publication_date_valid CHECK (date(publication_date) IS publication_date),
    CONSTRAINT books_edition_positive CHECK (edition IS NULL OR edition > 0),
    CONSTRAINT books_language_length CHECK (
        length(language) BETWEEN 1 AND 50 AND trim(language) = language
    ),
    CONSTRAINT books_cover_type_length CHECK (
        cover_type IS NULL
        OR (length(cover_type) BETWEEN 1 AND 30 AND trim(cover_type) = cover_type)
    ),
    CONSTRAINT books_print_length_positive CHECK (print_length > 0),

    CONSTRAINT books_isbn_10_form CHECK (
        isbn_10 IS NULL
        OR (length(isbn_10) = 10
            AND substr(isbn_10, 1, 9) NOT GLOB '*[^0-9]*'
            AND substr(isbn_10, 10, 1) GLOB '[0-9X]')
    ),
    CONSTRAINT books_isbn_13_form CHECK (
        isbn_13 IS NULL
        OR (length(isbn_13) = 13
            AND isbn_13 NOT GLOB '*[^0-9]*'
            AND substr(isbn_13, 1, 3) IN ('978', '979'))
    ),
    CONSTRAINT books_canonical_isbn_13_form CHECK (
        canonical_isbn_13 IS NULL
        OR (length(canonical_isbn_13) = 13
            AND canonical_isbn_13 NOT GLOB '*[^0-9]*'
            AND substr(canonical_isbn_13, 1, 3) IN ('978', '979'))
    ),
    -- An edition identity exists exactly when an ISBN was supplied. Without this, a row could
    -- carry an ISBN that uniqueness never examines.
    CONSTRAINT books_canonical_isbn_13_consistent CHECK (
        (canonical_isbn_13 IS NULL AND isbn_10 IS NULL AND isbn_13 IS NULL)
        OR (canonical_isbn_13 IS NOT NULL AND (isbn_10 IS NOT NULL OR isbn_13 IS NOT NULL))
    ),

    CONSTRAINT books_weight_positive CHECK (weight_grams IS NULL OR weight_grams > 0),
    CONSTRAINT books_dimensions_length CHECK (
        dimensions IS NULL
        OR (length(dimensions) BETWEEN 1 AND 50 AND trim(dimensions) = dimensions)
    ),
    CONSTRAINT books_keywords_length CHECK (
        keywords IS NULL OR (length(keywords) BETWEEN 1 AND 500 AND trim(keywords) = keywords)
    ),

    -- Bytes and media type are present together or absent together, so a cover can never be
    -- half-stored by a partial update.
    CONSTRAINT books_cover_image_paired CHECK (
        (cover_image IS NULL AND cover_image_content_type IS NULL)
        OR (cover_image IS NOT NULL AND cover_image_content_type IS NOT NULL)
    ),
    CONSTRAINT books_cover_image_content_type CHECK (
        cover_image_content_type IS NULL
        OR cover_image_content_type IN ('image/jpeg', 'image/png')
    ),
    -- length() on a BLOB counts bytes; 5 MiB is 5242880 of them.
    CONSTRAINT books_cover_image_size CHECK (
        cover_image IS NULL OR length(cover_image) BETWEEN 1 AND 5242880
    ),

    CONSTRAINT books_canonical_metadata_key_present CHECK (length(canonical_metadata_key) > 0),

    -- A supplied ISBN belongs to one book, in whichever form it was entered...
    CONSTRAINT books_isbn_10_unique UNIQUE (isbn_10),
    CONSTRAINT books_isbn_13_unique UNIQUE (isbn_13),
    -- ...and the derived identity closes the gap between the two forms, so entering an ISBN-10
    -- cannot bypass a stored book holding its equivalent ISBN-13. SQLite treats NULLs as distinct,
    -- so any number of books may have no ISBN.
    CONSTRAINT books_canonical_isbn_13_unique UNIQUE (canonical_isbn_13),
    -- The duplicate-book rule for records whose ISBNs are absent or differ. Non-null, so every
    -- book is covered by it.
    CONSTRAINT books_canonical_metadata_key_unique UNIQUE (canonical_metadata_key)
);

-- Book authors ---------------------------------------------------------------------------------

-- A set, not a list: the composite primary key forbids linking an author twice, and there is
-- deliberately no ordering column, because author order carries no meaning.
CREATE TABLE book_authors (
    -- Deleting a book removes its author links in the same statement...
    book_id TEXT NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    -- ...while a referenced author cannot be deleted at all, so removing an author can never
    -- silently strip a book of one of its authors.
    author_id INTEGER NOT NULL REFERENCES authors (id) ON DELETE RESTRICT,

    CONSTRAINT book_authors_pk PRIMARY KEY (book_id, author_id)
);

-- Indexes --------------------------------------------------------------------------------------

-- The composite primary key already serves lookups by book_id. The reverse direction, needed to
-- decide whether an author is still referenced before deleting it, requires its own index.
CREATE INDEX idx_book_authors_author_id ON book_authors (author_id);

-- Finding the books of a publisher, and checking whether a publisher is still referenced.
CREATE INDEX idx_books_publisher_id ON books (publisher_id);

-- Title search. Partial matches cannot use an index, but exact and prefix lookups can, and the
-- column is what keeps search consistent with the normalization used everywhere else.
CREATE INDEX idx_books_normalized_title ON books (normalized_title);

-- The unique constraints above already provide indexes for authors.normalized_name,
-- publishers.normalized_name, books.isbn_10, books.isbn_13, books.canonical_isbn_13 and
-- books.canonical_metadata_key, so no further indexes are declared for them.
