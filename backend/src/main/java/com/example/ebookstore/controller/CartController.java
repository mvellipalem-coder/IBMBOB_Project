package com.example.ebookstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.dto.AddCartItemRequest;
import com.example.ebookstore.dto.AddToCartResult;
import com.example.ebookstore.dto.CartResponse;
import com.example.ebookstore.dto.UpdateCartItemRequest;
import com.example.ebookstore.security.AuthenticatedUser;
import com.example.ebookstore.service.CartService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

/**
 * Cart endpoints (operations getCart, addCartItem, updateCartItem and removeCartItem in openapi.yaml).
 *
 * <p>All of them need a valid token (the default rule in {@code SecurityConfig}). The owner of the cart is always
 * the token's user; there is no user id in any URL, query string or body, so one user cannot address another
 * user's cart.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** {@code GET /api/cart} - the current user's cart (empty, never 404). */
    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(user.id());
    }

    /** {@code POST /api/cart/items} - 201 when a new line was created, 200 when an existing line was increased. */
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal AuthenticatedUser user,
                                                @Valid @RequestBody AddCartItemRequest request) {
        AddToCartResult result = cartService.addItem(user.id(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.cart());
    }

    /** {@code PUT /api/cart/items/{itemId}} - sets the quantity of a line. */
    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable @Min(1) long itemId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(user.id(), itemId, request);
    }

    /** {@code DELETE /api/cart/items/{itemId}} - removes a line and returns the updated cart. */
    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable @Min(1) long itemId) {
        return cartService.removeItem(user.id(), itemId);
    }
}
