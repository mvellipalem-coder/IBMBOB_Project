package com.example.ebookstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ebookstore.entity.Payment;

/** Database access for payments (one per order). */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdIn(List<Long> orderIds);
}
