package com.example.controller;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.PriceResponse;
import com.example.service.PriceAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceAggregationService priceAggregationService;

    /**
     * GET /api/v1/prices
     * Returns best aggregated price for ALL supported pairs.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PriceResponse>>> getAllPrices() {
        List<PriceResponse> prices = priceAggregationService.getAllLatestPrices();
        return ResponseEntity.ok(ApiResponse.success(prices));
    }

    /**
     * GET /api/v1/prices/{symbol}
     * Returns best aggregated price for a specific pair (e.g. ETHUSDT, BTCUSDT).
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<PriceResponse>> getPriceBySymbol(
            @PathVariable String symbol) {
        PriceResponse price = priceAggregationService.getLatestPriceBySymbol(symbol);
        return ResponseEntity.ok(ApiResponse.success(price));
    }
}
