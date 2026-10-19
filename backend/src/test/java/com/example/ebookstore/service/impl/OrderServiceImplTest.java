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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ebookstore.config.BusinessProperties;
import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.domain.PaymentStatus;
import com.example.ebookstore.dto.BuyAgainResult;
import com.example.ebookstore.dto.OrderPage;
import com.example.ebookstore.dto.OrderResponse;
import com.example.ebookstore.entity.Cart;
import com.example.ebookstore.entity.CartItem;
import com.example.ebookstore.entity.CustomerOrder;
import com.example.ebookstore.entity.OrderItem;
import com.example.ebookstore.entity.Payment;
import com.example.ebookstore.entity.Role;
import com.example.ebookstore.entity.User;
import com.example.ebookstore.exception.ApiException;
import com.example.ebookstore.exception.ErrorCode;
import com.example.ebookstore.mapper.BookMapper;
import com.example.ebookstore.mapper.CartMapper;
import com.example.ebookstore.mapper.OrderMapper;
import com.example.ebookstore.repository.BookRepository;
import com.example.ebookstore.repository.CartItemRepository;
import com.example.ebookstore.repository.CartRepository;
import com.example.ebookstore.repository.GiftPointTransactionRepository;
import com.example.ebookstore.repository.OrderRepository;
import com.example.ebookstore.repository.PaymentRepository;
import com.example.ebookstore.repository.UserRepository;
import com.example.ebookstore.service.payment.PaymentGateway;
import com.example.ebookstore.service.payment.PaymentGatewayRefundRequest;
import com.example.ebookstore.service.payment.PaymentGatewayResponse;
import com.example.ebookstore.testsupport.CatalogueTestData;

