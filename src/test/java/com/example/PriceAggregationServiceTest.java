package com.example;

import com.example.dto.external.BinanceBookTickerDto;
import com.example.dto.external.HuobiResponseDto;
import com.example.dto.external.HuobiTickerDto;
import com.example.dto.response.PriceResponse;
import com.example.entity.AggregatedPrice;
import com.example.exception.InvalidTradingPairException;
import com.example.exception.PriceNotFoundException;
import com.example.repository.AggregatedPriceRepository;
import com.example.service.PriceAggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceAggregationService Unit Tests")
class PriceAggregationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AggregatedPriceRepository aggregatedPriceRepository;

    @InjectMocks
    private PriceAggregationService priceAggregationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(priceAggregationService, "binanceUrl",
                "https://api.binance.com/api/v3/ticker/bookTicker");
        ReflectionTestUtils.setField(priceAggregationService, "huobiUrl",
                "https://api.huobi.pro/market/tickers");
        ReflectionTestUtils.setField(priceAggregationService, "supportedPairs",
                List.of("ETHUSDT", "BTCUSDT"));
    }

    @Test
    @DisplayName("Best bid = MAX(binance, huobi), Best ask = MIN(binance, huobi)")
    void fetchAndStore_selectsBestBidAndBestAsk() {
        BinanceBookTickerDto binanceTicker = new BinanceBookTickerDto();
        binanceTicker.setSymbol("BTCUSDT");
        binanceTicker.setBidPrice(new BigDecimal("70558.00"));
        binanceTicker.setAskPrice(new BigDecimal("70559.00"));

        HuobiTickerDto huobiTicker = new HuobiTickerDto();
        huobiTicker.setSymbol("btcusdt");
        huobiTicker.setBid(new BigDecimal("70642.00"));
        huobiTicker.setAsk(new BigDecimal("70643.00"));

        HuobiResponseDto huobiResponse = new HuobiResponseDto();
        huobiResponse.setStatus("ok");
        huobiResponse.setData(List.of(huobiTicker));

        BinanceBookTickerDto ethTicker = new BinanceBookTickerDto();
        ethTicker.setSymbol("ETHUSDT");
        ethTicker.setBidPrice(new BigDecimal("3000.00"));
        ethTicker.setAskPrice(new BigDecimal("3001.00"));

        when(restTemplate.getForObject(contains("binance"), eq(BinanceBookTickerDto[].class)))
                .thenReturn(new BinanceBookTickerDto[]{binanceTicker, ethTicker});
        when(restTemplate.getForObject(contains("huobi"), eq(HuobiResponseDto.class)))
                .thenReturn(huobiResponse);
        when(aggregatedPriceRepository.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(aggregatedPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        priceAggregationService.fetchAndStoreBestPrices();

        // Capture what was saved
        ArgumentCaptor<AggregatedPrice> captor = ArgumentCaptor.forClass(AggregatedPrice.class);
        verify(aggregatedPriceRepository, atLeastOnce()).save(captor.capture());

        AggregatedPrice saved = captor.getAllValues().stream()
                .filter(p -> "BTCUSDT".equals(p.getSymbol()))
                .findFirst()
                .orElseThrow();

        // Best BID = Huobi 70642 (higher than Binance 70558)
        assertThat(saved.getBidPrice()).isEqualByComparingTo("70642.00");
        assertThat(saved.getBidSource()).isEqualTo("HUOBI");

        // Best ASK = Binance 70559 (lower than Huobi 70643)
        assertThat(saved.getAskPrice()).isEqualByComparingTo("70559.00");
        assertThat(saved.getAskSource()).isEqualTo("BINANCE");
    }

    @Test
    @DisplayName("When Binance fails, still stores prices from Huobi alone")
    void fetchAndStore_binanceFails_usesHuobiOnly() {
        HuobiTickerDto huobiEth = new HuobiTickerDto();
        huobiEth.setSymbol("ethusdt");
        huobiEth.setBid(new BigDecimal("3100.00"));
        huobiEth.setAsk(new BigDecimal("3101.00"));

        HuobiTickerDto huobiBtc = new HuobiTickerDto();
        huobiBtc.setSymbol("btcusdt");
        huobiBtc.setBid(new BigDecimal("70000.00"));
        huobiBtc.setAsk(new BigDecimal("70001.00"));

        HuobiResponseDto huobiResponse = new HuobiResponseDto();
        huobiResponse.setStatus("ok");
        huobiResponse.setData(List.of(huobiEth, huobiBtc));

        // Binance throws exception (network error)
        when(restTemplate.getForObject(contains("binance"), eq(BinanceBookTickerDto[].class)))
                .thenThrow(new RuntimeException("Connection refused"));
        when(restTemplate.getForObject(contains("huobi"), eq(HuobiResponseDto.class)))
                .thenReturn(huobiResponse);
        when(aggregatedPriceRepository.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(aggregatedPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should NOT throw — scheduler must not die
        assertThatCode(() -> priceAggregationService.fetchAndStoreBestPrices())
                .doesNotThrowAnyException();

        // Should still save prices from Huobi
        ArgumentCaptor<AggregatedPrice> captor = ArgumentCaptor.forClass(AggregatedPrice.class);
        verify(aggregatedPriceRepository, atLeastOnce()).save(captor.capture());

        AggregatedPrice ethPrice = captor.getAllValues().stream()
                .filter(p -> "ETHUSDT".equals(p.getSymbol()))
                .findFirst().orElseThrow();

        assertThat(ethPrice.getBidPrice()).isEqualByComparingTo("3100.00");
        assertThat(ethPrice.getBidSource()).isEqualTo("HUOBI");
    }

    @Test
    @DisplayName("When both exchanges fail, no prices saved but no exception thrown")
    void fetchAndStore_bothFail_noSave_noException() {
        when(restTemplate.getForObject(contains("binance"), eq(BinanceBookTickerDto[].class)))
                .thenThrow(new RuntimeException("Binance down"));
        when(restTemplate.getForObject(contains("huobi"), eq(HuobiResponseDto.class)))
                .thenThrow(new RuntimeException("Huobi down"));

        assertThatCode(() -> priceAggregationService.fetchAndStoreBestPrices())
                .doesNotThrowAnyException();

        verify(aggregatedPriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("getLatestPriceBySymbol - returns price response")
    void getLatestPrice_success() {
        AggregatedPrice price = AggregatedPrice.builder()
                .id(1L).symbol("ETHUSDT")
                .bidPrice(new BigDecimal("3000.00")).bidSource("HUOBI")
                .askPrice(new BigDecimal("3001.00")).askSource("BINANCE")
                .updatedAt(LocalDateTime.now()).build();

        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.of(price));

        PriceResponse response = priceAggregationService.getLatestPriceBySymbol("ETHUSDT");

        assertThat(response.getSymbol()).isEqualTo("ETHUSDT");
        assertThat(response.getBidPrice()).isEqualByComparingTo("3000.00");
        assertThat(response.getAskPrice()).isEqualByComparingTo("3001.00");
    }

    @Test
    @DisplayName("getLatestPriceBySymbol - throws PriceNotFoundException when no data")
    void getLatestPrice_notFound_throwsException() {
        when(aggregatedPriceRepository.findBySymbol("ETHUSDT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceAggregationService.getLatestPriceBySymbol("ETHUSDT"))
                .isInstanceOf(PriceNotFoundException.class)
                .hasMessageContaining("ETHUSDT");
    }

    @Test
    @DisplayName("getLatestPriceBySymbol - throws InvalidTradingPairException for DOGEUSDT")
    void getLatestPrice_invalidSymbol_throwsException() {
        assertThatThrownBy(() -> priceAggregationService.getLatestPriceBySymbol("DOGEUSDT"))
                .isInstanceOf(InvalidTradingPairException.class)
                .hasMessageContaining("DOGEUSDT");
    }
}
