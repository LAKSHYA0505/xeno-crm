package com.xeno.crm_backend.repository;


import com.xeno.crm_backend.domain.entity.Customer;
import com.xeno.crm_backend.dto.response.CustomerSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // Single query - aggregates order stats per customer
    @Query(value = """
        SELECT 
            c.id,
            c.name,
            c.email,
            c.phone,
            c.city,
            c.gender,
            c.created_at,
            COUNT(o.id)        AS order_count,
            COALESCE(SUM(o.amount), 0) AS total_spent,
            MAX(o.ordered_at)  AS last_order_at
        FROM customers c
        LEFT JOIN orders o ON o.customer_id = c.id
        WHERE (:search IS NULL 
               OR LOWER(c.name)  LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
        GROUP BY c.id, c.name, c.email, c.phone, c.city, c.gender, c.created_at
        """,
            countQuery = """
        SELECT COUNT(DISTINCT c.id)
        FROM customers c
        LEFT JOIN orders o ON o.customer_id = c.id
        WHERE (:search IS NULL 
               OR LOWER(c.name)  LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
            nativeQuery = true)
    Page<CustomerSummary> findCustomersWithStats(@Param("search") String search, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT c.* FROM customers c
        JOIN orders o ON o.customer_id = c.id
        GROUP BY c.id
        HAVING EXTRACT(DAY FROM (NOW() - MAX(o.ordered_at))) >= :days
        """, nativeQuery = true)
    List<Customer> findInactiveSince(int days);

    // High-performance batch query for segment previews and campaign targeting
    @Query(value = """
    SELECT 
        c.id,
        c.name,
        c.email,
        c.phone,
        c.city,
        c.gender,
        c.created_at,
        COUNT(o.id)        AS order_count,
        COALESCE(SUM(o.amount), 0) AS total_spent,
        MAX(o.ordered_at)  AS last_order_at
    FROM customers c
    LEFT JOIN orders o ON o.customer_id = c.id
    WHERE c.id IN :customerIds
    GROUP BY c.id, c.name, c.email, c.phone, c.city, c.gender, c.created_at
    """, nativeQuery = true)
    List<CustomerSummary> findCustomersWithStatsByIds(@Param("customerIds") List<UUID> customerIds);

    @Query(value = """
    SELECT 
        c.id,
        c.name,
        c.email,
        c.phone,
        c.city,
        c.gender,
        c.created_at,
        COUNT(o.id) AS order_count,
        COALESCE(SUM(o.amount), 0) AS total_spent,
        MAX(o.ordered_at) AS last_order_at
    FROM customers c
    LEFT JOIN orders o ON o.customer_id = c.id
    GROUP BY c.id, c.name, c.email, c.phone, c.city, c.gender, c.created_at
    HAVING :#{#havingClause} = :#{#havingClause}
    """, nativeQuery = true)
    List<CustomerSummary> findByDynamicHaving(@Param("havingClause") String havingClause);
}