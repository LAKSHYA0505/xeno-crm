package com.xeno.crm_backend.service;


import com.xeno.crm_backend.domain.entity.*;
import com.xeno.crm_backend.domain.enums.CampaignStatus;
import com.xeno.crm_backend.domain.enums.MessageStatus;
import com.xeno.crm_backend.dto.request.CampaignRequest;
import com.xeno.crm_backend.dto.request.ReceiptRequest;
import com.xeno.crm_backend.dto.response.CampaignResponse;
import com.xeno.crm_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository       campaignRepository;
    private final CampaignLogRepository    campaignLogRepository;
    private final CampaignEventRepository  campaignEventRepository;
    private final SegmentRepository        segmentRepository;
    private final SegmentCustomerRepository segmentCustomerRepository;
    private final CustomerRepository       customerRepository;
    private final OrderRepository          orderRepository;
    private final AIService                aiService;
    private final RestTemplate             restTemplate;

    @Value("${channel.service.url:http://localhost:9090}")
    private String channelServiceUrl;

    @Lazy
    private final SegmentService segmentService;

    private final AsyncSenderService asyncSenderService;
    // ---------------------------------------------------
    // CREATE campaign (draft)
    // ---------------------------------------------------
    @Transactional
    public CampaignResponse createCampaign(CampaignRequest request) {
        Segment segment = segmentRepository.findById(request.getSegmentId())
                .orElseThrow(() -> new RuntimeException("Segment not found"));

        Campaign campaign = Campaign.builder()
                .name(request.getName())
                .segment(segment)
                .messageTemplate(request.getMessageTemplate())
                .channel(request.getChannel())
                .status(CampaignStatus.DRAFT)
                .build();

        return toResponse(campaignRepository.save(campaign));
    }

    // ---------------------------------------------------
    // LAUNCH campaign
    // 1. Materialize segment_customers
    // 2. AI generates message template
    // 3. Create campaign_logs per customer
    // 4. Fire async sends to channel service
    // ---------------------------------------------------
    @Transactional
    public CampaignResponse launchCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        if (campaign.getStatus() == CampaignStatus.LAUNCHED) {
            throw new RuntimeException("Campaign already launched");
        }

        Segment segment = campaign.getSegment();

        // Step 1 — Re-execute rules to get fresh customer list
        // (segment may have been created days ago)
        // We stored rules in segment, re-run them now
        // For simplicity use segment_customers if already populated,
        // otherwise re-execute rules
        List<UUID> customerIds = getOrMaterializeSegmentCustomers(segment);

        // Step 2 — AI generates personalized message template
        String aiTemplate = aiService.generateMessageTemplate(
                segment.getDescription() != null
                        ? segment.getDescription()
                        : segment.getNlQuery());

        // Use AI template if message_template is empty
        String template = (campaign.getMessageTemplate() == null
                || campaign.getMessageTemplate().isBlank())
                ? aiTemplate
                : campaign.getMessageTemplate();

        // Step 3 — Create campaign_logs per customer
        List<CampaignLog> logs = new ArrayList<>();
        for (UUID customerId : customerIds) {
            Customer customer = customerRepository.findById(customerId)
                    .orElse(null);
            if (customer == null) continue;

            // Personalize message
            String personalized = personalizeMessage(template, customer);

            CampaignLog log = CampaignLog.builder()
                    .campaign(campaign)
                    .customer(customer)
                    .personalizedMessage(personalized)
                    .status(MessageStatus.QUEUED)
                    .retryCount(0)
                    .build();
            logs.add(log);
        }
        campaignLogRepository.saveAll(logs);

        // Step 4 — Update campaign status
        campaign.setStatus(CampaignStatus.LAUNCHED);
        campaign.setLaunchedAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        // Step 5 — Fire async sends (non-blocking)
        List<UUID> logIds = logs.stream()
                .map(CampaignLog::getId)
                .toList();
        asyncSenderService.sendAll(logIds, campaign.getChannel());

        return toResponse(campaign);
    }

    // ---------------------------------------------------
    // RECEIPT — called by channel service callback
    // ---------------------------------------------------
    @Transactional
    public void processReceipt(ReceiptRequest receipt) {
        CampaignLog log = campaignLogRepository.findById(receipt.getLogId())
                .orElseThrow(() -> new RuntimeException("Log not found: " + receipt.getLogId()));

        MessageStatus newStatus = parseStatus(receipt.getStatus());

        // Only advance status, never go backwards
        if (shouldUpdateStatus(log.getStatus(), newStatus)) {
            log.setStatus(newStatus);
            log.setUpdatedAt(LocalDateTime.now());
            campaignLogRepository.save(log);
        }

        // Always record the event (append-only)
        CampaignEvent event = CampaignEvent.builder()
                .log(log)
                .eventType(receipt.getStatus())
                .occurredAt(LocalDateTime.now())
                .build();
        campaignEventRepository.save(event);

        // Check if campaign is complete
        checkAndCompleteCampaign(log.getCampaign().getId());
    }

    // ---------------------------------------------------
    // STATS
    // ---------------------------------------------------
    public CampaignResponse getCampaignStats(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));
        return toResponse(campaign);
    }


    public List<CampaignResponse> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------
    // AI SUMMARY — generate and persist
    // ---------------------------------------------------
    @Transactional
    public String generateAndSaveSummary(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        long sent      = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.SENT);
        long delivered = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.DELIVERED);
        long opened    = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.OPENED);
        long clicked   = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.CLICKED);
        long failed    = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.FAILED);
        long total     = campaignLogRepository.countByCampaign_Id(campaignId);

        String summary = aiService.generateCampaignSummary(
                total, delivered, opened, clicked, failed,
                campaign.getSegment().getDescription() != null
                        ? campaign.getSegment().getDescription()
                        : campaign.getSegment().getNlQuery()
        );

        campaign.setAiSummary(summary);
        campaignRepository.save(campaign);
        return summary;
    }

    // ---------------------------------------------------
    // PRIVATE HELPERS
    // ---------------------------------------------------





    private List<UUID> getOrMaterializeSegmentCustomers(Segment segment) {
        List<SegmentCustomer> existing =
                segmentCustomerRepository.findBySegmentId(segment.getId());

        if (!existing.isEmpty()) {
            return existing.stream()
                    .map(SegmentCustomer::getCustomerId)
                    .toList();
        }

        // Re-execute rules fresh at launch time
        return segmentService.executeRules(segment.getRules());
    }

    private String personalizeMessage(String template, Customer customer) {
        // Get last product
        Order lastOrder = orderRepository.findLatestByCustomerId(customer.getId());
        String lastProduct = "our latest collection";
        if (lastOrder != null && lastOrder.getItems() != null) {
            try {
                // items is JSON array string — extract first product name
                String items = lastOrder.getItems();
                // simple parse: find "name":"..."
                int nameIdx = items.indexOf("\"name\":");
                if (nameIdx >= 0) {
                    int start = items.indexOf("\"", nameIdx + 7) + 1;
                    int end   = items.indexOf("\"", start);
                    lastProduct = items.substring(start, end);
                }
            } catch (Exception ignored) {}
        }

        return template
                .replace("{{name}}", customer.getName().split(" ")[0])
                .replace("{{last_product}}", lastProduct);
    }

    private void checkAndCompleteCampaign(UUID campaignId) {
        long total  = campaignLogRepository.countByCampaign_Id(campaignId);
        long queued = campaignLogRepository.countByCampaign_IdAndStatus(
                campaignId, MessageStatus.QUEUED);

        if (queued == 0 && total > 0) {
            campaignRepository.findById(campaignId).ifPresent(c -> {
                if (c.getStatus() == CampaignStatus.LAUNCHED) {
                    c.setStatus(CampaignStatus.COMPLETED);
                    campaignRepository.save(c);
                }
            });
        }
    }

    private boolean shouldUpdateStatus(MessageStatus current, MessageStatus incoming) {
        // Terminal states — never overwrite
        if (current == MessageStatus.FAILED || current == MessageStatus.CLICKED) {
            return false;
        }

        // FAILED can only be set from QUEUED or SENT
        if (incoming == MessageStatus.FAILED) {
            return current == MessageStatus.QUEUED || current == MessageStatus.SENT;
        }

        List<MessageStatus> order = List.of(
                MessageStatus.QUEUED,
                MessageStatus.SENT,
                MessageStatus.DELIVERED,
                MessageStatus.OPENED,
                MessageStatus.READ,
                MessageStatus.CLICKED
        );

        int currentIdx  = order.indexOf(current);
        int incomingIdx = order.indexOf(incoming);
        return incomingIdx > currentIdx;
    }

    private MessageStatus parseStatus(String status) {
        return switch (status.toLowerCase()) {
            case "sent"      -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "failed"    -> MessageStatus.FAILED;
            case "opened"    -> MessageStatus.OPENED;
            case "read"      -> MessageStatus.READ;
            case "clicked"   -> MessageStatus.CLICKED;
            default          -> MessageStatus.DELIVERED;
        };
    }

    private CampaignResponse toResponse(Campaign c) {
        long total     = campaignLogRepository.countByCampaign_Id(c.getId());
        long sent      = campaignLogRepository.countByCampaign_IdAndStatus(c.getId(), MessageStatus.SENT);
        long delivered = campaignLogRepository.countByCampaign_IdAndStatus(c.getId(), MessageStatus.DELIVERED);
        long failed    = campaignLogRepository.countByCampaign_IdAndStatus(c.getId(), MessageStatus.FAILED);
        long opened    = campaignLogRepository.countByCampaign_IdAndStatus(c.getId(), MessageStatus.OPENED);
        long clicked   = campaignLogRepository.countByCampaign_IdAndStatus(c.getId(), MessageStatus.CLICKED);

        return CampaignResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .segmentId(c.getSegment() != null ? c.getSegment().getId() : null)
                .segmentName(c.getSegment() != null ? c.getSegment().getName() : null)
                .messageTemplate(c.getMessageTemplate())
                .channel(c.getChannel())
                .status(c.getStatus())
                .aiSummary(c.getAiSummary())
                .launchedAt(c.getLaunchedAt())
                .createdAt(c.getCreatedAt())
                .totalLogs(total)
                .sent(sent)
                .delivered(delivered)
                .failed(failed)
                .opened(opened)
                .clicked(clicked)
                .build();
    }
}