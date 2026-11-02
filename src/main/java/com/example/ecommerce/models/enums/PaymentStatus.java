package com.example.ecommerce.models.enums;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == COMPLETED || next == FAILED;
            case COMPLETED -> next == REFUNDED;
            case FAILED, REFUNDED -> false;
        };
    }
}
