package com.xeno.crm_backend.dto.request;


import lombok.Data;
import java.util.UUID;

@Data
public class CampaignRequest {
    private String name;
    private UUID segmentId;
    private String messageTemplate;
    private String channel;
}