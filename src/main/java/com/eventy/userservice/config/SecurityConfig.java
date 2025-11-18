package com.eventy.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // N'oubliez pas l'import
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // N'oubliez pas l'import
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
                        
                        // NOUVEAU : Autoriser POST /api/users pour l'inscription
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

                        // 2. Routes Admin (Réservé à l'ADMIN)
                        // Note : Le pattern doit être ajusté si votre UserController a /api/users/admin
                        // La règle .requestMatchers("/api/admin/**") n'est pas utilisée par votre UserController.
                        // Je me base sur votre UserController qui utilise /api/users/admin/**
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")

                        // 3. Routes Authentifiées (Les /me et /users/{id} si elles existaient)
                        // Cette règle capture toutes les autres routes /api/users/** (y compris /me)
                        .requestMatchers("/api/users/**").authenticated()
                        
                        // 4. Par défaut, tout le reste est public (bonne pratique, mais attention à l'ordre)
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}