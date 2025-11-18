package com.eventy.userservice.service;

import com.eventy.userservice.model.User;
import com.eventy.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public Optional<User> updateUser(UUID id, User userDetails) {
        return userRepository.findById(id).map(user -> {
            user.setUsername(userDetails.getUsername());
            user.setEmail(userDetails.getEmail());
            user.setFirstName(userDetails.getFirstName());
            user.setLastName(userDetails.getLastName());
            user.setAvatarUrl(userDetails.getAvatarUrl());
            user.setCreationDate(userDetails.getCreationDate());
            user.setStatus(userDetails.getStatus());
            user.setBalance(userDetails.getBalance());
            user.setRole(userDetails.getRole());
            return userRepository.save(user);
        });
    }

    public boolean suspendUser(UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setStatus(User.Status.SUSPENDED);
            userRepository.save(user);
            return true;
        }).orElse(false);
    }

    public void deleteUser(UUID id) {
        userRepository.deleteById(id);
    }
}
