package com.example.ebookstore.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.ebookstore.config.BusinessProperties;

/** The conversion and cap rules, with the documented demo defaults and with other configurations. */
class GiftPointPolicyTest {

    private static GiftPointPolicy policy(String pointValue, int percent, String earnAmount) {
        return new GiftPointPolicy(new BusinessProperties(10, 10, new BigDecimal(pointValue), percent,
                new BigDecimal(earnAmount), 48, 30, 24, 5, 2, List.of()));
    }

    private final GiftPointPolicy demo = policy("1.00", 50, "10.00");

    @Test
    void oneDemoPointIsWorthOneCurrencyUnit() {
        assertThat(demo.discountFor(100)).isEqualByComparingTo("100.00");
        assertThat(demo.discountFor(0)).isEqualByComparingTo("0.00");
    }

    @Test
    void atMostHalfTheSubtotalCanBePaidWithPointsRoundedDownToWholePoints() {
        assertThat(demo.maxRedeemablePoints(new BigDecimal("1199.00"))).isEqualTo(599); // the contract example
        assertThat(demo.maxRedeemablePoints(new BigDecimal("1000.00"))).isEqualTo(500);
        assertThat(demo.maxRedeemablePoints(new BigDecimal("1.99"))).isZero();
        assertThat(demo.maxRedeemablePoints(new BigDecimal("0.00"))).isZero();
    }

    @Test
    void theDiscountForTheCapNeverExceedsTheConfiguredShareOfTheSubtotal() {
        for (String subtotal : new String[] {"1.00", "19.99", "899.00", "1199.00", "123456.78"}) {
            BigDecimal sub = new BigDecimal(subtotal);
            BigDecimal discount = demo.discountFor(demo.maxRedeemablePoints(sub));
            assertThat(discount).isLessThanOrEqualTo(sub.multiply(new BigDecimal("0.50")));
        }
    }

    @Test
    void thePointValueAndThePercentageAreConfigurableNotHardCoded() {
        GiftPointPolicy custom = policy("0.50", 20, "25.00");
        assertThat(custom.discountFor(10)).isEqualByComparingTo("5.00");
        // 20% of 1000 = 200 money = 400 points at 0.50 each
        assertThat(custom.maxRedeemablePoints(new BigDecimal("1000.00"))).isEqualTo(400);
        assertThat(custom.pointsEarnedFor(new BigDecimal("100.00"))).isEqualTo(4);
    }

    @Test
    void aZeroPercentageDisablesRedemption() {
        assertThat(policy("1.00", 0, "10.00").maxRedeemablePoints(new BigDecimal("5000.00"))).isZero();
    }

    @Test
    void pointsEarnedAreOnePerTenOfTheAmountPaidRoundedDown() {
        assertThat(demo.pointsEarnedFor(new BigDecimal("1099.00"))).isEqualTo(109); // the contract example
        assertThat(demo.pointsEarnedFor(new BigDecimal("9.99"))).isZero();
        assertThat(demo.pointsEarnedFor(new BigDecimal("10.00"))).isEqualTo(1);
        assertThat(demo.pointsEarnedFor(new BigDecimal("0.00"))).isZero();
    }
}
