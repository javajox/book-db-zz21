package org.wintermutecore.bookdbzz21.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Produces the credentials the application refuses to start without.
 *
 * <p>Runs before Spring does, as {@code java -jar book-db-zz21.jar provision-credentials}, so the
 * tool is available on the deployment host from the same artifact rather than needing the source
 * tree or a separate script.
 *
 * <p>The password is read from the console rather than taken as an argument. A command-line
 * argument is visible to every process listing on the host and lands in the shell history, which
 * would make the provisioning step itself the weakest part of the arrangement.
 */
public final class CredentialProvisioning {

    /** The command-line argument that selects this tool. */
    public static final String COMMAND = "provision-credentials";

    /**
     * Bytes of randomness in a generated API token.
     *
     * <p>The specification requires at least 32. The token is the only thing protecting the API, it
     * is never typed by a person, and it is compared by digest, so there is no reason to be
     * parsimonious.
     */
    public static final int TOKEN_BYTES = 32;

    private CredentialProvisioning() {
    }

    /**
     * Runs the tool.
     *
     * @param arguments the command-line arguments, the first being {@link #COMMAND}
     * @return the process exit code
     */
    public static int run(String[] arguments) {
        var username = arguments.length > 1 ? arguments[1].trim() : prompt("Operator username: ");
        if (username == null || username.isBlank()) {
            System.err.println("A username is required.");
            return 2;
        }

        var password = readPassword();
        if (password == null || password.isBlank()) {
            System.err.println("A password is required.");
            return 2;
        }

        var token = newApiToken();
        System.out.println();
        System.out.println("Add these to the deployment configuration, outside the JAR and outside");
        System.out.println("version control:");
        System.out.println();
        System.out.println("  bookdb.security.operator-username=" + username);
        System.out.println("  bookdb.security.operator-password-hash=" + hashPassword(password));
        System.out.println("  bookdb.security.api-token-digest=" + sha256Hex(token));
        System.out.println();
        System.out.println("Give this API token to the integration client through your credential");
        System.out.println("channel. It is shown once and is not recoverable from the digest above:");
        System.out.println();
        System.out.println("  " + token);
        System.out.println();
        System.out.println("Clients send it as:  Authorization: Bearer " + "<token>");
        return 0;
    }

    /** Hashes a password for {@code bookdb.security.operator-password-hash}. */
    public static String hashPassword(String password) {
        return new BCryptPasswordEncoder().encode(password);
    }

    /**
     * Generates an API token.
     *
     * <p>URL-safe Base64 without padding, so it survives a copy and paste through a shell, a
     * configuration file, or an HTTP header without needing to be escaped.
     */
    public static String newApiToken() {
        var bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The SHA-256 digest of a token, in lowercase hexadecimal. */
    public static String sha256Hex(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String readPassword() {
        var console = System.console();
        if (console != null) {
            // Not echoed, and never stored in a String the caller could leak by accident.
            var typed = console.readPassword("Operator password: ");
            return typed == null ? null : new String(typed);
        }
        // No console, as when piped from a script. Reading the line still beats accepting the
        // password as an argument, which every process listing on the host can see.
        System.out.print("Operator password (input will be visible): ");
        System.out.flush();
        return readLine();
    }

    private static String prompt(String label) {
        System.out.print(label);
        System.out.flush();
        return readLine();
    }

    private static String readLine() {
        try {
            var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
