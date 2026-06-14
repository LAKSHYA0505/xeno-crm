package com.xeno.crm_backend.dto.request;

import lombok.Data;

@Data
public class FollowUpRequest {
    private String name;     // optional override for campaign name
    private String message;  // marketer-approved (possibly edited) message
    private String channel;  // marketer-chosen channel
}