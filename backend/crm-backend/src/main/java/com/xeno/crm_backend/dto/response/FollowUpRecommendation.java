package com.xeno.crm_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FollowUpRecommendation {
    private UUID sourceCampaignId;
    private String basis;            // e.g. "non-openers"
    private long targetCount;        // how many customers we'd re-target
    private String suggestedChannel; // escalated channel (whatsapp -> sms -> email)
    private String suggestedMessage; // AI-drafted re-engagement message
    private String reason;           // AI one-sentence justification
}