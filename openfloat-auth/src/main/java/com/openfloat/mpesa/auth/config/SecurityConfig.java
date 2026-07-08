package com.openfloat.mpesa.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    @Value("${openfloat.ldap.enabled:false}")
    private boolean ldapEnabled;

    @Value("${openfloat.ldap.domain:}")
    private String ldapDomain;

    @Value("${openfloat.ldap.url:}")
    private String ldapUrl;

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/error", "/actuator/**", "/openapi.json").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults()); // Form login handles the redirect from the authorization server

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        List<org.springframework.security.authentication.AuthenticationProvider> providers = new ArrayList<>();
        
        // 1. DB Dao Authentication
        DaoAuthenticationProvider dbProvider = new DaoAuthenticationProvider();
        dbProvider.setUserDetailsService(userDetailsService);
        dbProvider.setPasswordEncoder(passwordEncoder);
        providers.add(dbProvider);

        // 2. Active Directory / LDAP Authentication (Optional)
        if (ldapEnabled && ldapDomain != null && !ldapDomain.isBlank()) {
            ActiveDirectoryLdapAuthenticationProvider adProvider = 
                    new ActiveDirectoryLdapAuthenticationProvider(ldapDomain, ldapUrl);
            adProvider.setConvertSubErrorCodesToExceptions(true);
            providers.add(adProvider);
        }

        return new ProviderManager(providers);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
