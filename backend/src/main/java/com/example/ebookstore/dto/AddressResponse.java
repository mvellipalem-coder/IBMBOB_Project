package com.example.ebookstore.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A saved delivery address (schema {@code Address}). */
public record AddressResponse(
        Long id,
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String countryCode,
        @JsonProperty("isDefault") boolean isDefault) {
}
