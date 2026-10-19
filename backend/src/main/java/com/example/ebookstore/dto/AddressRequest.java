package com.example.ebookstore.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/addresses} and {@code PUT /api/addresses/{id}} (schema {@code AddressRequest}).
 * There is no user id: the owner is always the authenticated user. Unknown JSON fields are rejected with {@code 400}.
 *
 * @param countryCode optional, defaults to {@code IN}
 * @param isDefault   {@code true} makes this the default address; {@code false} or omitted never removes the
 *                    flag from the current default
 */
public record AddressRequest(

        @NotBlank @Size(max = 100)
        String recipientName,

        @NotNull @Pattern(regexp = "^[0-9+() -]{7,20}$", message = "must be 7-20 digits, spaces or + ( ) -")
        String phone,

        @NotBlank @Size(max = 150)
        String line1,

        @Size(max = 150)
        String line2,

        @NotBlank @Size(max = 80)
        String city,

        @NotBlank @Size(max = 80)
        String state,

        @NotNull @Pattern(regexp = "^[A-Za-z0-9 -]{3,12}$", message = "must be 3-12 letters, digits, spaces or -")
        String postalCode,

        @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter upper-case country code")
        String countryCode,

        @JsonProperty("isDefault")
        Boolean isDefault) {

    /** The country to store: the requested one, or {@code IN} when the client did not send it. */
    public String countryCodeOrDefault() {
        return countryCode == null ? "IN" : countryCode;
    }

    public boolean wantsDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
