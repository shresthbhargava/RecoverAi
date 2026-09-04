package com.recoverai.repository;

import com.recoverai.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
