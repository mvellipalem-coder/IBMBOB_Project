package com.example.ebookstore.mapper;

import org.springframework.stereotype.Component;

import com.example.ebookstore.dto.GiftPointTransactionResponse;
import com.example.ebookstore.entity.GiftPointTransaction;

/** Converts ledger entries to API responses. */
@Component
public class GiftPointMapper {

    public GiftPointTransactionResponse toResponse(GiftPointTransaction t) {
        return new GiftPointTransactionResponse(t.getId(), t.getType(), t.getPoints(), t.getBalanceAfter(),
                t.getOrderId(), t.getDescription(), t.getCreatedAt());
    }
}
