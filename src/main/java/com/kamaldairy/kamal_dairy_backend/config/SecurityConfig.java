package com.kamaldairy.kamal_dairy_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    // we have to make password encoder (for hashing the password)
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    // security rules basically who can access what, who will be blocked / who will be not blocked
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests
                (auth -> auth.requestMatchers
                                ("/api/products/**" , "/api/trending-products/**", "/auth/**").permitAll().anyRequest().authenticated()
                );
        return http.build();
    }
}
