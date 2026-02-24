package com.ldapadmin;

import com.ldapadmin.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(AppProperties.class)
public class LDAPAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(LDAPAdminApplication.class, args);
    }
}
