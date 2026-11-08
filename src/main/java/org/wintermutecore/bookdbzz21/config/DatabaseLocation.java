package org.wintermutecore.bookdbzz21.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The resolved, validated location of the SQLite database file.
 *
 * <p>Resolution happens once at startup and is checked thoroughly, because every later failure mode
 * is worse. A path that turns out to be unusable after the application is serving requests looks
 * like data loss to an operator; a path that quietly resolves somewhere unexpected looks like data
 * loss twice, once when records go missing and again when they reappear.
 *
 * <p>SQLite needs more than the database file: in WAL mode it also creates {@code -wal} and
 * {@code -shm} companions beside it, and temporary files during some operations. That is why the
 * writability of the <em>parent directory</em> is checked, not just the file.
 */
public final class DatabaseLocation {

    private final Path path;

    private DatabaseLocation(Path path) {
        this.path = path;
    }

    /**
     * Resolves and validates a configured database path.
     *
     * <p>A relative path resolves against the process working directory, which is why production
     * deployments should configure an absolute one: otherwise starting the application from a
     * different directory silently selects a different database.
     *
     * @param configuredPath the value of {@code bookdb.database.path}
     * @return the validated location
     * @throws DatabaseConfigurationException when the path cannot be used
     */
    public static DatabaseLocation resolve(String configuredPath) {
        Objects.requireNonNull(configuredPath, "configuredPath");

        Path resolved;
        try {
            resolved = Path.of(configuredPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new DatabaseConfigurationException(
                    "bookdb.database.path is not a usable file path: " + configuredPath, e);
        }

        if (Files.isDirectory(resolved)) {
            throw new DatabaseConfigurationException(
                    "bookdb.database.path points at a directory, not a database file: " + resolved);
        }

        var directory = resolved.getParent();
        if (directory == null) {
            throw new DatabaseConfigurationException(
                    "bookdb.database.path has no parent directory: " + resolved);
        }

        createDirectory(directory, resolved);
        // The target is inspected before the directory is probed, so a path that exists but is the
        // wrong kind of thing is reported as such. Probing first would blame the directory for
        // something the path itself got wrong, and would write a probe file to find that out.
        requireUsableFile(resolved);
        requireWritableDirectory(directory, resolved);

        return new DatabaseLocation(resolved);
    }

    private static void createDirectory(Path directory, Path resolved) {
        if (Files.isDirectory(directory)) {
            return;
        }
        if (Files.exists(directory)) {
            throw new DatabaseConfigurationException(
                    "The parent of bookdb.database.path exists but is not a directory: " + directory);
        }
        try {
            // Created when absent and permissions allow, so a first run against the default
            // ./data/books.db works without the operator preparing the directory by hand.
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new DatabaseConfigurationException(
                    "Could not create the data directory " + directory
                            + " for bookdb.database.path " + resolved, e);
        }
    }

    private static void requireWritableDirectory(Path directory, Path resolved) {
        if (!Files.isWritable(directory)) {
            throw new DatabaseConfigurationException(
                    "The data directory " + directory + " is not writable, so SQLite cannot create "
                            + "the journal and temporary files it needs beside " + resolved);
        }
        // isWritable can disagree with reality on some filesystems, so the ability to create a
        // companion file is confirmed by actually creating one and removing it again.
        var probe = directory.resolve(resolved.getFileName() + ".startup-probe");
        try {
            Files.deleteIfExists(probe);
            Files.createFile(probe);
        } catch (IOException e) {
            throw new DatabaseConfigurationException(
                    "Could not create a file in the data directory " + directory
                            + ", so SQLite cannot create the companion files it needs beside "
                            + resolved, e);
        } finally {
            try {
                Files.deleteIfExists(probe);
            } catch (IOException e) {
                // Leaving the probe behind is untidy but harmless, and must not mask a real
                // configuration problem or stop an otherwise healthy startup.
            }
        }
    }

    private static void requireUsableFile(Path resolved) {
        if (!Files.exists(resolved)) {
            return;
        }
        if (!Files.isRegularFile(resolved)) {
            throw new DatabaseConfigurationException(
                    "bookdb.database.path exists but is not a regular file: " + resolved);
        }
        if (!Files.isWritable(resolved)) {
            throw new DatabaseConfigurationException(
                    "The database file " + resolved + " exists but is not writable");
        }
        if (!Files.isReadable(resolved)) {
            throw new DatabaseConfigurationException(
                    "The database file " + resolved + " exists but is not readable");
        }
    }

    /** The absolute, normalized path of the database file. */
    public Path path() {
        return path;
    }

    /** The directory holding the database and its companion files. */
    public Path directory() {
        return path.getParent();
    }

    /** The JDBC URL addressing this database. */
    public String jdbcUrl() {
        return "jdbc:sqlite:" + path;
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
