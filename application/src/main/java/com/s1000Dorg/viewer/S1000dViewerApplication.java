package com.s1000Dorg.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = { LdapAutoConfiguration.class })
@ConfigurationPropertiesScan
public class S1000dViewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(S1000dViewerApplication.class, args);
    }
}
