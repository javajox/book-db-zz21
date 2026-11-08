-- A fixture migration, not part of the application schema.
--
-- Applied on top of V1 to verify that a pending migration reaches an already-populated database
-- without disturbing the records in it.

ALTER TABLE books ADD COLUMN shelf_location TEXT;

CREATE INDEX idx_books_shelf_location ON books (shelf_location);
