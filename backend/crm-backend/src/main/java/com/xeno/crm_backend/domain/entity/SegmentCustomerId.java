package com.xeno.crm_backend.domain.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SegmentCustomerId implements Serializable {
    private UUID segmentId;
    private UUID customerId;
}