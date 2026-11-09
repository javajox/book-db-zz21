package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks who may open each view, using Vaadin's own annotation checker.
 *
 * <p>Route access in this application is expressed as annotations rather than URL patterns, which
 * means a view is published or withheld by a single line that is easy to omit and impossible to see
 * from the security configuration. This asks Vaadin the same question the running server asks, for
 * every view and for the layout they share.
 *
 * <h2>Why the layout is tested too</h2>
 *
 * <p>A view's annotation is not sufficient on its own. Vaadin checks the parent layout as well and
 * refuses to show a view that is more permissive than the frame it renders inside — reasonably, since
 * the layout would otherwise leak navigation and the signed-in operator's name into a page the
 * visitor was not allowed to see.
 *
 * <p>This was not a hypothetical. {@code MainLayout} carried no annotation at all, so every operator
 * view was denied to the authenticated operator as well as to the anonymous visitor, and the running
 * application logged "the view allows broader access than the layout, which is likely a
 * configuration error" on each attempt. Logging in succeeded and then bounced straight back to the
 * login page. The stage 14 checks all probed the anonymous case, where being denied is the correct
 * outcome and so looked like success; nothing had ever signed in.
 */
class ViewAccessTest {

    /** The views an authenticated operator manages the catalogue through. */
    private static final List<Class<? extends Component>> OPERATOR_VIEWS =
            List.of(BooksView.class, AuthorsView.class, PublishersView.class);

    private final AccessAnnotationChecker checker = new AccessAnnotationChecker();

    private static final Principal OPERATOR = () -> "operator";

    /** Only one account exists, so no role grants anything. */
    private static boolean noRoles(String role) {
        return false;
    }

    @Test
    void theSignedInOperatorCanOpenEveryOperatorView() {
        for (var view : OPERATOR_VIEWS) {
            assertThat(checker.hasAccess(view, OPERATOR, ViewAccessTest::noRoles))
                    .describedAs(view.getSimpleName())
                    .isTrue();
        }
    }

    @Test
    void theSignedInOperatorCanOpenTheLayoutEveryViewRendersInside() {
        // The regression this class exists for. A view is unreachable when its layout is stricter
        // than it is, however permissive the view's own annotation looks.
        assertThat(checker.hasAccess(MainLayout.class, OPERATOR, ViewAccessTest::noRoles)).isTrue();
    }

    @Test
    void everyOperatorViewIsRenderedInsideALayoutNoStricterThanItself() {
        for (var view : OPERATOR_VIEWS) {
            var layout = view.getAnnotation(Route.class).layout();

            // Stated as a comparison rather than as "the layout permits the operator", so that it
            // keeps holding if the access policy is ever narrowed: whatever a view allows, its
            // layout must allow at least as much.
            assertThat(checker.hasAccess(layout, OPERATOR, ViewAccessTest::noRoles))
                    .describedAs("%s is rendered inside %s, which must not be stricter",
                            view.getSimpleName(), layout.getSimpleName())
                    .isEqualTo(checker.hasAccess(view, OPERATOR, ViewAccessTest::noRoles));
        }
    }

    @Test
    void anAnonymousVisitorCanOpenNothingButTheLoginPage() {
        assertThat(checker.hasAccess(LoginView.class, null, ViewAccessTest::noRoles))
                .describedAs("the login page must be reachable without signing in")
                .isTrue();

        for (var view : OPERATOR_VIEWS) {
            assertThat(checker.hasAccess(view, null, ViewAccessTest::noRoles))
                    .describedAs(view.getSimpleName())
                    .isFalse();
        }
        assertThat(checker.hasAccess(MainLayout.class, null, ViewAccessTest::noRoles))
                .describedAs("the layout leaks navigation and the operator's name, so it must not "
                        + "render for a visitor who has not signed in")
                .isFalse();
    }
}
