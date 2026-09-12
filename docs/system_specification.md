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

This project must be implemented in Java using JDK25. As the main framework SpringBoot must be used. As GUI Vaadin must be used. Because the system is small and will have
like 1-2 users the SQLite DB will be used. The final result must be a Jar file (something like fatJar) that will contain everthing
the DB will be of course an external file