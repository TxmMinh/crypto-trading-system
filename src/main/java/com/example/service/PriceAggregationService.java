package com.example.service;

import com.example.dto.external.BinanceBookTickerDto;
import com.example.dto.external.HuobiResponseDto;
import com.example.dto.external.HuobiTickerDto;
import com.example.dto.response.PriceResponse;
import com.example.entity.AggregatedPrice;
import com.example.exception.InvalidTradingPairException;
import com.example.exception.PriceNotFoundException;
import com.example.repository.AggregatedPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAggregationService {

    private static final String SOURCE_BINANCE = "BINANCE";
    private static final String SOURCE_HUOBI = "HUOBI";

    private final RestTemplate restTemplate;
    private final AggregatedPriceRepository aggregatedPriceRepository;

    @Value("${crypto.binance.url}")
    private String binanceUrl;

    @Value("${crypto.huobi.url}")
    private String huobiUrl;

    @Value("${crypto.supported-pairs}")
    private List<String> supportedPairs;

    /**
     * Called by Scheduler every 10 seconds
     */
    public void fetchAndStoreBestPrices() {
        Map<String, BinanceBookTickerDto> binancePrices = fetchBinancePrices();
        Map<String, HuobiTickerDto> huobiPrices = fetchHuobiPrices();

        for (String symbol : supportedPairs) {
            try {
                aggregateBestPrice(symbol, binancePrices, huobiPrices);
            } catch (Exception e) {
                log.error("Failed to aggregate price for symbol {}: {}", symbol, e.getMessage());
            }
        }
    }

    /**
     * Fetch from Binance
     */
    private Map<String, BinanceBookTickerDto> fetchBinancePrices() {
        try {
            BinanceBookTickerDto[] response =
                    restTemplate.getForObject(binanceUrl, BinanceBookTickerDto[].class);

            if (response == null) {
                log.warn("Binance returned null response");
                return Collections.emptyMap();
            }

            // Filter only supported pairs and index by symbol
            Map<String, BinanceBookTickerDto> result = Arrays.stream(response)
                    .filter(t -> supportedPairs.contains(t.getSymbol()))
                    .collect(Collectors.toMap(BinanceBookTickerDto::getSymbol, t -> t));

            log.info("Fetched {} supported pairs from Binance", result.size());
            return result;

        } catch (Exception e) {
            log.warn("Failed to fetch prices from Binance: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Fetch from Huobi
     */
    private Map<String, HuobiTickerDto> fetchHuobiPrices() {
        try {
            HuobiResponseDto response =
                    restTemplate.getForObject(huobiUrl, HuobiResponseDto.class);

            if (response == null || response.getData() == null) {
                log.warn("Huobi returned null or empty response");
                return Collections.emptyMap();
            }

            Map<String, HuobiTickerDto> result = response.getData().stream()
                    .filter(t -> supportedPairs.contains(t.getSymbol().toUpperCase()))
                    .collect(Collectors.toMap(
                            t -> t.getSymbol().toUpperCase(),
                            t -> t
                    ));

            log.info("Fetched {} supported pairs from Huobi", result.size());
            return result;

        } catch (Exception e) {
            log.warn("Failed to fetch prices from Huobi: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Core aggregation logic
     */
    private void aggregateBestPrice(String symbol, Map<String, BinanceBookTickerDto> binancePrices, Map<String, HuobiTickerDto> huobiPrices) {
        BinanceBookTickerDto binance = binancePrices.get(symbol);
        HuobiTickerDto huobi = huobiPrices.get(symbol);

        if (binance == null && huobi == null) {
            log.warn("No price data available from either exchange for {}", symbol);
            return;
        }

        // Determine best BID (highest)
        BigDecimal bestBid = null;
        String bidSource = null;

        if (binance != null && huobi != null) {
            if (binance.getBidPrice().compareTo(huobi.getBid()) >= 0) {
                bestBid = binance.getBidPrice();
                bidSource = SOURCE_BINANCE;
            } else {
                bestBid = huobi.getBid();
                bidSource = SOURCE_HUOBI;
            }
        } else if (binance != null) {
            bestBid = binance.getBidPrice();
            bidSource = SOURCE_BINANCE;
        } else {
            bestBid = huobi.getBid();
            bidSource = SOURCE_HUOBI;
        }

        // Determine best ASK (lowest)
        BigDecimal bestAsk = null;
        String askSource = null;

        if (binance != null && huobi != null) {
            if (binance.getAskPrice().compareTo(huobi.getAsk()) <= 0) {
                bestAsk = binance.getAskPrice();
                askSource = SOURCE_BINANCE;
            } else {
                bestAsk = huobi.getAsk();
                askSource = SOURCE_HUOBI;
            }
        } else if (binance != null) {
            bestAsk = binance.getAskPrice();
            askSource = SOURCE_BINANCE;
        } else {
            bestAsk = huobi.getAsk();
            askSource = SOURCE_HUOBI;
        }

        // Upsert into DB
        AggregatedPrice aggregatedPrice = aggregatedPriceRepository
                .findBySymbol(symbol)
                .orElse(AggregatedPrice.builder().symbol(symbol).build());

        aggregatedPrice.setBidPrice(bestBid);
        aggregatedPrice.setBidSource(bidSource);
        aggregatedPrice.setAskPrice(bestAsk);
        aggregatedPrice.setAskSource(askSource);

        aggregatedPriceRepository.save(aggregatedPrice);

        log.info("[{}] BestBid={} ({}), BestAsk={} ({})", symbol, bestBid, bidSource, bestAsk, askSource);
    }

    /**
     * retrieve stored prices
     */
    public List<PriceResponse> getAllLatestPrices() {
        return aggregatedPriceRepository.findAllByOrderBySymbolAsc()
                .stream()
                .map(this::toPriceResponse)
                .collect(Collectors.toList());
    }

    /**
     * retrieve stored prices by symbol
     */
    public PriceResponse getLatestPriceBySymbol(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        validateSymbol(upperSymbol);
        AggregatedPrice price = aggregatedPriceRepository.findBySymbol(upperSymbol)
                .orElseThrow(() -> new PriceNotFoundException(upperSymbol));
        return toPriceResponse(price);
    }

    private PriceResponse toPriceResponse(AggregatedPrice p) {
        return PriceResponse.builder()
                .symbol(p.getSymbol())
                .bidPrice(p.getBidPrice())
                .bidSource(p.getBidSource())
                .askPrice(p.getAskPrice())
                .askSource(p.getAskSource())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    public void validateSymbol(String symbol) {
        if (!supportedPairs.contains(symbol.toUpperCase())) {
            throw new InvalidTradingPairException(symbol);
        }
    }
}
