package com.example.ebookstore.service;

import org.springframework.data.domain.Pageable;

import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.dto.BuyAgainResult;
import com.example.ebookstore.dto.OrderPage;
import com.example.ebookstore.dto.OrderResponse;

/**
 * Service contract for order retrieval, cancellation and repurchase.
 */
public interface OrderService {

    /**
     * Retrieves an order by ID for the authenticated user.
     *
     * @param userId authenticated user ID
     * @param orderId order ID
     * @return order details
     */
    OrderResponse getOrder(Long userId, Long orderId);

    /**
     * Lists orders for the authenticated user with optional status filtering.
     *
     * @param userId authenticated user ID
     * @param status optional status filter
     * @param pageable pagination parameters
     * @return page of order summaries
     */
    OrderPage listOrders(Long userId, OrderStatus status, Pageable pageable);

    /**
     * Cancels an order within the 48-hour cancellation window.
     *
     * @param userId authenticated user ID
     * @param orderId order ID
     * @param reason cancellation reason
     * @return cancelled order view
     */
    OrderResponse cancelOrder(Long userId, Long orderId, String reason);

    /**
     * Adds books from a previous order into the current cart.
     *
     * @param userId authenticated user ID
     * @param orderId previous order ID
     * @return cart contents and any availability issues
     */
    BuyAgainResult buyAgain(Long userId, Long orderId);
}
