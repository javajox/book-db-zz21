package org.wintermutecore.bookdbzz21.config;

/**
 * The configured database cannot be used, so the application must not start.
 *
 * <p>Deliberately not part of the {@code ApplicationException} hierarchy. Those errors describe a
 * request that failed and carry a message safe to show a user; this one describes a deployment that
 * is not viable and belongs in a startup log, where naming the exact path is the whole point.
 *
 * <p>Every failure reported through this type stops startup. The alternative — carrying on against
 * some other database — is the one outcome that must never happen, because an operator would enter
 * records into a file nobody intended and a later fix would appear to lose them.
 */
public class DatabaseConfigurationException extends RuntimeException {

    public DatabaseConfigurationException(String message) {
        super(message);
    }

    public DatabaseConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
