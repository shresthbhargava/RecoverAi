package com.recoverai.service;

import com.recoverai.entity.Customer;
import com.recoverai.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Get-or-create by email. The synthetic dataset and any real webhook payload
     * both key customers by email; if absent we create a minimal record so the
     * pipeline never blocks on missing customer master data.
     */
    public Customer getOrCreate(String email, String name, String phone) {
        return customerRepository.findByEmail(email)
                .orElseGet(() -> customerRepository.save(
                        Customer.builder()
                                .name(name)
                                .email(email)
                                .phone(phone)
                                .build()
                ));
    }

    public Customer getById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new com.recoverai.exception.ResourceNotFoundException("Customer not found: " + id));
    }

    public void recordPaymentOutcome(Customer customer, boolean succeeded) {
        if (succeeded) {
            customer.setTotalSuccessfulPayments(customer.getTotalSuccessfulPayments() + 1);
        } else {
            customer.setTotalFailedPayments(customer.getTotalFailedPayments() + 1);
        }
        customerRepository.save(customer);
    }

    /**
     * Correction path used only by webhook reconciliation.
     *
     * The batch already recorded an outcome for this attempt from the simulated result, so
     * when a real Razorpay callback later confirms the payment did go through, the tally has
     * to MOVE rather than grow. Calling recordPaymentOutcome again would leave the customer
     * with one failure and one success for a single payment, inflating their attempt history
     * and skewing the reliability signals the Strategy Agent reads back out of it.
     */
    public void reclassifyFailureAsSuccess(Customer customer) {
        customer.setTotalFailedPayments(Math.max(0, customer.getTotalFailedPayments() - 1));
        customer.setTotalSuccessfulPayments(customer.getTotalSuccessfulPayments() + 1);
        customerRepository.save(customer);
    }
}
