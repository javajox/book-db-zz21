# Book DB zz21

## Introduction
This document outlines the system specification for the project. This project is a DB about books with a RESTful API, main purpose being
to integrate with other systems and provide data. Also, it will have a web based GUI where the operator can manage the data.

## Specification

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
| ID                | `id`               | String    | Special string             | Internal identification of the book inside the system     |
| Keywords          | `keywords`         | String    | Comma separated string     | Comma-separated keywords that describe the book's content |
| Cover image       | `cover_image`      | Image     | May be NULL                | Image of cover of the book                                |
| Publisher         | `publisher`        | String    | May be NULL                | The publisher of the book                                 |

The system will provide a RESTful API for providing data to other systems. The following two endpoints will be provided:
 - `/books`: Retrieve a list of books, but only basic information:
   * title
   * authors
   * edition
   * id
 - `/books-full`: Retrieve full information about all books.
