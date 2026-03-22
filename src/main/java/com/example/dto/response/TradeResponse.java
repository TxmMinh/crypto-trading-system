package com.example.dto.response;

import com.example.entity.enums.TradeStatus;
import com.example.entity.enums.TradeType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TradeResponse {

    private Long tradeId;
    private Long userId;
    private String symbol;
    private TradeType type;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private TradeStatus status;
    private String remarks;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
