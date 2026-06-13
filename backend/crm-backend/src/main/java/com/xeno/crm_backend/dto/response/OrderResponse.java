package com.xeno.crm_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {
    private UUID id;
    private BigDecimal amount;
    private String items;
    private String channel;
    private LocalDateTime orderedAt;
}