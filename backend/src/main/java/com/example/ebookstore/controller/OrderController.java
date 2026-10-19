package com.example.ebookstore.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.dto.BuyAgainResult;
import com.example.ebookstore.dto.CancelOrderRequest;
import com.example.ebookstore.dto.OrderPage;
import com.example.ebookstore.dto.OrderResponse;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.OrderService;

import jakarta.validation.Valid;

/**
 * {@code /api/orders} endpoints for order summary, details, history, cancellation, and buy-again.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(orderService.getOrder(user.id(), id));
    }

    @GetMapping
    public ResponseEntity<OrderPage> listOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "status", required = false) OrderStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(orderService.listOrders(user.id(), status, pageable));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.reasonOrDefault() : "Cancelled by customer";
        return ResponseEntity.ok(orderService.cancelOrder(user.id(), id, reason));
    }

    @PostMapping("/{id}/buy-again")
    public ResponseEntity<BuyAgainResult> buyAgain(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(orderService.buyAgain(user.id(), id));
    }
}
