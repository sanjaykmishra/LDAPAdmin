package com.ldapadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LDAPAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(LDAPAdminApplication.class, args);
    }
}
