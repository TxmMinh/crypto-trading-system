package com.example.controller;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.WalletResponse;
import com.example.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * GET /api/v1/wallet?userId=1
     * Returns all crypto currency balances for the user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WalletResponse>>> getWalletBalance(@RequestParam Long userId) {
        List<WalletResponse> wallets = walletService.getWalletsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(wallets));
    }
}