/**
 * Unit tests for {@link OrderServiceImpl}. All repositories and gateways are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long ORDER_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    private static final BusinessProperties BUSINESS = new BusinessProperties(10, 10, new BigDecimal("1.00"), 50,
            new BigDecimal("10.00"), 48, 30, 24, 5, 2, List.of());

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookRepository bookRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private GiftPointTransactionRepository ledger;
    @Mock private PaymentGateway paymentGateway;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        CartMapper cartMapper = new CartMapper(new BookMapper(BUSINESS), BUSINESS);
        OrderMapper orderMapper = new OrderMapper(BUSINESS, fixedClock);
        service = new OrderServiceImpl(userRepository, orderRepository, paymentRepository, bookRepository,
                cartRepository, cartItemRepository, ledger, paymentGateway, orderMapper, cartMapper, BUSINESS,
                fixedClock);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(bookRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of());
    }

    // ------------------------------------------------------------------ getOrder

    @Test
    void getOrderReturnsResponseWhenOrderBelongsToUser() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        OrderResponse result = service.getOrder(USER_ID, ORDER_ID);

        assertThat(result.id()).isEqualTo(ORDER_ID);
        assertThat(result.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void getOrderThrowsNotFoundWhenOrderBelongsToAnotherUser() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(USER_ID, ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ------------------------------------------------------------------ listOrders

    @Test
    void listOrdersReturnsPaginatedResults() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        Payment payment = pendingPayment(ORDER_ID);
        Page<CustomerOrder> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(orderRepository.findByUserId(eq(USER_ID), any())).thenReturn(page);
        when(paymentRepository.findByOrderIdIn(List.of(ORDER_ID))).thenReturn(List.of(payment));

        OrderPage result = service.listOrders(USER_ID, null, PageRequest.of(0, 10));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(ORDER_ID);
    }

    @Test
    void listOrdersWithStatusFilterCallsFilteredRepository() {
        when(orderRepository.findByUserIdAndStatus(eq(USER_ID), eq(OrderStatus.PLACED), any()))
                .thenReturn(Page.empty());

        service.listOrders(USER_ID, OrderStatus.PLACED, PageRequest.of(0, 10));

        verify(orderRepository).findByUserIdAndStatus(eq(USER_ID), eq(OrderStatus.PLACED), any());
        verify(orderRepository, never()).findByUserId(any(), any());
    }

    // ------------------------------------------------------------------ cancelOrder

    @Test
    void cancelOrderWithinWindowSucceedsAndSetsCancelledStatus() {
        // Order created 1 hour ago — within the 48h window.
        Instant createdAt = NOW.minusSeconds(3600);
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        Payment payment = pendingPayment(ORDER_ID);

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        OrderResponse result = service.cancelOrder(USER_ID, ORDER_ID, "Changed mind");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.cancelReason()).isEqualTo("Changed mind");
    }

    @Test
    void cancelOrderAfter48HoursThrowsWindowExpired() {
        Instant createdAt = NOW.minusSeconds(49 * 3600); // 49 hours ago
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(USER_ID, ORDER_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANCELLATION_WINDOW_EXPIRED));
    }

    @Test
    void cancelAlreadyCancelledOrderThrowsAlreadyCancelled() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.CANCELLED, 0);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(USER_ID, ORDER_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
    }

    @Test
    void cancelShippedOrderThrowsNotCancellableStatus() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.SHIPPED, 0);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(USER_ID, ORDER_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE_STATUS));
    }

    @Test
    void cancelOrderWithSuccessfulPaymentTriggersRefund() {
        Instant createdAt = NOW.minusSeconds(3600);
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        Payment payment = successPayment(ORDER_ID, "SIM-ABC123");

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);
        when(paymentGateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayResponse(true, "REF-123", null));

        OrderResponse result = service.cancelOrder(USER_ID, ORDER_ID, "Want refund");

        verify(paymentGateway).refundPayment(any());
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void cancelOrderWithRedeemedPointsRefundsThemToTheLedger() {
        Instant createdAt = NOW.minusSeconds(3600);
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 100); // 100 points redeemed
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        Payment payment = pendingPayment(ORDER_ID);

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        service.cancelOrder(USER_ID, ORDER_ID, null);

        verify(ledger).insertRefund(eq(USER_ID), eq(ORDER_ID), eq(100), anyString());
    }

    @Test
    void cancelOrderWithNoRedeemedPointsDoesNotTouchLedger() {
        Instant createdAt = NOW.minusSeconds(3600);
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        Payment payment = pendingPayment(ORDER_ID);

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(paymentRepository.saveAndFlush(payment)).thenReturn(payment);

        service.cancelOrder(USER_ID, ORDER_ID, null);

        verify(ledger, never()).insertRefund(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void cancelOrderForAnotherUsersOrderThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelOrder(USER_ID, ORDER_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    // ------------------------------------------------------------------ buyAgain

    @Test
    void buyAgainAddsItemsToCartAndReturnsNoIssues() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        var book = CatalogueTestData.book(1L, "Clean Code", "899.00", 5,
                CatalogueTestData.category(4L, "Programming"),
                CatalogueTestData.publisher(6L, "Pearson"),
                CatalogueTestData.author(1L, "Robert C. Martin"));
        OrderItem item = new OrderItem(1L, "Clean Code", "9780000000001", new BigDecimal("899.00"), 2);
        order.addItem(item);

        Cart cart = cart(50L, USER_ID);

        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(cartItemRepository.findByCartIdAndBookId(50L, 1L)).thenReturn(Optional.empty());
        CartItem addedItem = new CartItem(50L, book, 2);
        ReflectionTestUtils.setField(addedItem, "id", 99L);
        when(cartItemRepository.findByCartIdOrderByIdAsc(50L)).thenReturn(List.of(addedItem));

        BuyAgainResult result = service.buyAgain(USER_ID, ORDER_ID);

        assertThat(result.issues()).isEmpty();
        assertThat(result.cart().items()).hasSize(1);
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void buyAgainWithInactiveBookReturnsIssueWithBookInactiveReason() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        var book = CatalogueTestData.book(1L, "Old Book", "500.00", 3,
                CatalogueTestData.category(1L, "Cat"),
                CatalogueTestData.publisher(1L, "Pub"));
        ReflectionTestUtils.setField(book, "active", false);
        OrderItem item = new OrderItem(1L, "Old Book", "9780000000001", new BigDecimal("500.00"), 1);
        order.addItem(item);

        Cart cart = cart(50L, USER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(cartItemRepository.findByCartIdOrderByIdAsc(50L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.buyAgain(USER_ID, ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NO_ITEMS_AVAILABLE));
    }

    @Test
    void buyAgainWithOutOfStockBookReturnsNoItemsAvailableWhenAllItemsUnavailable() {
        CustomerOrder order = order(ORDER_ID, USER_ID, OrderStatus.PLACED, 0);
        var book = CatalogueTestData.book(1L, "Sold Out Book", "500.00", 0,
                CatalogueTestData.category(1L, "Cat"),
                CatalogueTestData.publisher(1L, "Pub"));
        OrderItem item = new OrderItem(1L, "Sold Out Book", "9780000000001", new BigDecimal("500.00"), 1);
        order.addItem(item);

        Cart cart = cart(50L, USER_ID);
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(cartRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(cart));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(cartItemRepository.findByCartIdOrderByIdAsc(50L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.buyAgain(USER_ID, ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NO_ITEMS_AVAILABLE));
    }

    @Test
    void buyAgainForAnotherUsersOrderThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buyAgain(USER_ID, ORDER_ID))
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
        if (status != OrderStatus.PLACED) {
            ReflectionTestUtils.setField(order, "status", status);
        }
        return order;
    }

    private static Payment pendingPayment(long orderId) {
        return Payment.pending(orderId, PaymentMethod.CARD, new BigDecimal("1000.00"), NOW);
    }

    private static Payment successPayment(long orderId, String ref) {
        Payment p = Payment.pending(orderId, PaymentMethod.CARD, new BigDecimal("1000.00"), NOW);
        p.markSuccess(ref, NOW);
        return p;
    }

    private static Cart cart(long id, long userId) {
        Cart c = BeanUtils.instantiateClass(Cart.class);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "userId", userId);
        return c;
    }
}
