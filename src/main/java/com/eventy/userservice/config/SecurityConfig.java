package com.eventy.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Import est déjà présent
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Import est déjà présent
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 1. Routes publiques spécifiques (Permis à tous)
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        
                        // 2. NOUVEAU : Autoriser POST /api/users pour l'inscription
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

                        // 3. NOUVEAU (LA CORRECTION) : Autoriser l'endpoint de sync interne
                        // Cet endpoint est protégé par un secret dans le contrôleur, pas par JWT.
                        // Il DOIT être défini AVANT la règle générale /api/users/**
                        .requestMatchers(HttpMethod.POST, "/api/users/internal/keycloak-sync").permitAll()

                        // 4. Routes Admin (Réservé à l'ADMIN)
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")

                        // 5. Routes Authentifiées (Les /me et /users/{id} si elles existaient)
                        // Cette règle capture toutes les autres routes /api/users/**
                        .requestMatchers("/api/users/**").authenticated()
                        
                        // 6. Par défaut, tout le reste est public (bonne pratique, mais attention à l'ordre)
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}