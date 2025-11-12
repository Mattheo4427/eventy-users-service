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
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
        Optional<User> user = userService.getUserById(id);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest req) {
        User u = new User();
        u.setUsername(req.username);
        u.setEmail(req.email);
        u.setFirstName(req.firstName);
        u.setLastName(req.lastName);
        u.setBirthDate(req.birthDate);
        u.setIsActive(true);
        User saved = userService.createUser(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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
        return updated.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
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

        @NotNull
        public LocalDate birthDate;
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

        @NotNull
        public LocalDate birthDate;

        @NotNull
        public Boolean isActive;
    }
}

