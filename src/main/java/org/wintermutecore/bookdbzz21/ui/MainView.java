package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("")
public class MainView extends VerticalLayout {

    public MainView() {
        add(new H1("Book DB zz21"));
        add(new Paragraph("Vaadin UI is ready."));
        addClassName("centered-content");
    }
}
