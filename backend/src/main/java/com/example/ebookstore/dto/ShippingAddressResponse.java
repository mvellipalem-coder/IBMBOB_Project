package com.example.ebookstore.dto;

/** Copy of the delivery address taken at checkout (schema {@code ShippingAddress}). */
public record ShippingAddressResponse(
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String countryCode) {
}
