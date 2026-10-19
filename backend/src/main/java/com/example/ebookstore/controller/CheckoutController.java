package com.example.ebookstore.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.CheckoutRequest;
import com.example.ebookstore.dto.CheckoutResult;
import com.example.ebookstore.dto.OrderResponse;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.CheckoutService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /api/checkout} (operation checkout in openapi.yaml).
 *
 * <p>The body names only an address, a payment method and optional gift points; the cart is the source of the items
 * and the server calculates every amount. {@code Idempotency-Key} is required: retrying with the same key and
 * the same body returns the original order with {@code Idempotent-Replayed: true} instead of creating a second one.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$", message = "must be 16-64 letters, digits, '-' or '_'")
            String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        CheckoutResult result = checkoutService.checkout(user.id(), idempotencyKey, request);
        ResponseEntity.BodyBuilder response = ResponseEntity
                .created(URI.create("/api/orders/" + result.order().id()));
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.order());
    }
}
