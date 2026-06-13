package com.xeno.crm_backend.controller;

import com.xeno.crm_backend.dto.request.CampaignRequest;
import com.xeno.crm_backend.dto.request.ReceiptRequest;
import com.xeno.crm_backend.dto.response.CampaignResponse;
import com.xeno.crm_backend.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping("/api/campaigns")
    public ResponseEntity<CampaignResponse> createCampaign(
            @RequestBody CampaignRequest request) {
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    @PostMapping("/api/campaigns/{id}/launch")
    public ResponseEntity<CampaignResponse> launchCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.launchCampaign(id));
    }

    @GetMapping("/api/campaigns")
    public ResponseEntity<List<CampaignResponse>> getAllCampaigns() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    @GetMapping("/api/campaigns/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.getCampaignStats(id));
    }

    @PostMapping("/api/campaigns/{id}/ai-summary")
    public ResponseEntity<Map<String, String>> generateSummary(@PathVariable UUID id) {
        String summary = campaignService.generateAndSaveSummary(id);
        return ResponseEntity.ok(Map.of("summary", summary));
    }

    // Called by channel service — receipt webhook
    @PostMapping("/api/receipt")
    public ResponseEntity<Void> receiveReceipt(@RequestBody ReceiptRequest receipt) {
        campaignService.processReceipt(receipt);
        return ResponseEntity.ok().build();
    }
}