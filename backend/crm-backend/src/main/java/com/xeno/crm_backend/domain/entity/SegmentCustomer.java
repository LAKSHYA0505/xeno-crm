package com.xeno.crm_backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "segment_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(SegmentCustomerId.class)
public class SegmentCustomer {

    @Id
    @Column(name = "segment_id")
    private UUID segmentId;

    @Id
    @Column(name = "customer_id")
    private UUID customerId;
}