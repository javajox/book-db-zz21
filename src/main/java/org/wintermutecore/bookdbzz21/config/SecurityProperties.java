package org.wintermutecore.bookdbzz21.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * The externalized credentials for the operator login and the API token.
 *
 * <h2>There is no default, and no insecure fallback</h2>
 *
 * <p>Every value is required, and a missing or malformed one fails startup. The alternative — a
 * built-in account, or starting unsecured when configuration is absent — is the failure this design
 * exists to prevent: a deployment that looks finished while anyone who can reach the port has full
 * management access. A deployment that refuses to start is a problem someone fixes in a minute; one
 * that starts unsecured is a problem nobody notices.
 *
 * <h2>Only derived secrets are stored</h2>
 *
 * <p>The password is held as a BCrypt hash and the API token as a SHA-256 digest, so the
 * configuration file never contains a credential that could be replayed. Both are checked for shape
 * as well as presence, because the realistic mistake is not omitting them — it is pasting the plain
 * password or the raw token into the property and believing it was secured.
 *
 * @param operatorUsername the single provisioned operator account
 * @param operatorPasswordHash that account's BCrypt password hash
 * @param apiTokenDigest the SHA-256 digest, in lowercase hexadecimal, of the API bearer token
 */
@ConfigurationProperties("bookdb.security")
public record SecurityProperties(
        String operatorUsername, String operatorPasswordHash, String apiTokenDigest) {

    /** How to produce the values, quoted in every failure message so the fix is at hand. */
    public static final String PROVISIONING_HINT =
            "Generate them with: java -jar book-db-zz21.jar provision-credentials";

    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    public SecurityProperties {
        if (isBlank(operatorUsername)) {
            throw new IllegalArgumentException(
                    "bookdb.security.operator-username is required; the application will not start "
                            + "without an operator account. " + PROVISIONING_HINT);
        }
        if (isBlank(operatorPasswordHash)) {
            throw new IllegalArgumentException(
                    "bookdb.security.operator-password-hash is required. " + PROVISIONING_HINT);
        }
        if (!BCRYPT.matcher(operatorPasswordHash).matches()) {
            // Almost certainly a plain password pasted into the property, which would never match
            // any login and would also put the real password in a configuration file.
            throw new IllegalArgumentException(
                    "bookdb.security.operator-password-hash must be a BCrypt hash, which begins "
                            + "with $2a$, $2b$ or $2y$ — not the password itself. "
                            + PROVISIONING_HINT);
        }
        if (isBlank(apiTokenDigest)) {
            throw new IllegalArgumentException(
                    "bookdb.security.api-token-digest is required. " + PROVISIONING_HINT);
        }
        operatorUsername = operatorUsername.trim();
        apiTokenDigest = apiTokenDigest.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SHA_256_HEX.matcher(apiTokenDigest).matches()) {
            // Most likely the raw token rather than its digest. Storing the token itself would mean
            // a readable copy of a live credential sitting in configuration.
            throw new IllegalArgumentException(
                    "bookdb.security.api-token-digest must be the token's SHA-256 digest as 64 "
                            + "hexadecimal characters — not the token itself. " + PROVISIONING_HINT);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
