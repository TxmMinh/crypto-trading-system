package com.example.service;

import com.example.dto.request.TradeRequest;
import com.example.dto.response.TradeResponse;
import com.example.entity.AggregatedPrice;
import com.example.entity.Trade;
import com.example.entity.User;
import com.example.entity.enums.TradeStatus;
import com.example.entity.enums.TradeType;
import com.example.exception.InvalidTradingPairException;
import com.example.exception.PriceNotFoundException;
import com.example.exception.UserNotFoundException;
import com.example.repository.AggregatedPriceRepository;
import com.example.repository.TradeRepository;
import com.example.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;
    private final AggregatedPriceRepository aggregatedPriceRepository;
    private final WalletService walletService;

    @Value("${crypto.supported-pairs}")
    private List<String> supportedPairs;

    /**
     * Execute a BUY or SELL trade
     */
    @Transactional
    public TradeResponse executeTrade(TradeRequest request) {
        String symbol = request.getSymbol().toUpperCase();

        // Validate trading pair
        if (!supportedPairs.contains(symbol)) {
            throw new InvalidTradingPairException(symbol);
        }

        // Validate user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId()));

        // Get latest best aggregated price
        AggregatedPrice aggregatedPrice = aggregatedPriceRepository.findBySymbol(symbol)
                .orElseThrow(() -> new PriceNotFoundException(symbol));

        // Determine base currency and quote currency from symbol
        String baseCurrency = symbol.replace("USDT", "");  // ETH or BTC
        String quoteCurrency = "USDT";

        BigDecimal quantity = request.getQuantity();
        BigDecimal price;
        BigDecimal totalAmount;

        // Execute trade logic
        if (request.getType() == TradeType.BUY) {
            // Use askPrice (lowest available sell price)
            price = aggregatedPrice.getAskPrice();
            totalAmount = quantity.multiply(price).setScale(8, RoundingMode.HALF_UP);

            log.info("BUY order: userId={}, symbol={}, qty={}, askPrice={}, totalUSDT={}",
                    user.getId(), symbol, quantity, price, totalAmount);

            // Debit USDT, then credit base currency
            walletService.debit(user.getId(), quoteCurrency, totalAmount);
            walletService.credit(user.getId(), baseCurrency, quantity);

        } else {
            // Use bidPrice (highest available buy price)
            price = aggregatedPrice.getBidPrice();
            totalAmount = quantity.multiply(price).setScale(8, RoundingMode.HALF_UP);

            log.info("SELL order: userId={}, symbol={}, qty={}, bidPrice={}, totalUSDT={}",
                    user.getId(), symbol, quantity, price, totalAmount);

            // Debit base currency, then credit USDT
            walletService.debit(user.getId(), baseCurrency, quantity);
            walletService.credit(user.getId(), quoteCurrency, totalAmount);
        }

        // Record trade history
        Trade trade = Trade.builder()
                .user(user)
                .symbol(symbol)
                .type(request.getType())
                .quantity(quantity)
                .price(price)
                .totalAmount(totalAmount)
                .status(TradeStatus.COMPLETED)
                .remarks(String.format("%s %s %s at price %s",
                        request.getType(), quantity, baseCurrency, price))
                .build();

        trade = tradeRepository.save(trade);
        log.info("Trade completed: tradeId={}", trade.getId());

        return toTradeResponse(trade);
    }

    /**
     * Query trade history
     */
    @Transactional(readOnly = true)
    public List<TradeResponse> getTradeHistory(Long userId) {
        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return tradeRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toTradeResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<TradeResponse> getTradeHistoryPaged(Long userId, int page, int size) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Pageable pageable = PageRequest.of(page, size);
        return tradeRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toTradeResponse);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getTradeHistoryBySymbol(Long userId, String symbol) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!supportedPairs.contains(symbol.toUpperCase())) {
            throw new InvalidTradingPairException(symbol);
        }

        return tradeRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(userId, symbol.toUpperCase())
                .stream()
                .map(this::toTradeResponse)
                .collect(Collectors.toList());
    }

    private TradeResponse toTradeResponse(Trade trade) {
        return TradeResponse.builder()
                .tradeId(trade.getId())
                .userId(trade.getUser().getId())
                .symbol(trade.getSymbol())
                .type(trade.getType())
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .totalAmount(trade.getTotalAmount())
                .status(trade.getStatus())
                .remarks(trade.getRemarks())
                .createdAt(trade.getCreatedAt())
                .build();
    }
}
