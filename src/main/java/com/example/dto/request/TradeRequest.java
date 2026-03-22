package com.example.dto.request;

import com.example.entity.enums.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Symbol is required (ETHUSDT or BTCUSDT)")
    private String symbol;

    @NotNull(message = "Trade type is required (BUY or SELL)")
    private TradeType type;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.00000001", message = "Quantity must be greater than 0")
    private BigDecimal quantity;
}
