package com.xeno.crm_backend.service;


import com.xeno.crm_backend.domain.entity.Customer;
import com.xeno.crm_backend.domain.entity.Order;
import com.xeno.crm_backend.dto.response.CustomerResponse;
import com.xeno.crm_backend.dto.response.CustomerSummary;
import com.xeno.crm_backend.dto.response.OrderResponse;
import com.xeno.crm_backend.repository.CustomerRepository;
import com.xeno.crm_backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    // 1 query total regardless of page size
    public Page<CustomerResponse> getCustomers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("created_at").descending());
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;

        return customerRepository
                .findCustomersWithStats(searchParam, pageable)
                .map(this::fromSummary);
    }

    public CustomerResponse getCustomerById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));

        List<Order> orders = orderRepository.findByCustomerIdOrderByOrderedAtDesc(id);
        double totalSpent = orders.stream()
                .mapToDouble(o -> o.getAmount().doubleValue())
                .sum();

        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .city(customer.getCity())
                .gender(customer.getGender())
                .createdAt(customer.getCreatedAt())
                .orderCount(orders.size())
                .totalSpent(totalSpent)
                .lastOrderAt(orders.isEmpty() ? null : orders.get(0).getOrderedAt())
                .build();
    }

    public List<OrderResponse> getCustomerOrders(UUID customerId) {
        return orderRepository.findByCustomerIdOrderByOrderedAtDesc(customerId)
                .stream()
                .map(this::toOrderResponse)
                .toList();
    }

    // used by SegmentService later when building segment previews
    public List<CustomerResponse> getCustomersWithStatsByIds(List<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return List.of();

        int batchSize = 200;
        List<CustomerResponse> result = new ArrayList<>();

        for (int i = 0; i < customerIds.size(); i += batchSize) {
            List<UUID> batch = customerIds.subList(i, Math.min(i + batchSize, customerIds.size()));
            customerRepository.findCustomersWithStatsByIds(batch)
                    .stream()
                    .map(this::fromSummary)
                    .forEach(result::add);
        }
        return result;
    }

    // --- Mappers ---

    private CustomerResponse fromSummary(CustomerSummary s) {
        return CustomerResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .email(s.getEmail())
                .phone(s.getPhone())
                .city(s.getCity())
                .gender(s.getGender())
                .createdAt(s.getCreatedAt())
                .orderCount(s.getOrderCount() != null ? s.getOrderCount().intValue() : 0)
                .totalSpent(s.getTotalSpent() != null ? s.getTotalSpent().doubleValue() : 0.0)
                .lastOrderAt(s.getLastOrderAt())
                .build();
    }

    private OrderResponse toOrderResponse(Order o) {
        return OrderResponse.builder()
                .id(o.getId())
                .amount(o.getAmount())
                .items(o.getItems())
                .channel(o.getChannel())
                .orderedAt(o.getOrderedAt())
                .build();
    }
}