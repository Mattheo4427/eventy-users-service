package com.eventy.userservice.service;

import com.eventy.userservice.model.User;
import com.eventy.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * SCÉNARIO 1 : Inscription (Création complète)
     * Crée l'utilisateur dans Keycloak PUIS dans la BDD locale.
     */
    @Transactional
    public User createUser(User user, String password) {
        log.info("Création complète de l'utilisateur : {}", user.getEmail());

        // 1. Création dans Keycloak (Source de vérité Auth)
        // Note : Assurez-vous d'avoir corrigé l'erreur Enum -> String ici aussi (.name())
        String keycloakId = keycloakAdminService.createUser(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                password,
                user.getRole().name() // Correction type Enum
        );

        // 2. Synchronisation de l'ID généré par Keycloak
        // Correction : setId -> setUserId (selon votre modèle User.java)
        user.setId(UUID.fromString(keycloakId));

        // 3. Sauvegarde locale
        return userRepository.save(user);
    }

    /**
     * CORRECTION : Synchronisation Keycloak (Webhook)
     * Ne met à jour QUE les champs d'identité, PRÉSERVE la balance et le status.
     */
    @Transactional
    public User syncUser(User sourceUser) {
        log.info("Synchronisation de l'utilisateur : {}", sourceUser.getEmail());

        return userRepository.findById(sourceUser.getId())
                .map(existingUser -> {
                    // Mise à jour partielle : On ne touche PAS à la balance !
                    existingUser.setEmail(sourceUser.getEmail());
                    existingUser.setUsername(sourceUser.getUsername());
                    existingUser.setFirstName(sourceUser.getFirstName());
                    existingUser.setLastName(sourceUser.getLastName());

                    // On met à jour le rôle uniquement s'il est fourni et valide (évite reset USER)
                    if (sourceUser.getRole() != null) {
                        existingUser.setRole(sourceUser.getRole());
                    }

                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // Si l'utilisateur n'existe pas, création initiale avec valeurs par défaut
                    return userRepository.save(sourceUser);
                });
    }

    /**
     * CORRECTION : Mise à jour du profil par l'utilisateur
     * Ne touche PAS à la balance ni au rôle. Met à jour Keycloak.
     */
    @Transactional
    public User updateUserProfile(UUID id, String firstName, String lastName, String email) {
        return userRepository.findById(id).map(user -> {
            // 1. Mise à jour Keycloak
            try {
                keycloakAdminService.updateUser(
                        id.toString(), firstName, lastName, email, user.getRole().name()
                );
            } catch (Exception e) {
                log.error("Erreur update Keycloak", e);
            }

            // 2. Mise à jour Locale (Champs d'identité uniquement)
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            // Pas de touche à la balance !

            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public Optional<User> updateUser(UUID id, User userDetails) {
        return userRepository.findById(id).map(existingUser -> {
            // 1. Mise à jour dans Keycloak (si nécessaire)
            try {
                if(userDetails.getRole() == null) {
                    String role = existingUser.getRole().name();
                    keycloakAdminService.updateUser(
                            id.toString(),
                            userDetails.getFirstName(),
                            userDetails.getLastName(),
                            userDetails.getEmail(),
                            role
                    );
                }
                else {
                    keycloakAdminService.updateUser(
                            id.toString(),
                            userDetails.getFirstName(),
                            userDetails.getLastName(),
                            userDetails.getEmail(),
                            userDetails.getRole().name()
                    );
                }
            } catch (Exception e) {
                log.error("Erreur lors de la mise à jour Keycloak pour l'user {}", id, e);
                // On continue quand même pour la mise à jour locale, ou on throw une exception selon votre règle métier
            }

            // 2. Mise à jour locale
            existingUser.setUsername(userDetails.getUsername());
            existingUser.setEmail(userDetails.getEmail());
            existingUser.setFirstName(userDetails.getFirstName());
            existingUser.setLastName(userDetails.getLastName());
            existingUser.setAvatarUrl(userDetails.getAvatarUrl());
            // existingUser.setCreationDate(userDetails.getCreationDate()); // Généralement on ne change pas la date de création
            existingUser.setStatus(userDetails.getStatus());
            //existingUser.setBalance(userDetails.getBalance());
            if(userDetails.getRole() != null) {
                existingUser.setRole(userDetails.getRole());
            }

            return userRepository.save(existingUser);
        });
    }


    /**
     * Suspend un utilisateur (Localement + Keycloak si implémenté)
     */
    @Transactional
    public boolean suspendUser(UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setStatus(User.Status.SUSPENDED);
            userRepository.save(user);
            
            // Optionnel : Désactiver aussi dans Keycloak
            // keycloakAdminService.disableUser(id.toString()); 
            
            return true;
        }).orElse(false);
    }

    /**
     * Supprime l'utilisateur de la BDD et de Keycloak
     */
    @Transactional
    public void deleteUser(UUID id) {
        if (userRepository.existsById(id)) {
            // 1. Suppression Keycloak
            try {
                keycloakAdminService.deleteUser(id.toString());
            } catch (Exception e) {
                log.error("Erreur lors de la suppression Keycloak pour l'user {}", id, e);
                // On continue pour supprimer localement
            }

            // 2. Suppression Locale
            userRepository.deleteById(id);
        }
    }
}