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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
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
            // Throw specific exception to be caught as UNAUTHORIZED
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
            u.setBalance(java.math.BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            u.setRole(User.Role.USER);
            
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Log the full exception for backend debugging
            // log.error("User creation failed due to data integrity violation", e); 
            
            // Return a specific 409 Conflict status with a helpful error message
            String message = "User creation failed: Username or Email already exists.";
            return ResponseEntity.status(HttpStatus.CONFLICT) // Use 409 Conflict for resource existence issues
                    .body(new ErrorResponse(message));
                    
        } catch (Exception e) {
            // Catch all other unexpected exceptions (e.g., service logic errors)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Failed to create user due to invalid data or an internal error."));
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
            
            // Only fields a regular user is expected to update
            User details = new User();
            details.setUsername(req.username);
            details.setEmail(req.email);
            details.setFirstName(req.firstName);
            details.setLastName(req.lastName);
            details.setAvatarUrl(req.avatarUrl);
            // Note: Status and Balance modifications are highly restricted for /me, 
            // but for simple DTO reuse, we allow them to be passed; 
            // the Service layer should ensure they are ignored or restricted.
            // For now, removing them to enforce user self-update limits:
            // details.setBalance(req.balance); 
            // details.setStatus(req.status);
            
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

    // Updated DTO to only include fields expected for regular user update,
    // as per strict interpretation of /me PUT endpoint
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
        
        // Removed status and balance, as these are typically administrative fields
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