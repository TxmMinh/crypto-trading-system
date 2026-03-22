package com.example.controller;

import com.example.dto.request.TradeRequest;
import com.example.dto.response.ApiResponse;
import com.example.dto.response.TradeResponse;
import com.example.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /**
     * Execute a BUY or SELL trade at the latest best aggregated price.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TradeResponse>> executeTrade(@Valid @RequestBody TradeRequest request) {
        TradeResponse response = tradeService.executeTrade(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trade executed successfully", response));
    }

    /**
     * GET /api/v1/trades?userId=1
     * Get ALL trade history for a user (most recent first).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TradeResponse>>> getTradeHistory(
            @RequestParam Long userId) {
        List<TradeResponse> trades = tradeService.getTradeHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(trades));
    }

    /**
     * GET /api/v1/trades/paged?userId=1&page=0&size=10
     * Get paginated trade history.
     */
    @GetMapping("/paged")
    public ResponseEntity<ApiResponse<Page<TradeResponse>>> getTradeHistoryPaged(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TradeResponse> trades = tradeService.getTradeHistoryPaged(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(trades));
    }

    /**
     * GET /api/v1/trades/symbol?userId=1&symbol=ETHUSDT
     * Get trade history filtered by trading pair.
     */
    @GetMapping("/symbol")
    public ResponseEntity<ApiResponse<List<TradeResponse>>> getTradeHistoryBySymbol(
            @RequestParam Long userId,
            @RequestParam String symbol) {
        List<TradeResponse> trades = tradeService.getTradeHistoryBySymbol(userId, symbol);
        return ResponseEntity.ok(ApiResponse.success(trades));
    }

    /**
     * GET /api/v1/trades/{tradeId}
     * Get single trade detail by tradeId.
     */
    @GetMapping("/{tradeId}")
    public ResponseEntity<ApiResponse<TradeResponse>> getTradeById(
            @PathVariable Long tradeId,
            @RequestParam Long userId) {
        // Re-use history and filter — keeps it simple without extra repository method
        List<TradeResponse> trades = tradeService.getTradeHistory(userId);
        TradeResponse trade = trades.stream()
                .filter(t -> t.getTradeId().equals(tradeId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Trade not found with id: " + tradeId));
        return ResponseEntity.ok(ApiResponse.success(trade));
    }
}
