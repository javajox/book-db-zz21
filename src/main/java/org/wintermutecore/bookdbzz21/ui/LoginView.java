package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Where an unauthenticated operator is sent.
 *
 * <p>The only route reachable without signing in, which is why it is the one place
 * {@link AnonymousAllowed} appears. Every other view carries {@code @PermitAll}, and a route with
 * no annotation at all is denied — so forgetting to annotate a new view fails closed rather than
 * publishing it.
 *
 * <p>The form posts to Spring Security's own processing URL rather than handling credentials in
 * Java. Authentication stays entirely inside the filter chain, so this view never touches a
 * password.
 */
@Route(value = "login")
@PageTitle("Sign in")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        var messages = LoginI18n.createDefault();
        messages.getForm().setTitle("Book DB zz21");
        messages.getErrorMessage().setTitle("Could not sign in");
        // Deliberately vague: saying which half was wrong tells someone probing whether a username
        // exists.
        messages.getErrorMessage().setMessage("Check the username and password and try again.");
        login.setI18n(messages);
        login.setForgotPasswordButtonVisible(false);
        login.setAction("login");

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        add(login);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security redirects back here with ?error after a failed attempt.
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }

    /** The login form, so a test can confirm its configuration without a browser. */
    LoginForm loginForm() {
        return login;
    }
}
