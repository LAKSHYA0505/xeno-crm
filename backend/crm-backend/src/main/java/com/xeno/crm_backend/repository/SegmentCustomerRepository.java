package com.xeno.crm_backend.repository;


import com.xeno.crm_backend.domain.entity.SegmentCustomer;
import com.xeno.crm_backend.domain.entity.SegmentCustomerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SegmentCustomerRepository extends JpaRepository<SegmentCustomer, SegmentCustomerId> {

    List<SegmentCustomer> findBySegmentId(UUID segmentId);

    void deleteBySegmentId(UUID segmentId);
}