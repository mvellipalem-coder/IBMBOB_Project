package com.example.ebookstore.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.ebookstore.config.BusinessProperties;

class DeliveryEstimatorTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 15);

    private static DeliveryEstimator estimator(List<String> remotePrefixes) {
        return new DeliveryEstimator(new BusinessProperties(10, 10, new BigDecimal("1.00"), 50,
                new BigDecimal("10.00"), 48, 30, 24, 5, 2, remotePrefixes));
    }

    @Test
    void deliveryIsFiveDaysAfterTheOrderDate() {
        assertThat(estimator(List.of()).expectedDeliveryDate(ORDER_DATE, "411001")).isEqualTo("2026-10-20");
    }

    @Test
    void noPostalCodeIsRemoteUnlessItIsConfigured() {
        // The contract names no remote area, so the default list is empty.
        assertThat(estimator(List.of()).expectedDeliveryDate(ORDER_DATE, "744101")).isEqualTo("2026-10-20");
    }

    @Test
    void configuredRemotePrefixesAddTwoDaysIgnoringCaseSpacesAndHyphens() {
        DeliveryEstimator estimator = estimator(List.of("744", " ab1 "));
        assertThat(estimator.expectedDeliveryDate(ORDER_DATE, "744101")).isEqualTo("2026-10-22");
        assertThat(estimator.expectedDeliveryDate(ORDER_DATE, "AB1 2CD")).isEqualTo("2026-10-22");
        assertThat(estimator.expectedDeliveryDate(ORDER_DATE, "ab-12cd")).isEqualTo("2026-10-22");
        assertThat(estimator.expectedDeliveryDate(ORDER_DATE, "411001")).isEqualTo("2026-10-20");
    }

    @Test
    void blankPrefixesAreDroppedSoTheyCannotMakeEverythingRemote() {
        assertThat(estimator(List.of("", "  ")).expectedDeliveryDate(ORDER_DATE, "411001")).isEqualTo("2026-10-20");
    }
}
