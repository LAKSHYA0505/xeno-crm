package com.xeno.crm_backend.repository;


import com.xeno.crm_backend.domain.entity.Segment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SegmentRepository extends JpaRepository<Segment, UUID> {
}