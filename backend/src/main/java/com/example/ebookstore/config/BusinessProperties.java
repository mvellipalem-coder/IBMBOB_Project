package com.example.ebookstore.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Business rules from the approved openapi.yaml, read from {@code app.business.*}.
 * Keeping them here (instead of hard-coding numbers in services) means they can change without touching Java code.
 *
 * @param maxQuantityPerLine           most copies of one book in a cart line (10)
 * @param maxAddressesPerUser          most saved addresses per user (10)
 * @param pointValue                   money value of one gift point (1.00)
 * @param maxRedeemPercentOfSubtotal   highest share of the subtotal payable with points (50)
 * @param earnAmountPerPoint           amount paid that earns one point (10.00)
 * @param cancellationWindowHours      hours after ordering during which cancelling is allowed (48)
 * @param unpaidOrderCancelMinutes     minutes before an unpaid CARD/UPI order is auto-cancelled (30)
 * @param idempotencyKeyTtlHours       hours an Idempotency-Key is remembered (24)
 * @param deliveryDays                 normal delivery time in days (5)
 * @param remoteAreaExtraDays          extra days for remote postal codes (2)
 * @param remoteAreaPostalPrefixes     postal-code prefixes that count as "remote" (empty by default: the contract
 *                                     does not name any remote area, so none is invented)
 */
@Validated
@ConfigurationProperties(prefix = "app.business")
public record BusinessProperties(
        @Positive int maxQuantityPerLine,
        @Positive int maxAddressesPerUser,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal pointValue,
        @Min(0) @Max(100) int maxRedeemPercentOfSubtotal,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal earnAmountPerPoint,
        @Positive int cancellationWindowHours,
        @Positive int unpaidOrderCancelMinutes,
        @Positive int idempotencyKeyTtlHours,
        @Positive int deliveryDays,
        @Min(0) int remoteAreaExtraDays,
        List<String> remoteAreaPostalPrefixes) {

    /** Normalises the prefix list once: never null, trimmed, upper case, no blanks. */
    public BusinessProperties {
        remoteAreaPostalPrefixes = remoteAreaPostalPrefixes == null ? List.of()
                : remoteAreaPostalPrefixes.stream()
                        .map(String::trim)
                        .filter(prefix -> !prefix.isEmpty())
                        .map(prefix -> prefix.toUpperCase(java.util.Locale.ROOT))
                        .toList();
    }
}
