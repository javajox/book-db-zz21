package org.wintermutecore.bookdbzz21.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies how the configured database path is resolved and validated.
 *
 * <p>Relative-path cases are written as a relative route into the test's temporary directory rather
 * than as something like {@code ./data/books.db}. Resolution creates directories, so a test using a
 * plainly relative path would create them inside the project checkout — and in the default case,
 * inside the very directory an operator's real database would live in.
 */
class DatabaseLocationTest {

    @TempDir
    Path directory;

    @Test
    void resolvesAnAbsolutePathUnchanged() {
        var file = directory.resolve("books.db");

        var location = DatabaseLocation.resolve(file.toString());

        assertThat(location.path()).isEqualTo(file);
        assertThat(location.directory()).isEqualTo(directory);
    }

    @Test
    void resolvesARelativePathAgainstTheWorkingDirectory() {
        var target = directory.resolve("books.db");
        var relative = Path.of("").toAbsolutePath().relativize(target);
        assertThat(relative.isAbsolute()).isFalse();

        var location = DatabaseLocation.resolve(relative.toString());

        assertThat(location.path()).isEqualTo(target);
    }

    @Test
    void normalizesRedundantSegments() {
        var messy = directory.resolve("nested").resolve("..").resolve("books.db");

        assertThat(DatabaseLocation.resolve(messy.toString()).path())
                .isEqualTo(directory.resolve("books.db"));
    }

    @Test
    void createsTheDataDirectoryWhenItIsAbsent() {
        var nested = directory.resolve("var").resolve("lib").resolve("book-db").resolve("books.db");
        assertThat(nested.getParent()).doesNotExist();

        var location = DatabaseLocation.resolve(nested.toString());

        assertThat(location.directory()).isDirectory();
        // The directory is prepared, but an empty database file is not: creating that is SQLite's
        // job on the first connection.
        assertThat(location.path()).doesNotExist();
    }

    @Test
    void acceptsAnExistingDatabaseFile() throws IOException {
        var file = Files.createFile(directory.resolve("books.db"));
        Files.writeString(file, "existing");

        assertThat(DatabaseLocation.resolve(file.toString()).path()).isEqualTo(file);
        // Validation must never disturb an existing database.
        assertThat(Files.readString(file)).isEqualTo("existing");
    }

    @Test
    void leavesNoProbeFileBehind() {
        var location = DatabaseLocation.resolve(directory.resolve("books.db").toString());

        assertThat(location.directory()).isEmptyDirectory();
    }

    @Test
    void rejectsAPathThatIsADirectory() {
        assertThatThrownBy(() -> DatabaseLocation.resolve(directory.toString()))
                .isInstanceOf(DatabaseConfigurationException.class)
                .hasMessageContaining("directory, not a database file");
    }

    @Test
    void rejectsAPathWhoseParentIsAFile() throws IOException {
        var file = Files.createFile(directory.resolve("not-a-directory"));

        assertThatThrownBy(() -> DatabaseLocation.resolve(file.resolve("books.db").toString()))
                .isInstanceOf(DatabaseConfigurationException.class)
                .hasMessageContaining("is not a directory");
    }

    @Test
    void rejectsAPathThatExistsButIsNotARegularFile() {
        var fifo = Path.of("/dev/null");
        assumeTrue(Files.exists(fifo) && !Files.isRegularFile(fifo), "needs a non-regular file");

        assertThatThrownBy(() -> DatabaseLocation.resolve(fifo.toString()))
                .isInstanceOf(DatabaseConfigurationException.class)
                .hasMessageContaining("not a regular file");
    }

    @Test
    void rejectsADirectoryItCannotWriteTo() throws IOException {
        var readOnly = Files.createDirectory(directory.resolve("read-only"));
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        // A process running as root ignores these permissions, so there would be nothing to assert.
        assumeTrue(!Files.isWritable(readOnly), "needs a genuinely unwritable directory");

        assertThatThrownBy(() -> DatabaseLocation.resolve(readOnly.resolve("books.db").toString()))
                .isInstanceOf(DatabaseConfigurationException.class)
                .hasMessageMatching("(?s).*(not writable|Could not create).*");
    }

    @Test
    void rejectsAnUnwritableExistingDatabaseFile() throws IOException {
        var file = Files.createFile(directory.resolve("books.db"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        assumeTrue(!Files.isWritable(file), "needs a genuinely unwritable file");

        assertThatThrownBy(() -> DatabaseLocation.resolve(file.toString()))
                .isInstanceOf(DatabaseConfigurationException.class)
                .hasMessageContaining("not writable");
    }

    @Test
    void rejectsADirectoryItCannotCreate() throws IOException {
        var readOnly = Files.createDirectory(directory.resolve("read-only"));
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        assumeTrue(!Files.isWritable(readOnly), "needs a genuinely unwritable directory");

        var unreachable = readOnly.resolve("nested").resolve("books.db");

        assertThatThrownBy(() -> DatabaseLocation.resolve(unreachable.toString()))
                .isInstanceOf(DatabaseConfigurationException.class)
                .hasMessageContaining("Could not create the data directory");
    }

    @Test
    void buildsTheJdbcUrlFromTheResolvedPath() {
        var file = directory.resolve("books.db");

        assertThat(DatabaseLocation.resolve(file.toString()).jdbcUrl())
                .isEqualTo("jdbc:sqlite:" + file)
                .startsWith("jdbc:sqlite:/");
    }

    @Test
    void requiresAPath() {
        assertThatThrownBy(() -> DatabaseLocation.resolve(null))
                .isInstanceOf(NullPointerException.class);
    }
}
