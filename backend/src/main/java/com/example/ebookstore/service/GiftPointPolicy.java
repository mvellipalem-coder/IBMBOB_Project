package com.example.ebookstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.example.ebookstore.config.BusinessProperties;

/**
 * The gift-point rules in one place (all numbers come from {@link BusinessProperties}, nothing is hard-coded).
 * Documented in {@code docs/CHECKOUT_AND_GIFT_POINTS.md}.
 *
 * <ul>
 *   <li><b>Redeeming:</b> one point is worth {@code pointValue} money (demo default 1.00). Points may pay for at most
 *       {@code maxRedeemPercentOfSubtotal} percent of the subtotal (demo default 50), rounded DOWN to whole points.</li>
 *   <li><b>Earning:</b> one point per {@code earnAmountPerPoint} of the amount actually paid (demo default 10.00),
 *       rounded DOWN, credited only when the order is delivered.</li>
 * </ul>
 * Rounding down always favours the store, and the discount is always a whole number of points times the point value,
 * so it can never exceed the cap.
 */
@Component
public class GiftPointPolicy {

    private final BusinessProperties business;

    public GiftPointPolicy(BusinessProperties business) {
        this.business = business;
    }

    /** Most points that may be redeemed on an order with this subtotal (ignores the user's balance). */
    public int maxRedeemablePoints(BigDecimal subtotal) {
        BigDecimal maxMoney = subtotal
                .multiply(BigDecimal.valueOf(business.maxRedeemPercentOfSubtotal()))
                .divide(BigDecimal.valueOf(100));
        return clampToInt(maxMoney.divide(business.pointValue(), 0, RoundingMode.DOWN));
    }

    /** Money taken off the order for this many points (2 decimal places). */
    public BigDecimal discountFor(int points) {
        return business.pointValue().multiply(BigDecimal.valueOf(points)).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Points an order of this paid amount earns on delivery. */
    public int pointsEarnedFor(BigDecimal totalAmount) {
        return clampToInt(totalAmount.divide(business.earnAmountPerPoint(), 0, RoundingMode.DOWN));
    }

    private static int clampToInt(BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : value.intValue();
    }
}
