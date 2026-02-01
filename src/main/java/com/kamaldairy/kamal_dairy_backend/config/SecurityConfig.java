package com.kamaldairy.kamal_dairy_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // PUBLIC APIs
                        .requestMatchers(
                                "/error",
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/products/**",
                                "/api/trending-products/**"
                        ).permitAll()

                        // EVERYTHING ELSE NEEDS TOKEN
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
