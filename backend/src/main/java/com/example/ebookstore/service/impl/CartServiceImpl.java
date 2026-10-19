package com.example.ebookstore.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.dto.AddCartItemRequest;
import com.example.ebookstore.dto.AddToCartResult;
import com.example.ebookstore.dto.CartResponse;
import com.example.ebookstore.dto.UpdateCartItemRequest;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Cart;
import com.example.ebookstore.entity.CartItem;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.CartMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.CartItemRepository;
import com.example.ebookstore.repository.CartRepository;
import com.example.ebookstore.service.CartService;

/**
 * Cart rules.
 *
 * <ul>
 *   <li><b>Ownership:</b> the user id comes from the token. Cart lines are only looked up through the caller's own
 *       cart, so another user's line looks exactly like a line that does not exist ({@code 404}).</li>
 *   <li><b>Transactions:</b> every change runs in one transaction that first locks the user's cart row. Stock check,
 *       change and the returned totals are therefore consistent, and racing requests of one user are serialised.</li>
 *   <li><b>Money:</b> prices and totals are always calculated here from the catalogue, never accepted from clients.</li>
 *   <li><b>Unavailable books:</b> a line whose book was deactivated or sold out stays in the cart (the user can see and
 *       remove it) but is reported with {@code maxQuantity = 0}; it cannot be increased.</li>
 * </ul>
 */
@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BookRepository bookRepository;
    private final CartMapper mapper;
    private final BusinessProperties business;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                           BookRepository bookRepository, CartMapper mapper, BusinessProperties business) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.bookRepository = bookRepository;
        this.mapper = mapper;
        this.business = business;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(long userId) {
        // Reading never creates a cart: no cart yet simply means an empty one.
        return cartRepository.findByUserId(userId)
                .map(cart -> loadCart(cart.getId()))
                .orElseGet(() -> mapper.toResponse(java.util.List.of()));
    }

    @Override
    @Transactional
    public AddToCartResult addItem(long userId, AddCartItemRequest request) {
        Book book = bookRepository.findByIdAndActiveTrue(request.bookId())
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
        Cart cart = lockOrCreateCart(userId);

        Optional<CartItem> existing = cartItemRepository.findByCartIdAndBookId(cart.getId(), book.getId());
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + request.quantityOrDefault();
        requireOrderable(book, newQuantity);

        boolean created = existing.isEmpty();
        if (created) {
            cartItemRepository.save(new CartItem(cart.getId(), book, newQuantity));
        } else {
            existing.get().setQuantity(newQuantity);
        }
        return new AddToCartResult(loadCart(cart.getId()), created);
    }

    @Override
    @Transactional
    public CartResponse updateItem(long userId, long itemId, UpdateCartItemRequest request) {
        Cart cart = lockExistingCart(userId);
        CartItem item = findOwnItem(cart, itemId);
        Book book = item.getBook();
        if (!book.isActive()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "\"" + book.getTitle() + "\" is no longer available. Remove it from your cart.");
        }
        requireOrderable(book, request.quantity());
        item.setQuantity(request.quantity());
        return loadCart(cart.getId());
    }

    @Override
    @Transactional
    public CartResponse removeItem(long userId, long itemId) {
        Cart cart = lockExistingCart(userId);
        cartItemRepository.delete(findOwnItem(cart, itemId));
        return loadCart(cart.getId());
    }

    // ------------------------------------------------------------------ helpers

    /** Creates the cart if needed (safe under concurrency), then locks it for the rest of the transaction. */
    private Cart lockOrCreateCart(long userId) {
        cartRepository.insertIfAbsent(userId);
        return lockExistingCart(userId);
    }

    /** A user who never had a cart cannot own any line, so the lookup ends in "item not found". */
    private Cart lockExistingCart(long userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    private CartItem findOwnItem(Cart cart, long itemId) {
        return cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    /** Stock first (the more helpful message), then the per-line limit. */
    private void requireOrderable(Book book, int quantity) {
        int stock = book.getStockQuantity();
        if (quantity > stock) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, stockMessage(book.getTitle(), stock));
        }
        if (quantity > business.maxQuantityPerLine()) {
            throw new ApiException(ErrorCode.CART_LINE_LIMIT_EXCEEDED);
        }
    }

    private static String stockMessage(String title, int stock) {
        if (stock <= 0) {
            return "\"" + title + "\" is out of stock.";
        }
        return "Only " + stock + (stock == 1 ? " copy" : " copies") + " of \"" + title + "\" "
                + (stock == 1 ? "is" : "are") + " available.";
    }

    private CartResponse loadCart(Long cartId) {
        return mapper.toResponse(cartItemRepository.findByCartIdOrderByIdAsc(cartId));
    }
}
