package com.example.ebookstore.dto;

import java.math.BigDecimal;

/**
 * The balance and the rules checkout will apply (schema {@code GiftPointBalance}).
 *
 * @param balance                    points available to redeem now
 * @param pointValue                 money value of one point
 * @param maxRedeemPercentOfSubtotal highest share of an order's subtotal that points may pay for
 * @param earnAmountPerPoint         amount paid that earns one point (credited on delivery)
 */
public record GiftPointBalanceResponse(
        int balance,
        BigDecimal pointValue,
        int maxRedeemPercentOfSubtotal,
        BigDecimal earnAmountPerPoint) {
}
