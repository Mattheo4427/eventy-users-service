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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("admin"));
        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
    }

    // Extract user ID from JWT (Keycloak) via Spring Security
    private UUID getAuthenticatedUserId() {
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnsupportedOperationException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String sub = jwt.getSubject();
            return UUID.fromString(sub);
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        } else if (principal instanceof String str) {
            return UUID.fromString(str);
        }
        throw new UnsupportedOperationException("Cannot extract user ID from principal: " + principal);
    }

    // PUBLIC ENDPOINTS
    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        List<User> users = userService.getAllUsers();
        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
        Optional<User> user = userService.getUserById(id);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest req) {
        try {
            System.out.println("=== CREATE USER REQUEST ===");
            System.out.println("Username: " + req.username);
            System.out.println("Email: " + req.email);
            System.out.println("FirstName: " + req.firstName);
            System.out.println("LastName: " + req.lastName);
            
            User u = new User();
            u.setUsername(req.username);
            u.setEmail(req.email);
            u.setFirstName(req.firstName);
            u.setLastName(req.lastName);
            // CREATION RULES: avatarUrl = null, balance = 0, creationDate = now
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(java.math.BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            u.setRole(User.Role.USER);
            
            System.out.println("Creating user in database...");
            User saved = userService.createUser(u);
            System.out.println("User created successfully with ID: " + saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            System.err.println("=== ERROR CREATING USER ===");
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        User details = new User();
        details.setUsername(req.username);
        details.setEmail(req.email);
        details.setFirstName(req.firstName);
        details.setLastName(req.lastName);
        // UPDATE RULES: avatarUrl and balance CAN be modified
        details.setAvatarUrl(req.avatarUrl);
        details.setBalance(req.balance);
        details.setStatus(req.status);
        // creationDate is NOT set on update (cannot be modified)
        
        Optional<User> updated = userService.updateUser(id, details);
        if (updated.isPresent()) {
            return ResponseEntity.ok(updated.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // AUTHENTICATED USER ENDPOINTS
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody UpdateUserRequest req) {
        try {
            UUID userId = getAuthenticatedUserId();
            User details = new User();
            details.setUsername(req.username);
            details.setEmail(req.email);
            details.setFirstName(req.firstName);
            details.setLastName(req.lastName);
            // UPDATE RULES: avatarUrl and balance CAN be modified
            details.setAvatarUrl(req.avatarUrl);
            details.setBalance(req.balance);
            details.setStatus(req.status);
            // creationDate is NOT set on update (cannot be modified)
            
            Optional<User> updated = userService.updateUser(userId, details);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

    // ADMIN ENDPOINTS
    @GetMapping("/admin/users")
    public ResponseEntity<?> adminListUsers(@RequestParam(required = false) String status) {
        try {
            checkAdminRole();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Admin role required"));
        }
        
        List<User> users = userService.getAllUsers();
        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No users found."));
        }
        return ResponseEntity.ok(users);
    }

    @PostMapping("/admin/users/create-admin")
    public ResponseEntity<?> createAdmin(@Valid @RequestBody CreateUserRequest req) {
        try {
            checkAdminRole();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Admin role required"));
        }
        
        try {
            User u = new User();
            u.setUsername(req.username);
            u.setEmail(req.email);
            u.setFirstName(req.firstName);
            u.setLastName(req.lastName);
            // CREATION RULES: avatarUrl = null, balance = 0, creationDate = now
            u.setAvatarUrl(null);
            u.setCreationDate(LocalDate.now());
            u.setBalance(java.math.BigDecimal.ZERO);
            u.setStatus(User.Status.ACTIVE);
            u.setRole(User.Role.ADMIN);
            
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Failed to create admin user"));
        }
    }

    @PostMapping("/admin/users/{id}/suspend")
    public ResponseEntity<?> suspendUser(@PathVariable UUID id) {
        try {
            checkAdminRole();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Admin role required"));
        }
        
        boolean suspended = userService.suspendUser(id);
        if (suspended) {
            return ResponseEntity.ok().body(new SuccessResponse("User suspended."));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
        }
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> adminDeleteUser(@PathVariable UUID id) {
        try {
            checkAdminRole();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Admin role required"));
        }
        
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok().body(new SuccessResponse("User deleted."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
        }
    }

    // DTOs
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

        public User.Status status;

        public java.math.BigDecimal balance;
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