package com.example.ebookstore.service;

import com.example.ebookstore.dto.AddCartItemRequest;
import com.example.ebookstore.dto.AddToCartResult;
import com.example.ebookstore.dto.CartResponse;
import com.example.ebookstore.dto.UpdateCartItemRequest;

/**
 * Shopping-cart use cases. Every method takes the id of the authenticated user (from the token, never from the
 * request) and only ever touches that user's cart.
 */
public interface CartService {

    /** The user's cart; an empty cart (never an error) when nothing was added yet. */
    CartResponse getCart(long userId);

    /**
     * Adds copies of a book; if the book is already in the cart the quantities are added together.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code BOOK_NOT_FOUND} (missing or inactive book),
     *         {@code INSUFFICIENT_STOCK} or {@code CART_LINE_LIMIT_EXCEEDED}
     */
    AddToCartResult addItem(long userId, AddCartItemRequest request);

    /**
     * Sets (does not add to) the quantity of a line.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code CART_ITEM_NOT_FOUND} (also when the line
     *         belongs to someone else), {@code INSUFFICIENT_STOCK} or {@code CART_LINE_LIMIT_EXCEEDED}
     */
    CartResponse updateItem(long userId, long itemId, UpdateCartItemRequest request);

    /**
     * Removes a line. Always allowed, also for books that are no longer available.
     *
     * @throws com.example.ebookstore.exception.ApiException {@code CART_ITEM_NOT_FOUND}
     */
    CartResponse removeItem(long userId, long itemId);
}
