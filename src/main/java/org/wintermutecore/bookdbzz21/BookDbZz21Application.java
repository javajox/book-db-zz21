package org.wintermutecore.bookdbzz21;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BookDbZz21Application implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(BookDbZz21Application.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println(
                "Book DB zz21 started with Java " + Runtime.version()
        );
    }
}
