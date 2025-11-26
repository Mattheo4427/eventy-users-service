package com.eventy.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // On injecte l'URL interne depuis le application.properties / env
    // Assurez-vous que KEYCLOAK_SERVER_URL est bien http://keycloak:8080 dans le .env
    @Value("${keycloak.server-url}") 
    private String keycloakServerUrl;
    
    @Value("${keycloak.realm}")
    private String realm;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // 1. Routes publiques
                .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/users").permitAll()
                .requestMatchers(HttpMethod.POST, "/users/internal/keycloak-sync").permitAll()

                // 2. Routes Admin
                .requestMatchers("/users/admin/**").hasRole("ADMIN")

                // 3. Routes Authentifiées
                .requestMatchers("/users/**").authenticated()

                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    .decoder(jwtDecoder()) // Utilisation de notre décodeur permissif
                )
            );
        return http.build();
    }

    /**
     * Décodeur JWT configuré pour récupérer les clés (JWK) via le réseau Docker interne
     * mais SANS valider strictement l'URL de l'émetteur (iss) pour éviter les erreurs d'IP.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Construction de l'URL JWK interne : http://keycloak:8080/realms/eventy-realm/protocol/openid-connect/certs
        String jwkSetUri = String.format("%s/realms/%s/protocol/openid-connect/certs", keycloakServerUrl, realm);
        
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * Convertisseur de Rôles (inchangé, car il fonctionne bien maintenant)
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

            // Rôles Realm
            if (jwt.hasClaim("realm_access")) {
                Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                Object roles = realmAccess.get("roles");
                if (roles instanceof Collection<?> rolesCollection) {
                    List<GrantedAuthority> realmRoles = rolesCollection.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase()))
                        .collect(Collectors.toList());
                    authorities.addAll(realmRoles);
                }
            }

            // Attribut app_role
            if (jwt.hasClaim("app_role")) {
                String appRole = jwt.getClaimAsString("app_role");
                if (appRole != null && !appRole.trim().isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + appRole.toUpperCase()));
                }
            }
            return authorities;
        });
        return converter;
    }
}