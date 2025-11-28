package com.eventy.userservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRefundedEvent {
    private UUID transactionId;
    private UUID ticketId;
    private UUID vendorId;
    private Double vendorAmount; // Montant à retirer au vendeur
}
