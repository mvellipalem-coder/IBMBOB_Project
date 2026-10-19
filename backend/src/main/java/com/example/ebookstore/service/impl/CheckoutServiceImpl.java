package com.example.ebookstore.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.dto.CheckoutRequest;
import com.example.ebookstore.dto.CheckoutResult;
import com.example.ebookstore.dto.OrderResponse;
import com.example.ebookstore.entity.Address;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Cart;
import com.example.ebookstore.entity.CustomerOrder;
import com.example.ebookstore.entity.IdempotencyKey;
import com.example.ebookstore.entity.OrderItem;
import com.example.ebookstore.entity.OrderStatusHistory;
import com.example.ebookstore.entity.Payment;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.OrderMapper;
import com.example.ebookstore.repository.AddressRepository;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.CartItemRepository;
import com.example.ebookstore.repository.CartItemRepository.CartLine;
import com.example.ebookstore.repository.CartRepository;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.IdempotencyKeyRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.repository.PaymentRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.CheckoutService;
import com.example.ebookstore.service.DeliveryEstimator;
import com.example.ebookstore.service.GiftPointPolicy;

/**
 * Checkout: cart -> order, in ONE database transaction.
 *
 * <h2>Order of locks (always the same, so concurrent checkouts cannot deadlock)</h2>
 * <ol>
 *   <li><b>User row</b> ({@code FOR UPDATE}). All checkouts and address changes of one user run one after the other.
 *       This is what makes gift points impossible to spend twice, and what makes idempotency race-free: a second
 *       request with the same key waits here and then finds the first request's committed result.</li>
 *   <li><b>Cart row</b>. Cart changes of the same user cannot interleave with the checkout.</li>
 *   <li><b>Book rows</b>, in ascending id order. Two users buying the last copy are serialised; the second one
 *       then sees the reduced stock and gets {@code INSUFFICIENT_STOCK} instead of overselling.</li>
 * </ol>
 *
 * <h2>Money is never taken from the client</h2>
 * Prices come from the locked book rows, the balance from the database, the discount and totals from
 * {@link GiftPointPolicy}. The request only names an address, a payment method and how many points to spend.
 *
 * <h2>All or nothing</h2>
 * Any exception rolls back everything: no order, no stock change, no ledger entry, the cart stays as it was and the
 * idempotency key is not stored, so the client can fix the problem and retry (even with the same key).
 */
@Service
public class CheckoutServiceImpl implements CheckoutService {

    static final String OPERATION = "CHECKOUT";
    /** How long a checkout waits for a row lock held by another transaction before giving up (milliseconds). */
    static final int LOCK_TIMEOUT_MILLIS = 5_000;

    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final GiftPointTransactionRepository ledger;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final GiftPointPolicy pointPolicy;
    private final DeliveryEstimator deliveryEstimator;
    private final OrderMapper orderMapper;
    private final BusinessProperties business;
    private final Clock clock;

