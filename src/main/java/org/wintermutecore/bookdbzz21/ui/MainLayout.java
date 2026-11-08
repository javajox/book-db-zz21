package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * The frame every operator view sits in, carrying navigation to the three things there are to
 * manage.
 *
 * <p>Books, authors and publishers are separate views because authors and publishers are reusable
 * records with their own lifecycle: an operator adds an author once and then selects it for many
 * books, so managing them has to be possible without opening a book at all.
 */
public class MainLayout extends AppLayout {

    public MainLayout() {
        var title = new H1("Book DB zz21");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.MEDIUM);

        addToNavbar(new DrawerToggle(), title);
        addToDrawer(navigation());
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
