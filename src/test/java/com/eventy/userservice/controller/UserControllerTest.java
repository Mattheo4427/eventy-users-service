package com.eventy.userservice.controller;

import com.eventy.userservice.model.User;
import com.eventy.userservice.service.UserService;
import com.eventy.userservice.controller.UserController.CreateUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    // Helper: create test user
    private User baseUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setUsername("testuser");
        u.setEmail("test@example.com");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setCreationDate(LocalDate.now());
        u.setBalance(BigDecimal.ZERO);
        u.setStatus(User.Status.ACTIVE);
        u.setRole(User.Role.USER);
        return u;
    }

    // Helper: simulate authenticated user
    private void mockAuthenticated(UUID userId, boolean admin) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        userId.toString(),
                        null,
                        admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                              : List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    // Helper: simulate no authentication
    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    /** ================================
     *    PUBLIC CREATE USER
     *  ================================= */

    @Test
    void testCreateUser_Success() throws Exception {
        UUID newId = UUID.randomUUID();
        // Utilisation du constructeur public pour créer le DTO, évitant l'accès aux champs privés.
        CreateUserRequest req = new CreateUserRequest("newuser", "new@example.com", "New", "User", "pass");

        UUID id = UUID.randomUUID();
        User saved = baseUser(id);
        saved.setUsername("newuser");
        saved.setEmail("new@example.com");

        // Mock the service call to return the created user
        when(userService.createUser(any(User.class), "pass")).thenReturn(savedUser);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void testCreateUser_BadRequest_InvalidEmail() throws Exception {
        // Utilisation du constructeur public avec un email invalide
        CreateUserRequest req = new CreateUserRequest("newuser", "invalid-email", "New", "User" , "pass");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    /** ================================
     *          /me ENDPOINT
     *  ================================= */

    @Test
    void me_unauthorized_whenNotAuthenticated() throws Exception {
        clearAuth();

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required."));
    }

    @Test
    void me_success() throws Exception {
        UUID id = UUID.randomUUID();
        mockAuthenticated(id, false);

        User user = baseUser(id);
        when(userService.getUserById(id)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    /** ================================
     *     ADMIN PROTECTED ENDPOINTS
     *  ================================= */

    @Test
    void admin_list_forbidden_whenNotAdmin() throws Exception {
        mockAuthenticated(UUID.randomUUID(), false);

        mockMvc.perform(get("/api/users/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_list_success_whenAdmin() throws Exception {
        mockAuthenticated(UUID.randomUUID(), true);

        when(userService.getAllUsers()).thenReturn(List.of(baseUser(UUID.randomUUID())));

        mockMvc.perform(get("/api/users/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    void admin_suspend_notFound() throws Exception {
        mockAuthenticated(UUID.randomUUID(), true);

        when(userService.suspendUser(any(UUID.class))).thenReturn(false);

        mockMvc.perform(post("/api/users/admin/users/" + UUID.randomUUID() + "/suspend"))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_suspend_success() throws Exception {
        mockAuthenticated(UUID.randomUUID(), true);

        when(userService.suspendUser(any(UUID.class))).thenReturn(true);

        mockMvc.perform(post("/api/users/admin/users/" + UUID.randomUUID() + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User suspended."));
    }
}
