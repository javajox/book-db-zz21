package org.wintermutecore.bookdbzz21;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.wintermutecore.bookdbzz21.config.CredentialProvisioning;

@SpringBootApplication
public class BookDbZz21Application {

    public static void main(String[] args) {
        // The provisioning tool runs before Spring, because its whole purpose is to produce the
        // credentials the application refuses to start without. Starting the context first would
        // mean the tool could only ever run on an already-configured deployment.
        if (args.length > 0 && CredentialProvisioning.COMMAND.equals(args[0])) {
            System.exit(CredentialProvisioning.run(args));
        }
        SpringApplication.run(BookDbZz21Application.class, args);
    }
}
