package com.example.ebookstore.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.domain.PaymentStatus;
import com.example.ebookstore.dto.BuyAgainIssue;
import com.example.ebookstore.dto.BuyAgainResult;
import com.example.ebookstore.dto.CartResponse;
import com.example.ebookstore.dto.OrderPage;
import com.example.ebookstore.dto.OrderResponse;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.Cart;
import com.example.ebookstore.entity.CartItem;
import com.example.ebookstore.entity.CustomerOrder;
import com.example.ebookstore.entity.OrderItem;
import com.example.ebookstore.entity.Payment;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.CartMapper;
import com.example.ebookstore.mapper.OrderMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.CartItemRepository;
import com.example.ebookstore.repository.CartRepository;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.repository.PaymentRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.OrderService;

/**
 * Service implementation for order lookups, cancellation and reordering.
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    static final int LOCK_TIMEOUT_MILLIS = 5_000;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final BookRepository bookRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final GiftPointTransactionRepository ledger;
    private final com.example.ebookstore.service.payment.PaymentGateway paymentGateway;
    private final OrderMapper orderMapper;
    private final CartMapper cartMapper;
    private final BusinessProperties business;
    private final Clock clock;

    public OrderServiceImpl(UserRepository userRepository, OrderRepository orderRepository,
                            PaymentRepository paymentRepository, BookRepository bookRepository,
                            CartRepository cartRepository, CartItemRepository cartItemRepository,
                            GiftPointTransactionRepository ledger,
                            com.example.ebookstore.service.payment.PaymentGateway paymentGateway,
                            OrderMapper orderMapper, CartMapper cartMapper,
                            BusinessProperties business, Clock clock) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.bookRepository = bookRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.ledger = ledger;
        this.paymentGateway = paymentGateway;
        this.orderMapper = orderMapper;
        this.cartMapper = cartMapper;
        this.business = business;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        CustomerOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return orderMapper.toResponse(order, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPage listOrders(Long userId, OrderStatus status, Pageable pageable) {
        Page<CustomerOrder> page = status != null
                ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findByUserId(userId, pageable);

        List<Long> orderIds = page.getContent().stream().map(CustomerOrder::getId).toList();
        Map<Long, Payment> paymentsByOrderId = new HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Payment payment : paymentRepository.findByOrderIdIn(orderIds)) {
                paymentsByOrderId.put(payment.getOrderId(), payment);
            }
        }

        return orderMapper.toPage(page, paymentsByOrderId);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId, String reason) {
        Instant now = clock.instant();
        lockUser(userId);

        CustomerOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (!order.getStatus().isCancellableState()) {
            throw new ApiException(ErrorCode.ORDER_NOT_CANCELLABLE_STATUS);
        }

        Instant cancellableUntil = order.getCreatedAt().plus(business.cancellationWindowHours(), ChronoUnit.HOURS);
        if (!now.isBefore(cancellableUntil)) {
            throw new ApiException(ErrorCode.CANCELLATION_WINDOW_EXPIRED);
        }

        String effectiveReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Cancelled by customer";
        order.markCancelled(effectiveReason, now);
        orderRepository.saveAndFlush(order);

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            com.example.ebookstore.service.payment.PaymentGatewayResponse refundResult =
                    paymentGateway.refundPayment(new com.example.ebookstore.service.payment.PaymentGatewayRefundRequest(
                            order.getId(), payment.getTransactionRef(), payment.getAmount()));
            if (!refundResult.success()) {
                // Log the gateway detail server-side only; never send gateway internals to the client.
                log.error("Refund failed for order {} (gateway: {})", order.getOrderNumber(),
                        refundResult.failureReason());
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
            payment.markRefunded(now);
        } else if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.markFailed("Order cancelled before payment");
        }
        paymentRepository.saveAndFlush(payment);

        // Restore book stock
        Map<Long, Book> books = lockBooks(order.getItems());
        for (OrderItem item : order.getItems()) {
            Book book = books.get(item.getBookId());
            if (book != null) {
                book.increaseStock(item.getQuantity());
            }
        }
        bookRepository.flush();

        // Refund redeemed points
        if (order.getPointsRedeemed() > 0) {
            ledger.insertRefund(userId, order.getId(), order.getPointsRedeemed(),
                    "Refunded for cancelled order " + order.getOrderNumber());
        }

        return orderMapper.toResponse(order, payment);
    }

    @Override
    @Transactional
    public BuyAgainResult buyAgain(Long userId, Long orderId) {
        lockUser(userId);

        CustomerOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        cartRepository.insertIfAbsent(userId);
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        List<BuyAgainIssue> issues = new ArrayList<>();
        boolean anyAdded = false;

        for (OrderItem item : order.getItems()) {
            Optional<Book> bookOpt = bookRepository.findById(item.getBookId());
            if (bookOpt.isEmpty() || !bookOpt.get().isActive()) {
                issues.add(new BuyAgainIssue(item.getBookId(), item.getBookTitle(), "BOOK_INACTIVE",
                        item.getQuantity(), 0));
                continue;
            }

            Book book = bookOpt.get();
            if (book.getStockQuantity() <= 0) {
                issues.add(new BuyAgainIssue(item.getBookId(), item.getBookTitle(), "OUT_OF_STOCK",
                        item.getQuantity(), 0));
                continue;
            }

            Optional<CartItem> existing = cartItemRepository.findByCartIdAndBookId(cart.getId(), book.getId());
            int currentQty = existing.map(CartItem::getQuantity).orElse(0);
            int targetQty = Math.max(currentQty, item.getQuantity());
            int maxAllowed = Math.min(book.getStockQuantity(), business.maxQuantityPerLine());
            int finalQty = Math.min(targetQty, maxAllowed);

            if (finalQty <= 0) {
                issues.add(new BuyAgainIssue(item.getBookId(), item.getBookTitle(), "OUT_OF_STOCK",
                        item.getQuantity(), 0));
                continue;
            }

            if (finalQty < item.getQuantity()) {
                issues.add(new BuyAgainIssue(item.getBookId(), item.getBookTitle(), "QUANTITY_REDUCED",
                        item.getQuantity(), finalQty));
            }

            if (existing.isPresent()) {
                existing.get().setQuantity(finalQty);
            } else {
                cartItemRepository.save(new CartItem(cart.getId(), book, finalQty));
            }
            anyAdded = true;
        }

        if (!anyAdded) {
            throw new ApiException(ErrorCode.NO_ITEMS_AVAILABLE);
        }

        List<CartItem> lines = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        CartResponse cartResponse = cartMapper.toResponse(lines);
        return new BuyAgainResult(cartResponse, issues);
    }

    private void lockUser(long userId) {
        userRepository.setLocalLockTimeout(LOCK_TIMEOUT_MILLIS);
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private Map<Long, Book> lockBooks(List<OrderItem> items) {
        List<Long> ids = items.stream().map(OrderItem::getBookId).distinct().sorted().toList();
        Map<Long, Book> byId = new HashMap<>();
        for (Book book : bookRepository.findAllByIdForUpdate(ids)) {
            byId.put(book.getId(), book);
        }
        return byId;
    }
}
