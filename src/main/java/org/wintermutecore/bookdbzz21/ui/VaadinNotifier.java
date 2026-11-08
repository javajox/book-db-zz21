package org.wintermutecore.bookdbzz21.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.springframework.stereotype.Component;

/** Shows messages as Vaadin notifications. */
@Component
public class VaadinNotifier implements Notifier {

    private static final int SUCCESS_DURATION_MS = 3_000;

    /**
     * Errors stay until dismissed.
     *
     * <p>A rejected save usually needs the operator to read why and then do something about it, and
     * a message that disappears on its own is one they may never have seen.
     */
    private static final int ERROR_DURATION_MS = 0;

    @Override
    public void success(String message) {
        var notification = Notification.show(message, SUCCESS_DURATION_MS, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    @Override
    public void error(String message) {
        var notification = Notification.show(message, ERROR_DURATION_MS, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
