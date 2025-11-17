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
    // Extract user ID from JWT (Keycloak) via Spring Security
    private UUID getAuthenticatedUserId() {
        // Get authentication from security context
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnsupportedOperationException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();
        // Keycloak adapter: principal is usually a KeycloakPrincipal or Jwt
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String sub = jwt.getSubject();
            return UUID.fromString(sub);
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            // Fallback: try username as UUID
            return UUID.fromString(userDetails.getUsername());
        } else if (principal instanceof String str) {
            // Fallback: try principal string as UUID
            return UUID.fromString(str);
        }
        throw new UnsupportedOperationException("Cannot extract user ID from principal: " + principal);
    }

        // GET /api/users/me
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

        // PUT /api/users/me
        @PutMapping("/me")
        public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody UpdateUserRequest req) {
            try {
                UUID userId = getAuthenticatedUserId();
                User details = new User();
                details.setUsername(req.username);
                details.setEmail(req.email);
                details.setFirstName(req.firstName);
                details.setLastName(req.lastName);
                details.setBirthDate(req.birthDate);
                details.setIsActive(req.isActive);
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

        // GET /api/users/me/balance
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
        // GET /api/admin/users
        @GetMapping("/admin/users")
        public ResponseEntity<?> adminListUsers(@RequestParam(required = false) String status) {
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No users found."));
            }
            return ResponseEntity.ok(users);
        }

        // POST /api/admin/users/{id}/suspend
        @PostMapping("/admin/users/{id}/suspend")
        public ResponseEntity<?> suspendUser(@PathVariable UUID id) {
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
                userService.deleteUser(id);
                return ResponseEntity.ok().body(new SuccessResponse("User deleted."));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
            }
        }

        // Error/Success response DTOs
        public static class ErrorResponse {
            public String error;
            public ErrorResponse(String error) { this.error = error; }
        }
        public static class SuccessResponse {
            public String message;
            public SuccessResponse(String message) { this.message = message; }
        }
        public static class BalanceResponse {
            public Object balance;
            public BalanceResponse(Object balance) { this.balance = balance; }
        }
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
           List<User> users = userService.getAllUsers();
           if (users.isEmpty()) {
              return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
           }
           return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
            Optional<User> user = userService.getUserById(id);
            if (user.isPresent()) {
                return ResponseEntity.ok(user.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest req) {
        try {
            User u = new User();
            u.setUsername(req.username);
            u.setEmail(req.email);
            u.setFirstName(req.firstName);
            u.setLastName(req.lastName);
            u.setAvatarUrl(req.avatarUrl); // add avatarUrl
            u.setBirthDate(req.birthDate);
            u.setStatus(User.Status.ACTIVE); // default status
            u.setBalance(req.balance != null ? req.balance : java.math.BigDecimal.ZERO); // default balance
            u.setRole(User.Role.USER); // default role
            User saved = userService.createUser(u);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
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
            details.setBirthDate(req.birthDate);
            details.setIsActive(req.isActive);

            Optional<User> updated = userService.updateUser(id, details);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
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

        public String avatarUrl; // add avatarUrl

        @NotNull
        public LocalDate birthDate;

        public User.Status status; // optional, default ACTIVE

        public java.math.BigDecimal balance; // optional, default 0

        public User.Role role; // optional, default USER
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

        public String avatarUrl; // add avatarUrl

        @NotNull
        public LocalDate birthDate;

        public User.Status status;

        public java.math.BigDecimal balance;

        public User.Role role;
    }
}
