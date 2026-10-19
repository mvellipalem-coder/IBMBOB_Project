package com.example.ebookstore.mapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.example.ebookstore.config.BusinessProperties;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import com.example.ebookstore.domain.OrderStatus;
import com.example.ebookstore.dto.CancellationInfo;
import com.example.ebookstore.dto.OrderItemResponse;
import com.example.ebookstore.dto.OrderPage;
import com.example.ebookstore.dto.OrderResponse;
import com.example.ebookstore.dto.OrderStatusEventResponse;
import com.example.ebookstore.dto.OrderSummary;
import com.example.ebookstore.dto.PaymentResponse;
import com.example.ebookstore.dto.ShippingAddressResponse;
import com.example.ebookstore.entity.CustomerOrder;
import com.example.ebookstore.entity.OrderItem;
import com.example.ebookstore.entity.OrderStatusHistory;
import com.example.ebookstore.entity.Payment;

/**
 * Builds {@link OrderResponse} from the stored order. Call it inside a transaction: items and history are lazy.
 * Everything comes from what was stored at checkout (snapshots), never from the live catalogue.
 */
@Component
public class OrderMapper {

    private final BusinessProperties business;
    private final Clock clock;

    public OrderMapper(BusinessProperties business, Clock clock) {
        this.business = business;
        this.clock = clock;
    }

    public OrderResponse toResponse(CustomerOrder order, Payment payment) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpectedDeliveryDate(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                shippingOf(order),
                order.getItems().stream().map(this::itemOf).toList(),
                order.getSubtotal(),
                order.getPointsRedeemed(),
                order.getPointsDiscount(),
                order.getTotalAmount(),
                order.getPointsEarned(),
                paymentOf(payment),
                order.getStatusHistory().stream().map(this::eventOf).toList(),
                cancellationOf(order));
    }

    private ShippingAddressResponse shippingOf(CustomerOrder order) {
        return new ShippingAddressResponse(order.getShippingRecipientName(), order.getShippingPhone(),
                order.getShippingLine1(), order.getShippingLine2(), order.getShippingCity(),
                order.getShippingState(), order.getShippingPostalCode(), order.getShippingCountryCode());
    }

    private OrderItemResponse itemOf(OrderItem item) {
        return new OrderItemResponse(item.getBookId(), item.getBookTitle(), item.getBookIsbn(), item.getUnitPrice(),
                item.getQuantity(), item.getLineTotal());
    }

    public PaymentResponse paymentOf(Payment payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(payment.getOrderId(), payment.getMethod(), payment.getStatus(),
                payment.getAmount(), payment.getTransactionRef(), payment.getFailureReason(), payment.getPaidAt(),
                payment.getRefundedAt(), payment.getCreatedAt());
    }

    public OrderSummary toSummary(CustomerOrder order, Payment payment) {
        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpectedDeliveryDate(),
                order.getTotalAmount(),
                itemCount,
                payment != null ? payment.getMethod() : null,
                payment != null ? payment.getStatus() : null);
    }

    public OrderPage toPage(Page<CustomerOrder> page, Map<Long, Payment> paymentsByOrderId) {
        List<OrderSummary> summaries = page.getContent().stream()
                .map(order -> toSummary(order, paymentsByOrderId.get(order.getId())))
                .toList();
        return new OrderPage(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                summaries);
    }

    private OrderStatusEventResponse eventOf(OrderStatusHistory entry) {
        return new OrderStatusEventResponse(entry.getStatus(), entry.getNote(), entry.getChangedAt());
    }

    /**
     * Cancellable = a cancellable status AND now is before createdAt + window. The reason is checked in the order
     * the contract lists: already cancelled, wrong status, window expired.
     */
    private CancellationInfo cancellationOf(CustomerOrder order) {
        Instant until = order.getCreatedAt().plus(business.cancellationWindowHours(), ChronoUnit.HOURS);
        String reason = null;
        if (order.getStatus() == OrderStatus.CANCELLED) {
            reason = "ORDER_ALREADY_CANCELLED";
        } else if (!order.getStatus().isCancellableState()) {
            reason = "ORDER_NOT_CANCELLABLE_STATUS";
        } else if (!clock.instant().isBefore(until)) {
            reason = "CANCELLATION_WINDOW_EXPIRED";
        }
        return new CancellationInfo(reason == null, until, reason);
    }
}
