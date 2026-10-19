package com.example.ebookstore.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.example.ebookstore.domain.OrderStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A customer's order (table {@code orders}; the class is not called {@code Order} because that is an SQL keyword).
 *
 * <p>Everything a customer paid for is copied into the order: the delivery address, the item titles and unit
 * prices, the subtotal, the points discount and the total. Editing the catalogue or an address later never changes
 * an existing order. This class is never returned from a controller; {@code OrderResponse} is used.
 */
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 30, updatable = false)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "shipping_recipient_name", nullable = false, length = 100, updatable = false)
    private String shippingRecipientName;

    @Column(name = "shipping_phone", nullable = false, length = 20, updatable = false)
    private String shippingPhone;

    @Column(name = "shipping_line1", nullable = false, length = 150, updatable = false)
    private String shippingLine1;

    @Column(name = "shipping_line2", length = 150, updatable = false)
    private String shippingLine2;

    @Column(name = "shipping_city", nullable = false, length = 80, updatable = false)
    private String shippingCity;

    @Column(name = "shipping_state", nullable = false, length = 80, updatable = false)
    private String shippingState;

    @Column(name = "shipping_postal_code", nullable = false, length = 12, updatable = false)
    private String shippingPostalCode;

    @Column(name = "shipping_country_code", nullable = false, length = 2, updatable = false)
    private String shippingCountryCode;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "points_redeemed", nullable = false, updatable = false)
    private int pointsRedeemed;

    @Column(name = "points_discount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal pointsDiscount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "points_earned", nullable = false, updatable = false)
    private int pointsEarned;

    @Column(name = "expected_delivery_date", nullable = false)
    private LocalDate expectedDeliveryDate;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("changedAt ASC, id ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    /** Required by JPA. */
    protected CustomerOrder() {
    }

    /**
     * A freshly placed order ({@code PLACED}). All money values must already be calculated by the server.
     */
    public CustomerOrder(String orderNumber, Long userId, Address shippingFrom, BigDecimal subtotal,
                         int pointsRedeemed, BigDecimal pointsDiscount, BigDecimal totalAmount, int pointsEarned,
                         LocalDate expectedDeliveryDate, Instant createdAt) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.status = OrderStatus.PLACED;
        this.shippingRecipientName = shippingFrom.getRecipientName();
        this.shippingPhone = shippingFrom.getPhone();
        this.shippingLine1 = shippingFrom.getLine1();
        this.shippingLine2 = shippingFrom.getLine2();
        this.shippingCity = shippingFrom.getCity();
        this.shippingState = shippingFrom.getState();
        this.shippingPostalCode = shippingFrom.getPostalCode();
        this.shippingCountryCode = shippingFrom.getCountryCode();
        this.subtotal = subtotal;
        this.pointsRedeemed = pointsRedeemed;
        this.pointsDiscount = pointsDiscount;
        this.totalAmount = totalAmount;
        this.pointsEarned = pointsEarned;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.createdAt = createdAt;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public void addHistory(OrderStatusHistory entry) {
        statusHistory.add(entry);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getShippingRecipientName() {
        return shippingRecipientName;
    }

    public String getShippingPhone() {
        return shippingPhone;
    }

    public String getShippingLine1() {
        return shippingLine1;
    }

    public String getShippingLine2() {
        return shippingLine2;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public String getShippingState() {
        return shippingState;
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public String getShippingCountryCode() {
        return shippingCountryCode;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public int getPointsRedeemed() {
        return pointsRedeemed;
    }

    public BigDecimal getPointsDiscount() {
        return pointsDiscount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public List<OrderStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void markCancelled(String reason, Instant cancelledAt) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.cancelReason = reason;
        addHistory(new OrderStatusHistory(OrderStatus.CANCELLED, reason, cancelledAt));
    }
}
