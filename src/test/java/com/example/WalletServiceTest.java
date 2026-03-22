package com.example;

import com.example.dto.response.WalletResponse;
import com.example.entity.User;
import com.example.entity.Wallet;
import com.example.exception.InsufficientBalanceException;
import com.example.exception.UserNotFoundException;
import com.example.repository.UserRepository;
import com.example.repository.WalletRepository;
import com.example.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletService walletService;

    private User mockUser;
    private Wallet usdtWallet;
    private Wallet ethWallet;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L).username("trader01")
                .email("trader01@crypto.com")
                .createdAt(LocalDateTime.now()).build();

        usdtWallet = Wallet.builder()
                .id(1L).user(mockUser)
                .currency("USDT")
                .balance(new BigDecimal("50000.00"))
                .updatedAt(LocalDateTime.now()).build();

        ethWallet = Wallet.builder()
                .id(2L).user(mockUser)
                .currency("ETH")
                .balance(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("getWalletsByUserId - returns all wallets for user")
    void getWallets_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(usdtWallet, ethWallet));

        List<WalletResponse> result = walletService.getWalletsByUserId(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WalletResponse::getCurrency)
                .containsExactlyInAnyOrder("USDT", "ETH");
    }

    @Test
    @DisplayName("getWalletsByUserId - throws UserNotFoundException when user missing")
    void getWallets_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWalletsByUserId(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("debit - reduces balance correctly")
    void debit_success() {
        when(walletRepository.findByUserIdAndCurrencyWithLock(1L, "USDT"))
                .thenReturn(Optional.of(usdtWallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.debit(1L, "USDT", new BigDecimal("1000.00"));

        assertThat(usdtWallet.getBalance()).isEqualByComparingTo("49000.00");
        verify(walletRepository).save(usdtWallet);
    }

    @Test
    @DisplayName("debit - throws InsufficientBalanceException when balance too low")
    void debit_insufficientBalance() {
        when(walletRepository.findByUserIdAndCurrencyWithLock(1L, "ETH"))
                .thenReturn(Optional.of(ethWallet));   // balance = 0

        assertThatThrownBy(() -> walletService.debit(1L, "ETH", new BigDecimal("1.0")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("ETH")
                .hasMessageContaining("1.0")
                .hasMessageContaining("0");

        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit - increases balance correctly")
    void credit_success() {
        when(walletRepository.findByUserIdAndCurrencyWithLock(1L, "ETH"))
                .thenReturn(Optional.of(ethWallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.credit(1L, "ETH", new BigDecimal("2.5"));

        assertThat(ethWallet.getBalance()).isEqualByComparingTo("2.5");
        verify(walletRepository).save(ethWallet);
    }

    @Test
    @DisplayName("credit - creates new wallet if currency does not exist yet")
    void credit_createsWalletIfMissing() {
        when(walletRepository.findByUserIdAndCurrencyWithLock(1L, "BTC"))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.credit(1L, "BTC", new BigDecimal("0.5"));

        verify(walletRepository).save(argThat(w ->
                w.getCurrency().equals("BTC") &&
                w.getBalance().compareTo(new BigDecimal("0.5")) == 0
        ));
    }
}
