package com.eventy.userservice.controller;

import com.eventy.userservice.model.User;
import com.eventy.userservice.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            log.debug("Extracted user ID from JWT subject: {}", sub);
            return UUID.fromString(sub);
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            log.debug("Extracted user ID from UserDetails username: {}", userDetails.getUsername());
            return UUID.fromString(userDetails.getUsername());
        } else if (principal instanceof String str) {
            log.debug("Extracted user ID from String principal: {}", str);
            return UUID.fromString(str);
        }
        log.error("Cannot extract user ID from principal of type: {}", principal.getClass().getName());
        throw new UnsupportedOperationException("Cannot extract user ID from principal: " + principal);
    }

    // --- PUBLIC ENDPOINT: User Creation (as required for sign-up) ---

    // POST /api/users - PUBLIC: To create a new standard user
    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest req) {
        try {
            User u = new User();
            u.setId(UUID.randomUUID());
            u.setUsername(req.getUsername());
            u.setEmail(req.getEmail());
            u.setFirstName(req.getFirstName());
            u.setLastName(req.getLastName());
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            u.setRole(User.Role.USER);
            
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String message = "User creation failed: Username or Email already exists.";
            log.warn("Data Integrity Violation on user creation: {}", message);
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
            log.error("KEYCLOAK_SYNC_SECRET environment variable is not configured.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Keycloak sync secret not configured"));
        }
        
        if (secret == null || !secret.equals(expectedSecret)) {
            log.warn("Invalid or missing X-Keycloak-Secret received.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Invalid or missing Keycloak sync secret"));
        }
        
        try {
            log.info("Processing Keycloak sync for username: {}", req.getUsername());
            
            // Vérifier si l'utilisateur existe déjà
            Optional<User> existingUser = userService.getUserByUsername(req.getUsername());
            
            if (existingUser.isPresent()) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new SuccessResponse("User already exists, skipping sync"));
            }
            
            // Créer le nouvel utilisateur
            User u = new User();
            
            u.setId(req.getId());

            Optional<User> existingById = userService.getUserById(req.getId());
            if (existingById.isPresent()) {
                return ResponseEntity.ok(new SuccessResponse("User already exists, skipping sync"));
            }

            // Réinitialiser les champs potentiellement null/vides avant de sauvegarder
            String username = req.getUsername() != null ? req.getUsername().trim() : "";
            String email = req.getEmail() != null ? req.getEmail().trim() : "";
            String firstName = req.getFirstName() != null ? req.getFirstName().trim() : "N/A";
            String lastName = req.getLastName() != null ? req.getLastName().trim() : "N/A";

            // Vérifications de base avant l'insertion
            if (username.isEmpty() || email.isEmpty()) {
                log.error("Username or email is empty after trimming for Keycloak sync.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Username and email must be provided for Keycloak sync."));
            }

            u.setUsername(username);
            u.setEmail(email);
            u.setFirstName(firstName);
            u.setLastName(lastName);
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            
            // Accepter le rôle depuis Keycloak
            if (req.getRole() != null && !req.getRole().trim().isEmpty()) {
                try {
                    u.setRole(User.Role.valueOf(req.getRole().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Received invalid role '{}' from Keycloak for user {}. Defaulting to USER role.", req.getRole(), req.getUsername());
                    u.setRole(User.Role.USER); // Fallback si rôle invalide
                }
            } else {
                u.setRole(User.Role.USER);
            }
            
            User saved = userService.createUser(u);
            log.info("User {} created successfully from Keycloak sync.", saved.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Data Integrity Violation on Keycloak sync for user {}: {}", req.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("User already exists"));
                    
        } catch (Exception e) {
            log.error("Failed to process Keycloak sync request for user {}: {}", req.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Failed to sync user due to internal error: " + e.getMessage()));
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
            details.setUsername(req.getUsername());
            details.setEmail(req.getEmail());
            details.setFirstName(req.getFirstName());
            details.setLastName(req.getLastName());
            details.setAvatarUrl(req.getAvatarUrl());
            
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
            u.setId(UUID.randomUUID());
            u.setUsername(req.getUsername());
            u.setEmail(req.getEmail());
            u.setFirstName(req.getFirstName());
            u.setLastName(req.getLastName());
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
        @NotBlank private String username;
        @NotBlank @Email private String email;
        @NotBlank private String firstName;
        @NotBlank private String lastName;

        public CreateUserRequest() {} // Default constructor for Jackson

        // Constructor for testing and easy instantiation
        public CreateUserRequest(String username, String email, String firstName, String lastName) {
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
    }

    // DTO pour synchronisation Keycloak (avec role) - Validation assouplie
    public static class KeycloakSyncUserRequest {
        
        // Assouplissement de la validation pour éviter le 400 Bad Request
        @NotNull private UUID id;
        @NotNull @Email private String email; 
        private String username;
        private String firstName;
        private String lastName;
        private String role; 

        public KeycloakSyncUserRequest() {} // Default constructor for Jackson

        // Constructor for testing and easy instantiation
        public KeycloakSyncUserRequest(String username, String email, String firstName, String lastName, String role) {
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.role = role;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class UpdateUserRequest {
        @NotBlank private String username;
        @NotBlank @Email private String email;
        @NotBlank private String firstName;
        @NotBlank private String lastName;
        private String avatarUrl;

        public UpdateUserRequest() {} // Default constructor for Jackson

        // Constructor for testing and easy instantiation
        public UpdateUserRequest(String username, String email, String firstName, String lastName, String avatarUrl) {
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.avatarUrl = avatarUrl;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
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