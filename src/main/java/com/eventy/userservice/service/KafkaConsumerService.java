package com.eventy.userservice.service;

import com.eventy.userservice.event.PaymentValidatedEvent;
import com.eventy.userservice.model.User;
import com.eventy.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final UserRepository userRepository;

    @KafkaListener(topics = "payment-validated", groupId = "users-service-group")
    @Transactional
    public void handlePaymentValidated(PaymentValidatedEvent event) {
        log.info("Processing payment for transaction: {}", event.getTransactionId());

        // 1. Débiter l'acheteur (Si on gère un solde interne)
        updateUserBalance(event.getBuyerId(), BigDecimal.valueOf(event.getAmount()).negate(), "Buyer");

        // 2. Créditer le vendeur
        updateUserBalance(event.getVendorId(), BigDecimal.valueOf(event.getVendorAmount()), "Vendor");
    }

    private void updateUserBalance(java.util.UUID userId, BigDecimal amount, String userType) {
        userRepository.findById(userId).ifPresentOrElse(user -> {
            BigDecimal newBalance = user.getBalance().add(amount);
            user.setBalance(newBalance);
            userRepository.save(user);
            log.info("Updated {} balance (ID: {}). New Balance: {}", userType, userId, newBalance);
        }, () -> {
            log.error("{} not found with ID: {}", userType, userId);
            // Ici, on pourrait publier un événement d'erreur "TransferFailed"
        });
    }
}