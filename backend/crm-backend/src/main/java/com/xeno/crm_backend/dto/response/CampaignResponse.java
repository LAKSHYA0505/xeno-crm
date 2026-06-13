package com.xeno.crm_backend.dto.response;


import com.xeno.crm_backend.domain.enums.CampaignStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CampaignResponse {
    private UUID id;
    private String name;
    private UUID segmentId;
    private String segmentName;
    private String messageTemplate;
    private String channel;
    private CampaignStatus status;
    private String aiSummary;
    private LocalDateTime launchedAt;
    private LocalDateTime createdAt;
    // stats
    private Long totalLogs;
    private Long sent;
    private Long delivered;
    private Long failed;
    private Long opened;
    private Long clicked;
}