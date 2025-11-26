package com.eventy.userservice.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String targetRealm;

    /**
     * Crée un utilisateur dans Keycloak et retourne son ID (UUID).
     */
    public String createUser(String email, String firstName, String lastName, String password, String role) {
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(email); // On utilise l'email comme username
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmailVerified(true);
        
        // Ajout de l'attribut personnalisé pour le rôle (pour votre mapper)
        user.singleAttribute("app_role", role);

        // Récupération de la ressource Users
        UsersResource usersResource = getUsersResource();

        // Appel Keycloak
        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            String userId = CreatedResponseUtil.getCreatedId(response);
            log.info("Utilisateur créé dans Keycloak avec l'ID: {}", userId);
            
            // Définir le mot de passe
            resetPassword(userId, password);
            
            return userId;
        } else {
            log.error("Erreur création Keycloak: status={}", response.getStatus());
            throw new RuntimeException("Impossible de créer l'utilisateur dans Keycloak: " + response.getStatusInfo());
        }
    }

    /**
     * Supprime un utilisateur de Keycloak.
     */
    public void deleteUser(String userId) {
        getUsersResource().get(userId).remove();
        log.info("Utilisateur supprimé de Keycloak: {}", userId);
    }

    /**
     * Définit le mot de passe (Credentials).
     */
    private void resetPassword(String userId, String password) {
        CredentialRepresentation passwordCred = new CredentialRepresentation();
        passwordCred.setTemporary(false);
        passwordCred.setType(CredentialRepresentation.PASSWORD);
        passwordCred.setValue(password);

        UserResource userResource = getUsersResource().get(userId);
        userResource.resetPassword(passwordCred);
    }

    /**
     * Helper pour accéder au Realm.
     */
    private UsersResource getUsersResource() {
        return keycloak.realm(targetRealm).users();
    }
    
    /**
     * Met à jour un utilisateur
     */
    public void updateUser(String userId, String firstName, String lastName, String email, String role) {
        UserResource userResource = getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.singleAttribute("app_role", role.toUpperCase());
        
        userResource.update(user);
    }
}