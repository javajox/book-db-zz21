package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * The frame every operator view sits in, carrying navigation to the three things there are to
 * manage.
 *
 * <p>Books, authors and publishers are separate views because authors and publishers are reusable
 * records with their own lifecycle: an operator adds an author once and then selects it for many
 * books, so managing them has to be possible without opening a book at all.
 *
 * <h2>Why the layout is access-annotated</h2>
 *
 * <p>{@code @PermitAll} is required here, not merely tidy. Vaadin checks the parent layout's access
 * rules as well as the view's and refuses to show a view that is more permissive than the frame it
 * renders inside — reasonably, since this layout carries the navigation and the signed-in operator's
 * name. Without the annotation the layout denies everyone, so every view it hosts became unreachable
 * for the authenticated operator too, and a successful login bounced straight back to the login
 * page. It is only ever as permissive as the views it frames.
 */
@PermitAll
public class MainLayout extends AppLayout {

    private final AuthenticationContext authentication;

    public MainLayout(AuthenticationContext authentication) {
        this.authentication = authentication;

        var title = new H1("Book DB zz21");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.MEDIUM);

        addToNavbar(new DrawerToggle(), title, signedInAs(), signOut());
        addToDrawer(navigation());
    }

    /**
     * Who is signed in.
     *
     * <p>Shown because the deployment has exactly one operator account shared by whoever is at the
     * machine: seeing the name is how someone confirms they are working in the right session rather
     * than one a colleague left open.
     */
    private Span signedInAs() {
        var name = new Span(authentication.getPrincipalName().orElse(""));
        name.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Margin.MEDIUM,
                LumoUtility.TextColor.SECONDARY);
        return name;
    }

    /**
     * Ends the session.
     *
     * <p>Delegates to {@link AuthenticationContext#logout()} rather than clearing anything here, so
     * Spring Security's logout handling and Vaadin's session invalidation both run — a half-ended
     * session would leave the browser holding a usable one.
     */
    private Button signOut() {
        var logout = new Button("Sign out", event -> authentication.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return logout;
    }

    private VerticalLayout navigation() {
        var navigation = new SideNav();
        navigation.addItem(new SideNavItem("Books", BooksView.class));
        navigation.addItem(new SideNavItem("Authors", AuthorsView.class));
        navigation.addItem(new SideNavItem("Publishers", PublishersView.class));

        var drawer = new VerticalLayout(navigation);
        drawer.setPadding(false);
        drawer.setSpacing(false);
        return drawer;
    }
}
