package com.example.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuobiTickerDto {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("bid")
    private BigDecimal bid;

    @JsonProperty("bidSize")
    private BigDecimal bidSize;

    @JsonProperty("ask")
    private BigDecimal ask;

    @JsonProperty("askSize")
    private BigDecimal askSize;
}
