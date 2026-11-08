/**
 * Runtime configuration.
 *
 * <p>Holds the externalized {@code bookdb.*} settings and the beans built from them: the SQLite data
 * source, the transaction manager, and migration execution at startup.
 *
 * <p>Boundary rules:
 *
 * <ul>
 *   <li>Configuration is supplied through Spring Boot's external configuration support, so a
 *       configuration file, environment variables, and command-line arguments all work.
 *   <li>Unusable settings fail startup with a clear diagnostic. The application never silently
 *       falls back to a different database.
 *   <li>Foreign-key enforcement and the bounded busy timeout are applied to every connection,
 *       including replacement connections created later in the process lifetime.
 * </ul>
 */
package org.wintermutecore.bookdbzz21.config;
