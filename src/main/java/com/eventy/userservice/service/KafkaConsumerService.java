package com.eventy.userservice.service;

import com.eventy.userservice.event.PaymentValidatedEvent;
import com.eventy.userservice.event.TransactionRefundedEvent;
import com.eventy.userservice.model.User;
import com.eventy.userservice.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-validated", groupId = "users-service-group")
    @Transactional
    public void handlePaymentValidated(String message) throws JsonProcessingException {
        PaymentValidatedEvent event = objectMapper.readValue(message, PaymentValidatedEvent.class);
        log.info("Processing payment for transaction: {}", event.getTransactionId());

        // 2. Créditer le vendeur
        if (event.getVendorId() != null) {
            updateUserBalance(event.getVendorId(), BigDecimal.valueOf(event.getVendorAmount()), "Vendor");
        } else {
            log.warn("Vendor ID is null for transaction {} (Possible fee-only transaction or error)", event.getTransactionId());
        }
    }

    @KafkaListener(topics = "transaction-refunded", groupId = "users-service-group")
    @Transactional
    public void handleTransactionRefunded(String message) throws JsonProcessingException {
        TransactionRefundedEvent event = objectMapper.readValue(message, TransactionRefundedEvent.class);
        log.info("Traitement du remboursement pour transaction : {}", event.getTransactionId());

        if (event.getVendorId() != null) {
            // On DÉBITE le vendeur (montant négatif)
            // Note: vendorAmount est positif dans l'event, on le soustrait
            BigDecimal amountToDebit = BigDecimal.valueOf(event.getVendorAmount()).negate();

            updateUserBalance(event.getVendorId(), amountToDebit, "Vendor (Refund)");
        }
    }

    private void updateUserBalance(UUID userId, BigDecimal amount, String userType) {
        // Double sécurité : ne jamais appeler findById avec null
        if (userId == null) return;

        userRepository.findById(userId).ifPresentOrElse(user -> {
            BigDecimal newBalance = user.getBalance().add(amount);
            user.setBalance(newBalance);
            userRepository.save(user);
            log.info("Updated {} balance (ID: {}). New Balance: {}", userType, userId, newBalance);
        }, () -> {
            log.error("{} not found with ID: {}", userType, userId);
        });
    }
}