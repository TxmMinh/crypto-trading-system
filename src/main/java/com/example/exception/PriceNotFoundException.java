package com.example.exception;

public class PriceNotFoundException extends RuntimeException {
    public PriceNotFoundException(String symbol) {
        super(String.format("No aggregated price found for symbol '%s'. Please wait for the scheduler to fetch prices.", symbol));
    }
}
