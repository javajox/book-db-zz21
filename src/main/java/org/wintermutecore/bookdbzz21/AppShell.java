package org.wintermutecore.bookdbzz21;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.lumo.Lumo;

@PWA(name = "Book DB zz21", shortName = "zz21")
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("styles.css")
public class AppShell implements AppShellConfigurator {
}
