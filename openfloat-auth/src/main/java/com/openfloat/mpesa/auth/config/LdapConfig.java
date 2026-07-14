package com.openfloat.mpesa.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Slf4j
@Configuration
public class LdapConfig {

    @Value("${openfloat.ldap.enabled:false}")
    private boolean ldapEnabled;

    @Value("${openfloat.ldap.url:ldap://localhost:389}")
    private String ldapUrl;

    @Value("${openfloat.ldap.base:dc=example,dc=com}")
    private String ldapBase;

    @Value("${openfloat.ldap.user-dn:}")
    private String ldapUserDn;

    @Value("${openfloat.ldap.password:}")
    private String ldapPassword;

    @Bean
    public LdapContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        if (ldapEnabled) {
            log.info("LDAP is enabled. Configuring LDAP ContextSource with URL: {}, Base: {}", ldapUrl, ldapBase);
            contextSource.setUrl(ldapUrl);
            contextSource.setBase(ldapBase);
            if (ldapUserDn != null && !ldapUserDn.isBlank()) {
                contextSource.setUserDn(ldapUserDn);
                contextSource.setPassword(ldapPassword);
            }
        } else {
            log.info("LDAP is disabled. Configuring dummy LDAP ContextSource.");
            // Dummy values to prevent spring boot load failure
            contextSource.setUrl("ldap://localhost:389");
            contextSource.setBase("dc=example,dc=com");
        }
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }
}
