package com.example.service;

import com.example.dto.response.WalletResponse;
import com.example.entity.User;
import com.example.entity.Wallet;
import com.example.exception.InsufficientBalanceException;
import com.example.exception.UserNotFoundException;
import com.example.repository.UserRepository;
import com.example.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    /**
     * Get all wallet balances for a user.
     */
    @Transactional(readOnly = true)
    public List<WalletResponse> getWalletsByUserId(Long userId) {
        // Verify user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return walletRepository.findByUserId(userId)
                .stream()
                .map(this::toWalletResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get balance for a specific currency.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId, String currency) {
        return walletRepository.findByUserIdAndCurrency(userId, currency)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Debit (subtract) from a wallet.
     * Acquires pessimistic lock to prevent race conditions.
     */
    @Transactional
    public void debit(Long userId, String currency, BigDecimal amount) {
        Wallet wallet = walletRepository
                .findByUserIdAndCurrencyWithLock(userId, currency)
                .orElseThrow(() -> new RuntimeException(
                        "Wallet not found for user " + userId + " currency " + currency));

        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException(
                    currency,
                    amount.toPlainString(),
                    wallet.getBalance().toPlainString()
            );
        }
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        log.debug("Debited {} {} from userId={}. New balance: {}", amount, currency, userId, newBalance);
    }

    /**
     * Credit (add) to a wallet.
     * Creates wallet entry if it doesn't exist yet.
     */
    @Transactional
    public void credit(Long userId, String currency, BigDecimal amount) {
        Wallet wallet = walletRepository
                .findByUserIdAndCurrencyWithLock(userId, currency)
                .orElseGet(() -> createWallet(userId, currency));

        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        log.debug("Credited {} {} to userId={}. New balance: {}", amount, currency, userId, newBalance);
    }

    private Wallet createWallet(Long userId, String currency) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return Wallet.builder()
                .user(user)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .build();
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUser().getId())
                .currency(wallet.getCurrency())
                .balance(wallet.getBalance())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
