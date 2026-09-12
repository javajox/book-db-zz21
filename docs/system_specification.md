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
