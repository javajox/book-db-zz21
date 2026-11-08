-- A fixture migration that fails on purpose, not part of the application schema.
--
-- The first statement succeeds and the second refers to a table that does not exist, so a test can
-- confirm that a failed migration leaves no partial schema behind and that startup stops rather
-- than serving requests against it.

ALTER TABLE books ADD COLUMN never_committed TEXT;

INSERT INTO table_that_does_not_exist (id) VALUES (1);
