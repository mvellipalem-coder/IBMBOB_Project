package com.example.ebookstore.service;

import com.example.ebookstore.domain.GiftPointTransactionType;
import com.example.ebookstore.dto.GiftPointBalanceResponse;
import com.example.ebookstore.dto.GiftPointTransactionPage;

/** Read-only gift-point use cases. Spending points happens inside checkout; there is no separate spend endpoint. */
public interface GiftPointService {

    /** The user's balance (read from the database) and the redemption rules checkout will apply. */
    GiftPointBalanceResponse getBalance(long userId);

    /** The user's ledger, newest first, optionally filtered by type. */
    GiftPointTransactionPage listTransactions(long userId, GiftPointTransactionType type, int page, int size);
}
