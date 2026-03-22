package com.example.scheduler;

import com.example.service.PriceAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceScheduler {

    private final PriceAggregationService priceAggregationService;

    /**
     * Fetches best prices from Binance and Huobi every 10 seconds.
     * fixedDelay  = wait 10s AFTER the previous run finishes
     * initialDelay = wait 1s on startup before first run
     */
    @Scheduled(fixedDelayString = "${crypto.scheduler.interval-ms:10000}",
               initialDelayString = "${crypto.scheduler.initial-delay-ms:1000}")
    public void aggregatePrices() {
        log.info("=== Price aggregation started at {} ===", LocalDateTime.now());
        try {
            priceAggregationService.fetchAndStoreBestPrices();
            log.info("=== Price aggregation completed successfully ===");
        } catch (Exception e) {
            log.error("=== Price aggregation failed: {} ===", e.getMessage(), e);
        }
    }
}
