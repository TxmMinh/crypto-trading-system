package com.example;

import com.example.dto.request.TradeRequest;
import com.example.dto.response.TradeResponse;
import com.example.entity.AggregatedPrice;
import com.example.entity.Trade;
import com.example.entity.User;
import com.example.entity.enums.TradeStatus;
import com.example.entity.enums.TradeType;
import com.example.exception.InsufficientBalanceException;
import com.example.exception.InvalidTradingPairException;
import com.example.exception.PriceNotFoundException;
import com.example.exception.UserNotFoundException;
import com.example.repository.AggregatedPriceRepository;
import com.example.repository.TradeRepository;
import com.example.repository.UserRepository;
import com.example.service.TradeService;
import com.example.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeService Unit Tests")
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AggregatedPriceRepository aggregatedPriceRepository;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private TradeService tradeService;

    private User mockUser;
    private AggregatedPrice mockEthPrice;

    @BeforeEach
    void setUp() {
        // Inject supported pairs list
        ReflectionTestUtils.setField(tradeService, "supportedPairs", List.of("ETHUSDT", "BTCUSDT"));

        mockUser = User.builder()
                .id(1L)
                .username("trader01")
                .email("trader01@crypto.com")
                .createdAt(LocalDateTime.now())
                .build();

        mockEthPrice = AggregatedPrice.builder()
                .id(1L)
                .symbol("ETHUSDT")
                .bidPrice(new BigDecimal("3000.00"))
                .askPrice(new BigDecimal("3001.00"))
                .bidSource("HUOBI")
                .askSource("BINANCE")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("BUY ETHUSDT - success: debit USDT, credit ETH")
    void buyEth_success() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("ETHUSDT");
        request.setType(TradeType.BUY);
        request.setQuantity(new BigDecimal("2.0"));

        Trade savedTrade = Trade.builder()
                .id(100L)
                .user(mockUser)
                .symbol("ETHUSDT")
                .type(TradeType.BUY)
                .quantity(new BigDecimal("2.0"))
                .price(new BigDecimal("3001.00"))
                .totalAmount(new BigDecimal("6002.00000000"))
                .status(TradeStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.of(mockEthPrice));
        when(tradeRepository.save(any(Trade.class))).thenReturn(savedTrade);

        // Act
        TradeResponse response = tradeService.executeTrade(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTradeId()).isEqualTo(100L);
        assertThat(response.getType()).isEqualTo(TradeType.BUY);
        assertThat(response.getPrice()).isEqualByComparingTo("3001.00");
        assertThat(response.getStatus()).isEqualTo(TradeStatus.COMPLETED);

        // Verify
        verify(walletService).debit(1L, "USDT", new BigDecimal("6002.00000000"));
        verify(walletService).credit(1L, "ETH",  new BigDecimal("2.0"));
    }

    @Test
    @DisplayName("SELL ETHUSDT - success: debit ETH, credit USDT")
    void sellEth_success() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("ETHUSDT");
        request.setType(TradeType.SELL);
        request.setQuantity(new BigDecimal("1.5"));

        Trade savedTrade = Trade.builder()
                .id(101L)
                .user(mockUser)
                .symbol("ETHUSDT")
                .type(TradeType.SELL)
                .quantity(new BigDecimal("1.5"))
                .price(new BigDecimal("3000.00"))
                .totalAmount(new BigDecimal("4500.00000000"))
                .status(TradeStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.of(mockEthPrice));
        when(tradeRepository.save(any(Trade.class))).thenReturn(savedTrade);

        // Act
        TradeResponse response = tradeService.executeTrade(request);

        // Assert
        assertThat(response.getType()).isEqualTo(TradeType.SELL);
        assertThat(response.getPrice()).isEqualByComparingTo("3000.00");

        // Verify
        verify(walletService).debit(1L, "ETH",  new BigDecimal("1.5"));
        verify(walletService).credit(1L, "USDT", new BigDecimal("4500.00000000"));
    }

    @Test
    @DisplayName("BUY - throws InvalidTradingPairException for unsupported symbol")
    void trade_invalidSymbol_throwsException() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("DOGEUSDT");
        request.setType(TradeType.BUY);
        request.setQuantity(BigDecimal.ONE);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(InvalidTradingPairException.class)
                .hasMessageContaining("DOGEUSDT");

        verifyNoInteractions(walletService, tradeRepository);
    }

    @Test
    @DisplayName("BUY - throws UserNotFoundException when user does not exist")
    void trade_userNotFound_throwsException() {
        TradeRequest request = new TradeRequest();
        request.setUserId(99L);
        request.setSymbol("ETHUSDT");
        request.setType(TradeType.BUY);
        request.setQuantity(BigDecimal.ONE);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("BUY - throws PriceNotFoundException when no price aggregated yet")
    void trade_priceNotFound_throwsException() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("BTCUSDT");
        request.setType(TradeType.BUY);
        request.setQuantity(BigDecimal.ONE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(aggregatedPriceRepository.findBySymbol("BTCUSDT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(PriceNotFoundException.class)
                .hasMessageContaining("BTCUSDT");
    }

    @Test
    @DisplayName("BUY - throws InsufficientBalanceException when USDT not enough")
    void buyEth_insufficientUsdt_throwsException() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("ETHUSDT");
        request.setType(TradeType.BUY);
        request.setQuantity(new BigDecimal("100.0"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.of(mockEthPrice));

        // WalletService debit throws because balance is insufficient
        doThrow(new InsufficientBalanceException("USDT", "300100.00", "50000.00"))
                .when(walletService).debit(eq(1L), eq("USDT"), any());

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("USDT")
                .hasMessageContaining("300100.00")
                .hasMessageContaining("50000.00");

        // Ensure credit was NOT called (transaction rolled back)
        verify(walletService, never()).credit(anyLong(), anyString(), any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("SELL - throws InsufficientBalanceException when ETH not enough")
    void sellEth_insufficientEth_throwsException() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("ETHUSDT");
        request.setType(TradeType.SELL);
        request.setQuantity(new BigDecimal("10.0"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.of(mockEthPrice));

        doThrow(new InsufficientBalanceException("ETH", "10.0", "0.0"))
                .when(walletService).debit(eq(1L), eq("ETH"), any());

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("ETH");

        verify(walletService, never()).credit(anyLong(), anyString(), any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Symbol is case-insensitive - ethusdt treated same as ETHUSDT")
    void trade_symbolCaseInsensitive() {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setSymbol("ethusdt");
        request.setType(TradeType.BUY);
        request.setQuantity(new BigDecimal("1.0"));

        Trade savedTrade = Trade.builder()
                .id(102L).user(mockUser).symbol("ETHUSDT")
                .type(TradeType.BUY).quantity(new BigDecimal("1.0"))
                .price(new BigDecimal("3001.00"))
                .totalAmount(new BigDecimal("3001.00000000"))
                .status(TradeStatus.COMPLETED).createdAt(LocalDateTime.now()).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.of(mockEthPrice));
        when(tradeRepository.save(any())).thenReturn(savedTrade);

        TradeResponse response = tradeService.executeTrade(request);
        assertThat(response.getSymbol()).isEqualTo("ETHUSDT");
    }
}
