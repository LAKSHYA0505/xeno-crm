package com.xeno.crm_backend.dto.response;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface CustomerSummary {
    UUID getId();
    String getName();
    String getEmail();
    String getPhone();
    String getCity();
    String getGender();
    LocalDateTime getCreatedAt();
    Long getOrderCount();
    BigDecimal getTotalSpent();
    LocalDateTime getLastOrderAt();
}