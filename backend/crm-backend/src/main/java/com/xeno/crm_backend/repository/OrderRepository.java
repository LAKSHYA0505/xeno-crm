package com.xeno.crm_backend.repository;


import com.xeno.crm_backend.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByCustomerIdOrderByOrderedAtDesc(UUID customerId);
    //Used to show order history for a customer and to compute totalSpent and lastOrderAt
    @Query(value = """
        SELECT o.* FROM orders o
        WHERE o.customer_id = :customerId
        ORDER BY o.ordered_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Order findLatestByCustomerId(UUID customerId);
    //Returns only the single most recent order for a customer. Used during message personalization
}