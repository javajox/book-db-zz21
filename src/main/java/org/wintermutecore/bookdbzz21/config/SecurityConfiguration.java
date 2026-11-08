package org.wintermutecore.bookdbzz21.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.wintermutecore.bookdbzz21.ui.LoginView;

import java.nio.charset.StandardCharsets;

/**
 * Enforces the access policy: a form login for the operator, a bearer token for the API.
 *
 * <h2>Two chains, because the two audiences want opposite things</h2>
 *
 * <p>An operator at a browser wants a session and a login page to be redirected to. An integration
 * reading JSON wants neither: a redirect to HTML would be indistinguishable from success to anything
 * checking only the status code, and a session cookie would be pointless for a stateless reader.
 * Separating the chains is what lets each get the right answer, and the API chain is ordered first
 * so its two paths are matched before Vaadin's catch-all.
 *
 * <h2>The API token does not open the GUI</h2>
 *
 * <p>The token authenticates only the two read paths. It carries no authority over the management
 * interface, so leaking it to an integration partner does not hand them the operator's screens.
 *
 * <h2>Credentials come from outside</h2>
 *
 * <p>There is no built-in account and no fallback. {@link SecurityProperties} fails startup when the
 * credentials are missing or the wrong shape, so a deployment cannot come up unsecured.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    /** The paths the API token authenticates, and nothing else. */
    static final String[] API_PATHS = {"/books-brief", "/books-full"};

    /**
     * The body returned for an unauthenticated API read.
     *
     * <p>Written as a literal because both values are fixed: there is nothing to escape, and
     * nothing from the request can find its way into the response. The keys follow the same
     * hyphenated convention as every other API error.
     */
    private static final String UNAUTHORIZED_BODY = """
            {"error-code":"unauthorized",\
            "message":"A valid API token is required. Send it as an Authorization: Bearer header."}\
            """;

    /**
     * The API chain: stateless, token-authenticated, and always answered in JSON.
     *
     * <p>CSRF protection is off here and nowhere else. It defends a browser session against a
     * forged request, and this chain has no session and no cookie to forge with — the only
     * credential is a header a browser will never attach on its own. The GUI chain keeps Vaadin's
     * CSRF handling intact.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, SecurityProperties credentials) throws Exception {
        http.securityMatcher(API_PATHS)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new ApiTokenAuthenticationFilter(credentials.apiTokenDigest()),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(jsonUnauthorizedEntryPoint()));
        return http.build();
    }

    /**
     * The GUI chain: Vaadin's own configurer, with the login view it should redirect to.
     *
     * <p>Vaadin needs more than a path matcher: its internal request handling, framework resources
     * and websocket traffic all have to stay reachable while every route is access-checked. That is
     * what {@link VaadinSecurityConfigurer} arranges, and why route access is expressed as
     * annotations on the views rather than as URL patterns here.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain guiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(),
                vaadin -> vaadin.loginView(LoginView.class));
        return http.build();
    }

    /** The single provisioned operator account, built from external configuration. */
    @Bean
    public UserDetailsService operatorDetailsService(
            SecurityProperties credentials, PasswordEncoder passwordEncoder) {
        var operator = User.withUsername(credentials.operatorUsername())
                // Already a BCrypt hash: SecurityProperties rejects anything that is not.
                .password(credentials.operatorPasswordHash())
                .roles("OPERATOR")
                .build();
        return new InMemoryUserDetailsManager(operator);
    }

    /**
     * BCrypt, matching how the provisioning tool hashes a password.
     *
     * <p>In memory rather than a user table because there is exactly one account and no
     * registration; a database table would add a schema and a migration to store a single row that
     * the deployment configuration already holds.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static AuthenticationEntryPoint jsonUnauthorizedEntryPoint() {
        return (request, response, failure) -> {
            // Never a redirect: an integration following one would receive the login page with a
            // 200 and could not tell it from data.
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(UNAUTHORIZED_BODY);
        };
    }
}
