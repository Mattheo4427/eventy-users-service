package com.eventy.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    // Security filter chain configuration
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // 1. Public routes (actuator, swagger, docs)
                .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // 2. POST /api/users for registration
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()

                // 3. POST /api/users/internal/keycloak-sync for internal synchronization
                .requestMatchers(HttpMethod.POST, "/api/users/internal/keycloak-sync").permitAll()

                // 4. Admin routes (Reserved for users with ADMIN role)
                .requestMatchers("/api/users/admin/**").hasRole("ADMIN")

                // 5. Authenticated routes (Everything else under /api/users)
                .requestMatchers("/api/users/**").authenticated()

                // 6. By default, everything else is public
                .anyRequest().permitAll()
            )
            // CRITICAL: Use the converter to read roles from the JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    /**
     * Custom converter to read the 'realm_access.roles' claim from the Keycloak token
     * and map it to Spring authorities (e.g., ROLE_ADMIN).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

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

            return authorities;
        });

        return converter;
    }
}