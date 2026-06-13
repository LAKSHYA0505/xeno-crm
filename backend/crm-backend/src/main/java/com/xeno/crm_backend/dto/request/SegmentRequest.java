package com.xeno.crm_backend.dto.request;


import lombok.Data;

@Data
public class SegmentRequest {
    private String name;
    private String description;
    private String nlQuery;   // original natural language input
    private String rules;     // JSON rules string from AI
}