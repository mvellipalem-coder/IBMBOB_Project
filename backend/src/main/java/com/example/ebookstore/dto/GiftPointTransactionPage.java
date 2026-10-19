package com.example.ebookstore.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** One page of ledger entries (schema {@code GiftPointTransactionPage}): the paging information, then the entries. */
public record GiftPointTransactionPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<GiftPointTransactionResponse> content) {

    public static GiftPointTransactionPage of(Page<GiftPointTransactionResponse> page) {
        return new GiftPointTransactionPage(page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), page.getContent());
    }
}
