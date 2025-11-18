package com.eventy.userservice.controller;

import com.eventy.userservice.controller.UserController.CreateUserRequest;
import com.eventy.userservice.model.User;
import com.eventy.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) // Important: Désactive les filtres de sécurité pour isoler le contrôleur
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    // Helper method for creating a base User object
    private User createBaseUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setBalance(BigDecimal.ZERO);
        user.setCreationDate(LocalDate.now());
        user.setStatus(User.Status.ACTIVE);
        user.setRole(User.Role.USER);
        return user;
    }

    // --- Tests de l'Endpoint Public de Création (POST /api/users) ---

    @Test
    void testCreateUser_Success() throws Exception {
        UUID newId = UUID.randomUUID();
        CreateUserRequest req = new CreateUserRequest();
        req.username = "newuser";
        req.email = "new@example.com";
        req.firstName = "New";
        req.lastName = "User";

        User savedUser = createBaseUser(newId);
        savedUser.setUsername(req.username);
        savedUser.setEmail(req.email);

        // Mock the service call to return the created user
        when(userService.createUser(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void testCreateUser_BadRequest_InvalidEmail() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.username = "newuser";
        req.email = "invalid-email"; // Invalid email
        req.firstName = "New";
        req.lastName = "User";

        // We expect a Bad Request (400) due to @Email validation failure on the DTO
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // --- Tests de l'Endpoint /me (Nécessite normalement une simulation de JWT/Auth) ---
    // Ces tests montrent que SANS simulation de sécurité (addFilters=false), l'appel échoue
    // car getAuthenticatedUserId lève une exception. C'est le comportement attendu.

    @Test
    void testGetCurrentUser_Unauthorized() throws Exception {
        // Since addFilters = false, getAuthenticatedUserId() throws UnsupportedOperationException
        // which the controller catches and returns 401 UNAUTHORIZED.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required."));
    }
}