package com.example.ebookstore.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.domain.PaymentStatus;
import com.example.ebookstore.dto.PaymentProcessResult;
import com.example.ebookstore.dto.PaymentResponse;
import com.example.ebookstore.entity.CustomerOrder;
import com.example.ebookstore.entity.IdempotencyKey;
import com.example.ebookstore.entity.Payment;
import com.example.ebookstore.entity.Role;
import com.example.ebookstore.entity.User;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.OrderMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.IdempotencyKeyRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.repository.PaymentRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.payment.PaymentGateway;
import com.example.ebookstore.service.payment.PaymentGatewayRequest;
import com.example.ebookstore.service.payment.PaymentGatewayResponse;

/**
 * Unit tests for {@link PaymentServiceImpl}. All repositories and the payment gateway are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long ORDER_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String IDEMPOTENCY_KEY = "test-key-001";

    private static final BusinessProperties BUSINESS = new BusinessProperties(10, 10, new BigDecimal("1.00"), 50,
            new BigDecimal("10.00"), 48, 30, 24, 5, 2, List.of());

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookRepository bookRepository;
    @Mock private GiftPointTransactionRepository ledger;
    @Mock private IdempotencyKeyRepository idempotencyKeys;
    @Mock private PaymentGateway paymentGateway;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        OrderMapper orderMapper = new OrderMapper(BUSINESS, fixedClock);
        service = new PaymentServiceImpl(userRepository, orderRepository, paymentRepository, bookRepository,
                ledger, idempotencyKeys, paymentGateway, orderMapper, BUSINESS, fixedClock);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(bookRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of());
        when(idempotencyKeys.findByUserIdAndOperationAndKeyValue(eq(USER_ID), eq("PAYMENT"), eq(IDEMPOTENCY_KEY)))
                .thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------ processPayment — success path

    @Test
    void successfulPaymentSetsStatusAndTransactionRef() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any())).thenReturn(payment);
        when(paymentGateway.processPayment(any(PaymentGatewayRequest.class)))
                .thenReturn(new PaymentGatewayResponse(true, "SIM-AABBCCDD1234", null));

        PaymentProcessResult result = service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.payment().transactionRef()).isEqualTo("SIM-AABBCCDD1234");
        assertThat(result.replayed()).isFalse();
        verify(idempotencyKeys).save(any(IdempotencyKey.class));
    }

    @Test
    void failedPaymentCancelsOrderRestoresStockAndRefundsGiftPoints() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 50);
        Payment payment = pendingPayment(ORDER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any())).thenReturn(payment);
        when(orderRepository.saveAndFlush(any())).thenReturn(order);
        when(paymentGateway.processPayment(any()))
                .thenReturn(new PaymentGatewayResponse(false, null, "Insufficient funds"));

        PaymentProcessResult result = service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.payment().failureReason()).isEqualTo("Insufficient funds");
        // Gift-points were redeemed, so the ledger must get a refund entry
        verify(ledger).insertRefund(eq(USER_ID), eq(ORDER_ID), eq(50), anyString());
    }

    @Test
    void failedPaymentWithZeroPointsRedeemedDoesNotTouchLedger() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any())).thenReturn(payment);
        when(orderRepository.saveAndFlush(any())).thenReturn(order);
        when(paymentGateway.processPayment(any()))
                .thenReturn(new PaymentGatewayResponse(false, null, "Decline"));

        service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        verify(ledger, never()).insertRefund(anyLong(), anyLong(), anyInt(), anyString());
    }

    // ------------------------------------------------------------------ processPayment — guard rails

    @Test
    void alreadySettledPaymentThrowsPaymentAlreadySettled() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        payment.markSuccess("SIM-OLD", NOW.minusSeconds(3600));
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_SETTLED));
        verify(paymentGateway, never()).processPayment(any());
    }

    @Test
    void cancelledOrderThrowsOrderNotPayable() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.CANCELLED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        payment.markFailed("Was cancelled");
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE));
    }

    @Test
    void codOrderThrowsPaymentNotRequired() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = Payment.pending(ORDER_ID, PaymentMethod.COD, new BigDecimal("1000.00"), NOW);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_REQUIRED));
    }

    @Test
    void unknownOrderThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void duplicateKeyWithSameOrderReturnsReplayedResult() {
        IdempotencyKey remembered = new IdempotencyKey(USER_ID, "PAYMENT", IDEMPOTENCY_KEY,
                PaymentServiceImpl.hash(ORDER_ID), ORDER_ID, NOW.plusSeconds(3600));
        when(idempotencyKeys.findByUserIdAndOperationAndKeyValue(USER_ID, "PAYMENT", IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(remembered));

        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        payment.markSuccess("SIM-REPLAY", NOW);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        PaymentProcessResult result = service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment().transactionRef()).isEqualTo("SIM-REPLAY");
        verify(paymentGateway, never()).processPayment(any());
    }

    @Test
    void duplicateKeyWithDifferentOrderThrowsIdempotencyKeyReused() {
        long differentOrderId = 99L;
        IdempotencyKey remembered = new IdempotencyKey(USER_ID, "PAYMENT", IDEMPOTENCY_KEY,
                PaymentServiceImpl.hash(differentOrderId), differentOrderId, NOW.plusSeconds(3600));
        when(idempotencyKeys.findByUserIdAndOperationAndKeyValue(USER_ID, "PAYMENT", IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(remembered));

        // processPayment is called with ORDER_ID, but the key was for a different order
        assertThatThrownBy(() -> service.processPayment(USER_ID, ORDER_ID, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    // ------------------------------------------------------------------ getPayment

    @Test
    void getPaymentReturnsPaymentDetailsForOwnOrder() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        PaymentResponse result = service.getPayment(USER_ID, ORDER_ID);

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void getPaymentForAnotherUsersOrderThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(USER_ID, ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ------------------------------------------------------------------ helpers

    private static User user(long id) {
        User u = new User("Test", "test@example.com", "hash", null, Role.CUSTOMER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static CustomerOrder order(long id, long userId, OrderStatus status, int pointsRedeemed) {
        var addr = BeanUtils.instantiateClass(com.example.ebookstore.entity.Address.class);
        ReflectionTestUtils.setField(addr, "recipientName", "Test User");
        ReflectionTestUtils.setField(addr, "phone", "+91 98765 43210");
        ReflectionTestUtils.setField(addr, "line1", "1 Test St");
        ReflectionTestUtils.setField(addr, "city", "Mumbai");
        ReflectionTestUtils.setField(addr, "state", "Maharashtra");
        ReflectionTestUtils.setField(addr, "postalCode", "400001");
        ReflectionTestUtils.setField(addr, "countryCode", "IN");

        CustomerOrder order = new CustomerOrder("EB-20260601-0001", userId, addr,
                new BigDecimal("1000.00"), pointsRedeemed, new BigDecimal(pointsRedeemed),
                new BigDecimal("1000.00").subtract(new BigDecimal(pointsRedeemed)),
                100, LocalDate.of(2026, 6, 6), NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "orderNumber", "EB-20260601-0001");
        if (status != OrderStatus.PLACED) {
            ReflectionTestUtils.setField(order, "status", status);
        }
        return order;
    }

    private static Payment pendingPayment(long orderId) {
        return Payment.pending(orderId, PaymentMethod.CARD, new BigDecimal("1000.00"), NOW);
    }
}
