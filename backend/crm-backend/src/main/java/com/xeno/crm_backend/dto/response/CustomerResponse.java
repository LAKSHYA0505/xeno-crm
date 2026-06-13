package com.xeno.crm_backend.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String city;
    private String gender;
    private LocalDateTime createdAt;
    // computed from orders - passed in from service
    private Integer orderCount;
    private Double totalSpent;
    private LocalDateTime lastOrderAt;
}