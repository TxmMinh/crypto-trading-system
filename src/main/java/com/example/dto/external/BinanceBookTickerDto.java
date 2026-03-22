package com.example.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceBookTickerDto {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("bidPrice")
    private BigDecimal bidPrice;

    @JsonProperty("bidQty")
    private BigDecimal bidQty;

    @JsonProperty("askPrice")
    private BigDecimal askPrice;

    @JsonProperty("askQty")
    private BigDecimal askQty;
}
