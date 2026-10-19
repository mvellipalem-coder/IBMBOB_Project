package com.example.ebookstore.service;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.ebookstore.config.BusinessProperties;

/**
 * Tentative delivery date: order date + {@code deliveryDays} (demo default 5), plus {@code remoteAreaExtraDays}
 * (demo default 2) when the postal code starts with one of the configured remote-area prefixes. The list of remote
 * prefixes is configuration and is EMPTY by default, because the approved contract names no remote area.
 */
@Component
public class DeliveryEstimator {

    private final BusinessProperties business;

    public DeliveryEstimator(BusinessProperties business) {
        this.business = business;
    }

    public LocalDate expectedDeliveryDate(LocalDate orderDate, String postalCode) {
        int days = business.deliveryDays() + (isRemote(postalCode) ? business.remoteAreaExtraDays() : 0);
        return orderDate.plusDays(days);
    }

    private boolean isRemote(String postalCode) {
        String normalised = normalise(postalCode);
        return business.remoteAreaPostalPrefixes().stream()
                .map(DeliveryEstimator::normalise)
                .anyMatch(prefix -> !prefix.isEmpty() && normalised.startsWith(prefix));
    }

    /** Ignores case, spaces and hyphens, so "SW1A 1AA" and "sw1a-1aa" are the same. */
    private static String normalise(String value) {
        return value.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
    }
}
