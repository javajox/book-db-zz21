package org.wintermutecore.bookdbzz21.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the application cannot start without usable credentials.
 *
 * <p>This is the stage's most important guarantee, and the only one whose failure would be
 * invisible: a deployment that starts unsecured looks exactly like a deployment that works. Every
 * case below is a way someone could end up with management access open to anyone who can reach the
 * port, and each has to stop startup instead.
 */
class SecurityPropertiesTest {

    private static final String VALID_HASH =
            CredentialProvisioning.hashPassword("a-real-password");
    private static final String VALID_DIGEST =
            CredentialProvisioning.sha256Hex("a-real-token");

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    @Test
    void acceptsAFullyProvisionedDeployment() {
        runner.withPropertyValues(
                        "bookdb.security.operator-username=operator",
                        "bookdb.security.operator-password-hash=" + VALID_HASH,
                        "bookdb.security.api-token-digest=" + VALID_DIGEST)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var credentials = context.getBean(SecurityProperties.class);
                    assertThat(credentials.operatorUsername()).isEqualTo("operator");
                    assertThat(credentials.operatorPasswordHash()).isEqualTo(VALID_HASH);
                    assertThat(credentials.apiTokenDigest()).isEqualTo(VALID_DIGEST);
                });
    }

    @Test
    void refusesToStartWithNoCredentialsAtAll() {
        // The case that matters most: an operator deploys the JAR and forgets the configuration.
        // Starting here would mean anonymous management access.
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause()
                    .hasMessageContaining("operator-username is required")
                    .hasMessageContaining("provision-credentials");
        });
    }

    @Test
    void refusesToStartWithEachCredentialMissingInTurn() {
        runner.withPropertyValues(
                        "bookdb.security.operator-password-hash=" + VALID_HASH,
                        "bookdb.security.api-token-digest=" + VALID_DIGEST)
                .run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues(
                        "bookdb.security.operator-username=operator",
                        "bookdb.security.api-token-digest=" + VALID_DIGEST)
                .run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues(
                        "bookdb.security.operator-username=operator",
                        "bookdb.security.operator-password-hash=" + VALID_HASH)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesAPlainPasswordPastedWhereTheHashBelongs() {
        // The realistic mistake. Accepting it would store the real password in a configuration file
        // and still never match any login, so the deployment would be both insecure and broken.
        runner.withPropertyValues(
                        "bookdb.security.operator-username=operator",
                        "bookdb.security.operator-password-hash=correct-horse-battery-staple",
                        "bookdb.security.api-token-digest=" + VALID_DIGEST)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause()
                            .hasMessageContaining("must be a BCrypt hash")
                            .hasMessageContaining("not the password itself");
                });
    }

    @Test
    void refusesARawTokenPastedWhereTheDigestBelongs() {
        // Equally realistic, and it would leave a live credential readable in configuration.
        runner.withPropertyValues(
                        "bookdb.security.operator-username=operator",
                        "bookdb.security.operator-password-hash=" + VALID_HASH,
                        "bookdb.security.api-token-digest=" + CredentialProvisioning.newApiToken())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause()
                            .hasMessageContaining("SHA-256 digest")
                            .hasMessageContaining("not the token itself");
                });
    }

    @Test
    void refusesABlankValueJustAsItRefusesAMissingOne() {
        runner.withPropertyValues(
                        "bookdb.security.operator-username=   ",
                        "bookdb.security.operator-password-hash=" + VALID_HASH,
                        "bookdb.security.api-token-digest=" + VALID_DIGEST)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsAnUppercaseDigestByNormalizingIt() {
        // Hexadecimal is case-insensitive, and a digest copied from another tool may well arrive
        // uppercase; rejecting that would be pedantry rather than security.
        runner.withPropertyValues(
                        "bookdb.security.operator-username=operator",
                        "bookdb.security.operator-password-hash=" + VALID_HASH,
                        "bookdb.security.api-token-digest=" + VALID_DIGEST.toUpperCase())
                .run(context -> assertThat(context.getBean(SecurityProperties.class)
                        .apiTokenDigest()).isEqualTo(VALID_DIGEST));
    }

    @Test
    void refusesToBeConstructedInAnUnusableState() {
        assertThatThrownBy(() -> new SecurityProperties(null, VALID_HASH, VALID_DIGEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties("operator", null, VALID_DIGEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties("operator", VALID_HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropertiesOnly {
    }
}
