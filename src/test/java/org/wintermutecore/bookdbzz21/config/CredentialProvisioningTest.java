package org.wintermutecore.bookdbzz21.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the credentials the provisioning tool produces. */
class CredentialProvisioningTest {

    @Test
    void hashesAPasswordSoTheOriginalCannotBeRecovered() {
        var hash = CredentialProvisioning.hashPassword("correct-horse-battery-staple");

        assertThat(hash).doesNotContain("correct-horse");
        // Verifiable by the same encoder the application authenticates with.
        assertThat(new BCryptPasswordEncoder().matches("correct-horse-battery-staple", hash))
                .isTrue();
        assertThat(new BCryptPasswordEncoder().matches("wrong-password", hash)).isFalse();
    }

    @Test
    void producesADifferentHashEachTimeForTheSamePassword() {
        var first = CredentialProvisioning.hashPassword("same-password");
        var second = CredentialProvisioning.hashPassword("same-password");

        // BCrypt salts each hash, so two deployments of the same password do not reveal that fact
        // by sharing a value.
        assertThat(first).isNotEqualTo(second);
        assertThat(new BCryptPasswordEncoder().matches("same-password", second)).isTrue();
    }

    @Test
    void producesAHashTheApplicationWillAccept() {
        // The shape check in SecurityProperties and the tool must agree, or provisioning would
        // hand over a value the application then refuses.
        var properties = new SecurityProperties(
                "operator",
                CredentialProvisioning.hashPassword("a-password"),
                CredentialProvisioning.sha256Hex("a-token"));

        assertThat(properties.operatorPasswordHash()).startsWith("$2");
    }

    @Test
    void generatesTokensWithAtLeastTheRequiredRandomness() {
        var token = CredentialProvisioning.newApiToken();

        // The specification requires at least 32 bytes of randomness.
        assertThat(CredentialProvisioning.TOKEN_BYTES).isGreaterThanOrEqualTo(32);
        assertThat(Base64.getUrlDecoder().decode(token))
                .hasSizeGreaterThanOrEqualTo(32);
        // URL-safe and unpadded, so it survives a shell, a properties file and an HTTP header
        // without escaping.
        assertThat(token).matches("[A-Za-z0-9_-]+").doesNotContain("=");
    }

    @Test
    void generatesADistinctTokenEveryTime() {
        var tokens = new HashSet<String>();
        for (var attempt = 0; attempt < 500; attempt++) {
            tokens.add(CredentialProvisioning.newApiToken());
        }

        assertThat(tokens).hasSize(500);
    }

    @Test
    void digestsATokenAsLowercaseHexadecimal() {
        var digest = CredentialProvisioning.sha256Hex("a-token");

        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
        // Deterministic, which is what lets the application compare a presented token against a
        // stored digest.
        assertThat(CredentialProvisioning.sha256Hex("a-token")).isEqualTo(digest);
        assertThat(CredentialProvisioning.sha256Hex("another-token")).isNotEqualTo(digest);
    }

    @Test
    void digestsMatchAKnownSha256Value() {
        // Checked against the published SHA-256 of the empty string rather than against this
        // implementation, so a change in how the digest is computed cannot pass unnoticed.
        assertThat(CredentialProvisioning.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void aDigestRevealsNothingUsableAboutItsToken() {
        var token = CredentialProvisioning.newApiToken();
        var digest = CredentialProvisioning.sha256Hex(token);

        assertThat(digest).doesNotContain(token);
        // Different tokens give unrelated digests: the comparison the filter performs is on the
        // digest, and MessageDigest.isEqual over equal-length values is what keeps it constant-time.
        var other = CredentialProvisioning.sha256Hex(CredentialProvisioning.newApiToken());
        assertThat(MessageDigest.isEqual(digest.getBytes(), other.getBytes())).isFalse();
    }
}