    public CheckoutServiceImpl(UserRepository userRepository, CartRepository cartRepository,
                               CartItemRepository cartItemRepository, AddressRepository addressRepository,
                               BookRepository bookRepository, OrderRepository orderRepository,
                               PaymentRepository paymentRepository, GiftPointTransactionRepository ledger,
                               IdempotencyKeyRepository idempotencyKeys, GiftPointPolicy pointPolicy,
                               DeliveryEstimator deliveryEstimator, OrderMapper orderMapper,
                               BusinessProperties business, Clock clock) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.ledger = ledger;
        this.idempotencyKeys = idempotencyKeys;
        this.pointPolicy = pointPolicy;
        this.deliveryEstimator = deliveryEstimator;
        this.orderMapper = orderMapper;
        this.business = business;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CheckoutResult checkout(long userId, String idempotencyKey, CheckoutRequest request) {
        Instant now = clock.instant();
        lockUser(userId);

        // ---- idempotency: same key + same request -> the existing order; same key + other request -> 422
        String requestHash = hash(request);
        idempotencyKeys.deleteIfExpired(userId, OPERATION, idempotencyKey, now);
        Optional<IdempotencyKey> remembered =
                idempotencyKeys.findByUserIdAndOperationAndKeyValue(userId, OPERATION, idempotencyKey);
        if (remembered.isPresent()) {
            return replay(userId, remembered.get(), requestHash);
        }

        // ---- 1. the cart (contract step 1: reject an empty cart)
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.EMPTY_CART));
        List<CartLine> lines = cartItemRepository.findLinesByCartId(cart.getId());
        if (lines.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_CART);
        }

        // ---- 2. the address must belong to the caller (another user's address looks like a missing one)
        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ADDRESS_NOT_FOUND));

        // ---- 3. re-price every line from the locked book rows and check stock
        Map<Long, Book> books = lockBooks(lines);
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (CartLine line : lines) {
            Book book = requireOrderable(books.get(line.getBookId()), line.getQuantity());
            subtotal = subtotal.add(book.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }

        // ---- 4. gift points: balance from the database, cap from the policy
        int points = request.pointsOrZero();
        requireRedeemable(userId, points, subtotal);
        BigDecimal discount = pointPolicy.discountFor(points);
        BigDecimal total = subtotal.subtract(discount);

        // ---- 5. the order with its snapshots, the pending payment and the first history entry
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        CustomerOrder order = new CustomerOrder(nextOrderNumber(today), userId, address, subtotal, points, discount,
                total, pointPolicy.pointsEarnedFor(total),
                deliveryEstimator.expectedDeliveryDate(today, address.getPostalCode()), now);
        for (CartLine line : lines) {
            Book book = books.get(line.getBookId());
            order.addItem(new OrderItem(book.getId(), book.getTitle(), book.getIsbn(), book.getPrice(),
                    line.getQuantity()));
        }
        order.addHistory(new OrderStatusHistory(OrderStatus.PLACED, null, now));
        orderRepository.saveAndFlush(order);
        Payment payment = paymentRepository.save(
                Payment.pending(order.getId(), request.paymentMethod(), total, now));

        // ---- 6. stock (rows are locked, so this cannot go below zero)
        for (CartLine line : lines) {
            books.get(line.getBookId()).decreaseStock(line.getQuantity());
        }

        // ---- 7. the ledger entry; the database trigger lowers the balance and refuses a negative one
        if (points > 0) {
            redeem(userId, order, points);
        }

        // ---- 8. remember the key together with the order, then 9. clear the cart
        idempotencyKeys.save(new IdempotencyKey(userId, OPERATION, idempotencyKey, requestHash, order.getId(),
                now.plusSeconds(business.idempotencyKeyTtlHours() * 3600L)));
        cartItemRepository.deleteAllByCartId(cart.getId());
        bookRepository.flush();

        return new CheckoutResult(orderMapper.toResponse(order, payment), false);
    }

    // ------------------------------------------------------------------ steps

    /**
     * Takes the per-user lock. If another transaction of the same user holds it for too long the request is told to
     * retry shortly, which is exactly the contract's "still being processed" case.
     */
    private void lockUser(long userId) {
        try {
            userRepository.setLocalLockTimeout(LOCK_TIMEOUT_MILLIS);
            userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        } catch (PessimisticLockingFailureException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                    "Another checkout is still being processed. Retry shortly.");
        }
    }

    private CheckoutResult replay(long userId, IdempotencyKey remembered, String requestHash) {
        if (!remembered.getRequestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        CustomerOrder order = orderRepository.findByIdAndUserId(remembered.getOrderId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        OrderResponse response = orderMapper.toResponse(order, payment);
        return new CheckoutResult(response, true);
    }

    /** Locks the books of the cart in id order and returns them by id. */
    private Map<Long, Book> lockBooks(List<CartLine> lines) {
        List<Long> ids = lines.stream().map(CartLine::getBookId).distinct().sorted().toList();
        Map<Long, Book> byId = new HashMap<>();
        for (Book book : bookRepository.findAllByIdForUpdate(ids)) {
            byId.put(book.getId(), book);
        }
        return byId;
    }

    /** A book that was deactivated or sold out after it was added to the cart blocks the checkout with a clear message. */
    private Book requireOrderable(Book book, int quantity) {
        if (book == null) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "A book in your cart is no longer available. Update your cart and try again.");
        }
        if (!book.isActive()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "\"" + book.getTitle() + "\" is no longer available. Update your cart and try again.");
        }
        int stock = book.getStockQuantity();
        if (quantity > stock) {
            String what = stock <= 0
                    ? "\"" + book.getTitle() + "\" is out of stock."
                    : "Only " + stock + (stock == 1 ? " copy" : " copies") + " of \"" + book.getTitle() + "\" "
                            + (stock == 1 ? "is" : "are") + " available.";
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, what + " Update your cart and try again.");
        }
        return book;
    }

    /** Never more than the balance, never more than the cap; the balance is read after the user row is locked. */
    private void requireRedeemable(long userId, int points, BigDecimal subtotal) {
        if (points == 0) {
            return;
        }
        int balance = userRepository.findPointsBalance(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        int cap = pointPolicy.maxRedeemablePoints(subtotal);
        if (points > Math.min(balance, cap)) {
            String message = balance < cap
                    ? "You only have " + balance + " gift points available."
                    : "You can redeem at most " + cap + " points for this order.";
            throw new ApiException(ErrorCode.INVALID_POINTS_REDEMPTION, message);
        }
    }

    private void redeem(long userId, CustomerOrder order, int points) {
        try {
            ledger.insertRedeem(userId, order.getId(), -points,
                    "Redeemed at checkout for order " + order.getOrderNumber());
        } catch (DataIntegrityViolationException ex) {
            // Backstop only: the balance check above already passed. The database refused a negative balance.
            throw new ApiException(ErrorCode.INVALID_POINTS_REDEMPTION, "You do not have enough gift points.");
        }
    }

    private String nextOrderNumber(LocalDate today) {
        return "EB-" + ORDER_DATE.format(today) + "-" + String.format("%04d", orderRepository.nextOrderSequence());
    }

    /** SHA-256 of the fields that define the request (a missing {@code pointsToRedeem} equals 0). */
    static String hash(CheckoutRequest request) {
        String canonical = request.addressId() + "|" + request.paymentMethod() + "|" + request.pointsOrZero();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
