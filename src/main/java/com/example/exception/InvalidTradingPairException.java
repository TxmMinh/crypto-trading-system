package com.example.exception;

public class InvalidTradingPairException extends RuntimeException {
    public InvalidTradingPairException(String symbol) {
        super(String.format("Trading pair '%s' is not supported. Supported pairs: ETHUSDT, BTCUSDT", symbol));
    }
}
