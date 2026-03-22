package com.example.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String currency, String required, String available) {
        super(String.format("Insufficient %s balance. Required: %s, Available: %s", currency, required, available));
    }
}
