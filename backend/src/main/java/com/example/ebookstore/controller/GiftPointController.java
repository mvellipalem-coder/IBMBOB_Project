package com.example.ebookstore.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.domain.GiftPointTransactionType;
import com.example.ebookstore.dto.GiftPointBalanceResponse;
import com.example.ebookstore.dto.GiftPointTransactionPage;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.GiftPointService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Gift-point endpoints (operations getGiftPointBalance and listGiftPointTransactions in openapi.yaml). Read-only:
 * points are spent through checkout and earned on delivery, never through a call that names an amount.
 */
@RestController
@RequestMapping("/api/gift-points")
public class GiftPointController {

    private final GiftPointService giftPointService;

    public GiftPointController(GiftPointService giftPointService) {
        this.giftPointService = giftPointService;
    }

    /** {@code GET /api/gift-points/balance}. */
    @GetMapping("/balance")
    public GiftPointBalanceResponse getBalance(@AuthenticationPrincipal AuthenticatedUser user) {
        return giftPointService.getBalance(user.id());
    }

    /** {@code GET /api/gift-points/transactions} - the ledger, newest first. */
    @GetMapping("/transactions")
    public GiftPointTransactionPage listTransactions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) GiftPointTransactionType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return giftPointService.listTransactions(user.id(), type, page, size);
    }
}
