package org.wintermutecore.bookdbzz21.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates an API read by its bearer token.
 *
 * <p>The configured secret is the token's SHA-256 digest, so the presented token is digested and the
 * two digests are compared. Nothing that could be replayed is held in configuration, and the
 * comparison uses {@link MessageDigest#isEqual}, which does not return early on the first differing
 * byte — an ordinary {@code equals} would leak how much of a guess was correct through how long the
 * comparison took.
 *
 * <p>A request without a usable token is simply left unauthenticated. Rejecting it here would
 * duplicate the decision; the filter chain's entry point owns the response, so every refusal looks
 * the same whether the token was absent, malformed, or wrong.
 */
class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final byte[] expectedDigest;

    ApiTokenAuthenticationFilter(String expectedTokenDigest) {
        this.expectedDigest = expectedTokenDigest.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var presented = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (presented != null && presented.startsWith(BEARER)) {
            var token = presented.substring(BEARER.length()).trim();
            if (!token.isEmpty() && matches(token)) {
                // A fixed principal: the token identifies an integration, not a person, and the
                // API grants only reads.
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "api-client", null,
                                AuthorityUtils.createAuthorityList("ROLE_API")));
            }
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String token) {
        var presentedDigest = CredentialProvisioning.sha256Hex(token)
                .getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(presentedDigest, expectedDigest);
    }
}
