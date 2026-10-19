package com.example.ebookstore.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.ebookstore.domain.PaymentMethod;
import com.example.ebookstore.domain.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single payment of an order (table {@code payments}). Checkout only creates it in {@code PENDING} state.
 * No card, CVV or UPI data exists anywhere: only the method name and a simulated gateway reference.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "transaction_ref", length = 64)
    private String transactionRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected Payment() {
    }

    /** A new payment waiting to be processed. */
    public static Payment pending(Long orderId, PaymentMethod method, BigDecimal amount, Instant createdAt) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.method = method;
        payment.status = PaymentStatus.PENDING;
        payment.amount = amount;
        payment.createdAt = createdAt;
        return payment;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markSuccess(String transactionRef, Instant paidAt) {
        this.status = PaymentStatus.SUCCESS;
        this.transactionRef = transactionRef;
        this.paidAt = paidAt;
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void markRefunded(Instant refundedAt) {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = refundedAt;
    }
}
