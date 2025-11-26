package com.eventy.userservice.service;

import com.eventy.userservice.model.User;
import com.eventy.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private User baseUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setUsername("john");
        u.setEmail("john@example.com");
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setAvatarUrl(null);
        u.setCreationDate(LocalDate.now());
        u.setBalance(BigDecimal.ZERO);
        u.setStatus(User.Status.ACTIVE);
        u.setRole(User.Role.USER);
        return u;
    }

    @Test
    void getAllUsers_returnsList() {
        User user = baseUser(UUID.randomUUID());
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<User> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("john");
    }

    @Test
    void getUserById_found() {
        UUID id = UUID.randomUUID();
        User user = baseUser(id);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        Optional<User> result = userService.getUserById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    void getUserById_notFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        Optional<User> result = userService.getUserById(id);

        assertThat(result).isEmpty();
    }

    @Test
    void createUser_savesUser() {
        User u = baseUser(UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(u);

        User saved = userService.createUser(u);

        assertThat(saved.getUsername()).isEqualTo("john");
        verify(userRepository, times(1)).save(u);
    }

    @Test
    void updateUser_updatesFields() {
        UUID id = UUID.randomUUID();
        User existing = baseUser(id);
        User updates = baseUser(id);
        updates.setUsername("new_username");
        updates.setEmail("new@mail.com");

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenReturn(updates);

        Optional<User> result = userService.updateUser(id, updates);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("new_username");
        assertThat(result.get().getEmail()).isEqualTo("new@mail.com");
    }

    @Test
    void updateUser_returnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        User updates = baseUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        Optional<User> result = userService.updateUser(id, updates);

        assertThat(result).isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    void suspendUser_setsStatusSuspended() {
        UUID id = UUID.randomUUID();
        User existing = baseUser(id);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        boolean result = userService.suspendUser(id);

        assertThat(result).isTrue();
        assertThat(existing.getStatus()).isEqualTo(User.Status.SUSPENDED);
        verify(userRepository, times(1)).save(existing);
    }

    @Test
    void suspendUser_returnsFalseIfNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        boolean result = userService.suspendUser(id);

        assertThat(result).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_callsRepository() {
        UUID id = UUID.randomUUID();

        userService.deleteUser(id);

        verify(userRepository, times(1)).deleteById(id);
    }
}
