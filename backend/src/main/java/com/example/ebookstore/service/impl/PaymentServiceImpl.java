package com.example.ebookstore.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.domain.PaymentStatus;
import com.example.ebookstore.dto.PaymentProcessResult;
import com.example.ebookstore.dto.PaymentResponse;
import com.example.ebookstore.entity.Book;
import com.example.ebookstore.entity.CustomerOrder;
import com.example.ebookstore.entity.IdempotencyKey;
import com.example.ebookstore.entity.OrderItem;
import com.example.ebookstore.entity.Payment;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.OrderMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.IdempotencyKeyRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.repository.PaymentRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.PaymentService;
import com.example.ebookstore.service.payment.PaymentGateway;
import com.example.ebookstore.service.payment.PaymentGatewayRequest;
import com.example.ebookstore.service.payment.PaymentGatewayResponse;

/**
 * Payment processing and verification service implementation.
 *
 * <h2>Order of locks</h2>
 * <ol>
 *   <li><b>User row</b> ({@code FOR UPDATE}). Serializes checkouts, payments, and cancellations per user.</li>
 *   <li><b>Book rows</b> (in ascending id order) when restoring stock on payment failure.</li>
 * </ol>
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    static final String OPERATION = "PAYMENT";
    static final int LOCK_TIMEOUT_MILLIS = 5_000;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final BookRepository bookRepository;
    private final GiftPointTransactionRepository ledger;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final PaymentGateway paymentGateway;
    private final OrderMapper orderMapper;
    private final BusinessProperties business;
    private final Clock clock;

    public PaymentServiceImpl(UserRepository userRepository, OrderRepository orderRepository,
                              PaymentRepository paymentRepository, BookRepository bookRepository,
                              GiftPointTransactionRepository ledger, IdempotencyKeyRepository idempotencyKeys,
                              PaymentGateway paymentGateway, OrderMapper orderMapper,
                              BusinessProperties business, Clock clock) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.bookRepository = bookRepository;
        this.ledger = ledger;
        this.idempotencyKeys = idempotencyKeys;
        this.paymentGateway = paymentGateway;
        this.orderMapper = orderMapper;
        this.business = business;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentProcessResult processPayment(Long userId, Long orderId, String idempotencyKey) {
        Instant now = clock.instant();
        lockUser(userId);

        String requestHash = hash(orderId);
        idempotencyKeys.deleteIfExpired(userId, OPERATION, idempotencyKey, now);
        Optional<IdempotencyKey> remembered =
                idempotencyKeys.findByUserIdAndOperationAndKeyValue(userId, OPERATION, idempotencyKey);
        if (remembered.isPresent()) {
            return replay(userId, remembered.get(), requestHash, orderId);
        }

        CustomerOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ApiException(ErrorCode.PAYMENT_ALREADY_SETTLED, "This payment has already been processed.");
        }
        if (payment.getStatus() == PaymentStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ORDER_NOT_PAYABLE, "A cancelled order cannot be paid.");
        }
        if (payment.getMethod() == PaymentMethod.COD) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_REQUIRED, "Cash on delivery orders are paid on delivery.");
        }

        PaymentGatewayRequest gatewayRequest = new PaymentGatewayRequest(
                order.getId(),
                order.getOrderNumber(),
                payment.getAmount(),
                payment.getMethod()
        );
        PaymentGatewayResponse gatewayResponse = paymentGateway.processPayment(gatewayRequest);

        if (gatewayResponse.success()) {
            payment.markSuccess(gatewayResponse.transactionRef(), now);
            paymentRepository.saveAndFlush(payment);

            idempotencyKeys.save(new IdempotencyKey(userId, OPERATION, idempotencyKey, requestHash, order.getId(),
                    now.plusSeconds(business.idempotencyKeyTtlHours() * 3600L)));

            return new PaymentProcessResult(orderMapper.paymentOf(payment), false);
        } else {
            String reason = gatewayResponse.failureReason() != null
                    ? gatewayResponse.failureReason()
                    : "Payment declined by the simulated gateway";

            payment.markFailed(reason);
            paymentRepository.saveAndFlush(payment);

            order.markCancelled(reason, now);
            orderRepository.saveAndFlush(order);

            // Restore book stock
            Map<Long, Book> books = lockBooks(order.getItems());
            for (OrderItem item : order.getItems()) {
                Book book = books.get(item.getBookId());
                if (book != null) {
                    book.increaseStock(item.getQuantity());
                }
            }
            bookRepository.flush();

            // Refund gift points if any were redeemed at checkout
            if (order.getPointsRedeemed() > 0) {
                ledger.insertRefund(userId, order.getId(), order.getPointsRedeemed(),
                        "Refunded for failed payment on order " + order.getOrderNumber());
            }

            idempotencyKeys.save(new IdempotencyKey(userId, OPERATION, idempotencyKey, requestHash, order.getId(),
                    now.plusSeconds(business.idempotencyKeyTtlHours() * 3600L)));

            return new PaymentProcessResult(orderMapper.paymentOf(payment), false);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long userId, Long orderId) {
        CustomerOrder order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return orderMapper.paymentOf(payment);
    }

    private void lockUser(long userId) {
        try {
            userRepository.setLocalLockTimeout(LOCK_TIMEOUT_MILLIS);
            userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        } catch (PessimisticLockingFailureException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                    "Another payment is still being processed. Retry shortly.");
        }
    }

    private PaymentProcessResult replay(long userId, IdempotencyKey remembered, String requestHash, Long orderId) {
        if (!remembered.getRequestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        CustomerOrder order = orderRepository.findByIdAndUserId(remembered.getOrderId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        return new PaymentProcessResult(orderMapper.paymentOf(payment), true);
    }

    private Map<Long, Book> lockBooks(List<OrderItem> items) {
        List<Long> ids = items.stream().map(OrderItem::getBookId).distinct().sorted().toList();
        Map<Long, Book> byId = new HashMap<>();
        for (Book book : bookRepository.findAllByIdForUpdate(ids)) {
            byId.put(book.getId(), book);
        }
        return byId;
    }

    static String hash(Long orderId) {
        String canonical = "PAYMENT|" + orderId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
