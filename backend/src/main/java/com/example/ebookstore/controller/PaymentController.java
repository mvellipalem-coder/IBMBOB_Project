package com.example.ebookstore.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.PaymentProcessResult;
import com.example.ebookstore.dto.PaymentResponse;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.PaymentService;

import jakarta.validation.constraints.Pattern;

/**
 * {@code /api/payments} endpoints for simulated payment processing and verification.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderId}/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("orderId") Long orderId,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$", message = "must be 16-64 letters, digits, '-' or '_'")
            String idempotencyKey) {
        PaymentProcessResult result = paymentService.processPayment(user.id(), orderId, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.payment());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("orderId") Long orderId) {
        PaymentResponse response = paymentService.getPayment(user.id(), orderId);
        return ResponseEntity.ok(response);
    }
}
