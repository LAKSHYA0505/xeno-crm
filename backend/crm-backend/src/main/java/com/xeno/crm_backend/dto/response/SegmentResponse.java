package com.xeno.crm_backend.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SegmentResponse {
    private UUID id;
    private String name;
    private String description;
    private String nlQuery;
    private String rules;
    private Integer customerCount;
    private LocalDateTime createdAt;
}