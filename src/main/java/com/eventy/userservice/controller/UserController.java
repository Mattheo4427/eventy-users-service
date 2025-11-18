package com.eventy.userservice.controller;

import com.eventy.userservice.model.User;
import com.eventy.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private static final String ERROR_USER_NOT_FOUND = "User not found.";
    private static final String ERROR_AUTH_REQUIRED = "Authentication required.";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Helper method to check admin role
    private void checkAdminRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("admin"));
        if (!isAdmin) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    // Extract user ID from JWT (Keycloak) via Spring Security
    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnsupportedOperationException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            return UUID.fromString(sub);
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        } else if (principal instanceof String str) {
            return UUID.fromString(str);
        }
        throw new UnsupportedOperationException("Cannot extract user ID from principal: " + principal);
    }

    // --- PUBLIC ENDPOINT: User Creation (as required for sign-up) ---

    // POST /api/users - PUBLIC: To create a new standard user
    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest req) {
        try {
            User u = new User();
            u.setUsername(req.username);
            u.setEmail(req.email);
            u.setFirstName(req.firstName);
            u.setLastName(req.lastName);
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            u.setRole(User.Role.USER);
            
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String message = "User creation failed: Username or Email already exists.";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(message));
                    
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Failed to create user due to invalid data or an internal error."));
        }
    }

    // --- INTERNAL ENDPOINT: Keycloak Synchronization ---
    
    // POST /api/users/internal/keycloak-sync - INTERNAL: Sync user from Keycloak with role
    @PostMapping("/internal/keycloak-sync")
    public ResponseEntity<?> syncUserFromKeycloak(
            @Valid @RequestBody KeycloakSyncUserRequest req,
            @RequestHeader(value = "X-Keycloak-Secret", required = false) String secret) {
        
        // Vérifier le secret partagé
        String expectedSecret = System.getenv("KEYCLOAK_SYNC_SECRET");
        if (expectedSecret == null || expectedSecret.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Keycloak sync secret not configured"));
        }
        
        if (secret == null || !secret.equals(expectedSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Invalid or missing Keycloak sync secret"));
        }
        
        try {
            // Vérifier si l'utilisateur existe déjà
            Optional<User> existingUser = userService.getUserByUsername(req.username);
            
            if (existingUser.isPresent()) {
                // L'utilisateur existe déjà, on peut choisir de l'ignorer ou de le mettre à jour
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new SuccessResponse("User already exists, skipping sync"));
            }
            
            // Créer le nouvel utilisateur
            User u = new User();
            u.setUsername(req.username);
            u.setEmail(req.email);
            // Keycloak peut envoyer des chaînes vides pour firstName et lastName,
            // d'où la suppression de @NotBlank sur le DTO.
            u.setFirstName(req.firstName);
            u.setLastName(req.lastName);
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            
            // ✅ Accepter le rôle depuis Keycloak
            if (req.role != null && !req.role.trim().isEmpty()) {
                try {
                    u.setRole(User.Role.valueOf(req.role.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    // Log the invalid role received
                    log.warn("Received invalid role '{}' from Keycloak for user {}. Defaulting to USER role.", req.role, req.username);
                    u.setRole(User.Role.USER); // Fallback si rôle invalide
                }
            } else {
                u.setRole(User.Role.USER);
            }
            
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("User already exists"));
                    
        } catch (Exception e) {
            // Added logging for internal debugging in case of unexpected errors
            log.error("Failed to process Keycloak sync request for user {}: {}", req.username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Failed to sync user: " + e.getMessage()));
        }
    }
    
    // --- AUTHENTICATED USER ENDPOINTS (/me) ---

    // GET /api/users/me : Récupère le profil complet de l'utilisateur actuellement authentifié.
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            UUID userId = getAuthenticatedUserId();
            Optional<User> user = userService.getUserById(userId);
            if (user.isPresent()) {
                return ResponseEntity.ok(user.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

    // PUT /api/users/me : Met à jour les informations du profil de l'utilisateur.
    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody UpdateUserRequest req) {
        try {
            UUID userId = getAuthenticatedUserId();
            
            User details = new User();
            details.setUsername(req.username);
            details.setEmail(req.email);
            details.setFirstName(req.firstName);
            details.setLastName(req.lastName);
            details.setAvatarUrl(req.avatarUrl);
            
            Optional<User> updated = userService.updateUser(userId, details);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

    // GET /api/users/me/balance : Récupère le solde actuel du portefeuille virtuel.
    @GetMapping("/me/balance")
    public ResponseEntity<?> getCurrentUserBalance() {
        try {
            UUID userId = getAuthenticatedUserId();
            Optional<User> user = userService.getUserById(userId);
            if (user.isPresent()) {
                return ResponseEntity.ok(new BalanceResponse(user.get().getBalance()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
            }
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

    // --- ADMIN ENDPOINTS (/admin) ---

    // GET /api/admin/users : (Admin) Liste tous les utilisateurs avec filtres.
    @GetMapping("/admin/users")
    public ResponseEntity<?> adminListUsers(@RequestParam(required = false) String status) {
        try {
            checkAdminRole();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        }

        List<User> users = userService.getAllUsers();
        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("No users found."));
        }
        return ResponseEntity.ok(users);
    }
    
    // POST /api/admin/users/create-admin (utility, kept for convenience)
    @PostMapping("/admin/users/create-admin")
    public ResponseEntity<?> createAdmin(@Valid @RequestBody CreateUserRequest req) {
        try {
            checkAdminRole();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        }
        
        try {
            User u = new User();
            u.setUsername(req.username);
            u.setEmail(req.email);
            u.setFirstName(req.firstName);
            u.setLastName(req.lastName);
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            u.setRole(User.Role.ADMIN); 
            
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating admin user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Failed to create admin user"));
        }
    }

    // POST /api/admin/users/{id}/suspend : (Admin) Suspend le compte d'un utilisateur.
    @PostMapping("/admin/users/{id}/suspend")
    public ResponseEntity<?> suspendUser(@PathVariable UUID id) {
        try {
            checkAdminRole();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        }

        boolean suspended = userService.suspendUser(id);
        if (suspended) {
            return ResponseEntity.ok().body(new SuccessResponse("User suspended."));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
        }
    }

    // DELETE /api/admin/users/{id} : (Admin) Supprime un compte utilisateur.
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> adminDeleteUser(@PathVariable UUID id) {
        try {
            checkAdminRole();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(e.getMessage()));
        }

        try {
            userService.deleteUser(id);
            return ResponseEntity.ok().body(new SuccessResponse("User deleted."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
        }
    }

    // --- DTOs ---

    // DTO pour création publique d'utilisateur (sans role)
    public static class CreateUserRequest {
        @NotBlank
        public String username;

        @NotBlank
        @Email
        public String email;

        @NotBlank
        public String firstName;

        @NotBlank
        public String lastName;
    }

    // DTO pour synchronisation Keycloak (avec role)
    public static class KeycloakSyncUserRequest {
        @NotBlank
        public String username;

        @NotBlank
        @Email
        public String email;

        // Validation relaxed for Keycloak sync: firstName can be empty string ("") if Keycloak sends it that way.
        public String firstName;

        // Validation relaxed for Keycloak sync: lastName can be empty string ("") if Keycloak sends it that way.
        public String lastName;

        public String role; // Optionnel, géré uniquement par Keycloak
    }

    public static class UpdateUserRequest {
        @NotBlank
        public String username;

        @NotBlank
        @Email
        public String email;

        @NotBlank
        public String firstName;

        @NotBlank
        public String lastName;

        public String avatarUrl;
    }

    // Response DTOs
    public static class ErrorResponse {
        public String error;
        public ErrorResponse(String error) {
            this.error = error;
        }
    }

    public static class SuccessResponse {
        public String message;
        public SuccessResponse(String message) {
            this.message = message;
        }
    }

    public static class BalanceResponse {
        public Object balance;
        public BalanceResponse(Object balance) {
            this.balance = balance;
        }
    }
}