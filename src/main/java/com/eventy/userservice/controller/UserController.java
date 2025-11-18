package com.eventy.userservice.controller;

import com.eventy.userservice.model.User;
import com.eventy.userservice.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("admin"));
        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
    }

    // Extract authenticated user ID from JWT or UserDetails
    private UUID getAuthenticatedUserId() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnsupportedOperationException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            return UUID.fromString(ud.getUsername());
        } else if (principal instanceof String str) {
            return UUID.fromString(str);
        }
        throw new UnsupportedOperationException("Cannot extract user ID from principal: " + principal);
    }

    // -------------------- PUBLIC ENDPOINTS --------------------

    /** Public endpoint to create a new user */
    @PostMapping("/create")
    public ResponseEntity<User> createUserPublic(@Valid @RequestBody CreateUserRequest req) {
        User u = new User();
        u.setUsername(req.username);
        u.setEmail(req.email);
        u.setFirstName(req.firstName);
        u.setLastName(req.lastName);
        u.setAvatarUrl(null);
        u.setBalance(java.math.BigDecimal.ZERO);
        u.setCreationDate(LocalDate.now());
        u.setStatus(User.Status.ACTIVE);
        u.setRole(User.Role.USER);

        User saved = userService.createUser(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // -------------------- AUTHENTICATED USER ENDPOINTS --------------------

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            UUID userId = getAuthenticatedUserId();
            Optional<User> user = userService.getUserById(userId);
            return user.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse(ERROR_USER_NOT_FOUND)));
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
            details.setAvatarUrl(req.avatarUrl);
            details.setBalance(req.balance);
            details.setStatus(req.status);

            Optional<User> updated = userService.updateUser(userId, details);
            return updated.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse(ERROR_USER_NOT_FOUND)));
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
            return user.map(u -> ResponseEntity.ok(new BalanceResponse(u.getBalance())))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse(ERROR_USER_NOT_FOUND)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(ERROR_AUTH_REQUIRED));
        }
    }

    // -------------------- ADMIN ENDPOINTS --------------------

    @GetMapping("/admin/users")
    public ResponseEntity<?> adminListUsers() {
        try {
            checkAdminRole();
            List<User> users = userService.getAllUsers();
            if (users.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No users found."));
            }
            return ResponseEntity.ok(users);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Admin role required"));
        }
    }

    @PostMapping("/admin/users/{id}/suspend")
    public ResponseEntity<?> suspendUser(@PathVariable UUID id) {
        try {
            checkAdminRole();
            boolean suspended = userService.suspendUser(id);
            if (suspended) {
                return ResponseEntity.ok(new SuccessResponse("User suspended."));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Admin role required"));
        }
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> adminDeleteUser(@PathVariable UUID id) {
        try {
            checkAdminRole();
            userService.deleteUser(id);
            return ResponseEntity.ok(new SuccessResponse("User deleted."));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Admin role required"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(ERROR_USER_NOT_FOUND));
        }
    }

    // -------------------- DTOs --------------------

    public static class CreateUserRequest {
        @NotBlank public String username;
        @NotBlank @Email public String email;
        @NotBlank public String firstName;
        @NotBlank public String lastName;
    }

    public static class UpdateUserRequest {
        @NotBlank public String username;
        @NotBlank @Email public String email;
        @NotBlank public String firstName;
        @NotBlank public String lastName;
        public String avatarUrl;
        public User.Status status;
        public java.math.BigDecimal balance;
    }

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
}
