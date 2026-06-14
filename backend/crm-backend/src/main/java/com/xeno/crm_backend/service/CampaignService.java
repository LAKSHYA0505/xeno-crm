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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import com.xeno.crm_backend.dto.request.FollowUpRequest;
import com.xeno.crm_backend.dto.response.FollowUpRecommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository        campaignRepository;
    private final CampaignLogRepository     campaignLogRepository;
    private final CampaignEventRepository   campaignEventRepository;
    private final SegmentRepository         segmentRepository;
    private final SegmentCustomerRepository segmentCustomerRepository;
    private final CustomerRepository        customerRepository;
    private final OrderRepository           orderRepository;
    private final AIService                 aiService;
    private final RestTemplate              restTemplate;

    @Value("${channel.service.url:http://localhost:9090}")
    private String channelServiceUrl;

    @Lazy
    private final SegmentService segmentService;
    private final AsyncSenderService asyncSenderService;

    // ---------------------------------------------------
    // Cumulative funnel buckets — a log counts toward a stage
    // if it reached that stage OR ANY LATER stage.
    // This is what makes the funnel monotonic
    // (sent >= delivered >= opened >= clicked >= converted).
    // ---------------------------------------------------
    private static final List<MessageStatus> REACHED_SENT = List.of(
            MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.OPENED,
            MessageStatus.READ, MessageStatus.CLICKED, MessageStatus.CONVERTED);

    private static final List<MessageStatus> REACHED_DELIVERED = List.of(
            MessageStatus.DELIVERED, MessageStatus.OPENED, MessageStatus.READ,
            MessageStatus.CLICKED, MessageStatus.CONVERTED);

    private static final List<MessageStatus> REACHED_OPENED = List.of(
            MessageStatus.OPENED, MessageStatus.READ,
            MessageStatus.CLICKED, MessageStatus.CONVERTED);

    private static final List<MessageStatus> REACHED_CLICKED = List.of(
            MessageStatus.CLICKED, MessageStatus.CONVERTED);

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
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) continue;

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

        // Step 5 — Fire async sends (non-blocking, after commit)
        List<UUID> logIds = logs.stream().map(CampaignLog::getId).toList();
        String channel = campaign.getChannel();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        asyncSenderService.sendAll(logIds, channel);
                    }
                }
        );

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

        boolean advanced = shouldUpdateStatus(log.getStatus(), newStatus);
        if (advanced) {
            log.setStatus(newStatus);
            log.setUpdatedAt(LocalDateTime.now());
        }

        // Record revenue ONCE — guard against duplicate 'converted' callbacks
        // double-counting (channel service retries up to 3x).
        if (newStatus == MessageStatus.CONVERTED
                && receipt.getOrderValue() != null
                && log.getOrderValue() == null) {
            log.setOrderValue(receipt.getOrderValue());
        }

        campaignLogRepository.save(log);

        // Always record the event (append-only audit trail)
        CampaignEvent event = CampaignEvent.builder()
                .log(log)
                .eventType(receipt.getStatus())
                .occurredAt(LocalDateTime.now())
                .build();
        campaignEventRepository.save(event);

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

        long total     = campaignLogRepository.countByCampaign_Id(campaignId);
        long sent      = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_SENT);
        long delivered = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_DELIVERED);
        long opened    = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_OPENED);
        long clicked   = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_CLICKED);
        long converted = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.CONVERTED);
        long failed    = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.FAILED);

        BigDecimal revenue = campaignLogRepository.sumOrderValueByCampaignId(campaignId);

        String summary = aiService.generateCampaignSummary(
                total, sent, delivered, opened, clicked, converted, revenue, failed,
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
            return existing.stream().map(SegmentCustomer::getCustomerId).toList();
        }
        return segmentService.executeRules(segment.getRules());
    }

    private String personalizeMessage(String template, Customer customer) {
        Order lastOrder = orderRepository.findLatestByCustomerId(customer.getId());
        String lastProduct = "our latest collection";
        if (lastOrder != null && lastOrder.getItems() != null) {
            try {
                String items = lastOrder.getItems();
                int nameIdx = items.indexOf("\"name\":");
                if (nameIdx >= 0) {
                    int start = items.indexOf("\"", nameIdx + 7) + 1;
                    int end   = items.indexOf("\"", start);
                    lastProduct = items.substring(start, end);
                }
            } catch (Exception ignored) {}
        }
        return template
                .replace("{" + "{name}" + "}", customer.getName().split(" ")[0])
                .replace("{" + "{last_product}" + "}", lastProduct);
    }

    private void checkAndCompleteCampaign(UUID campaignId) {
        long total  = campaignLogRepository.countByCampaign_Id(campaignId);
        long queued = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.QUEUED);
        long sent   = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.SENT);

        // Done when nothing is queued and nothing is still in-flight at SENT.
        if (queued == 0 && sent == 0 && total > 0) {
            campaignRepository.findById(campaignId).ifPresent(c -> {
                if (c.getStatus() == CampaignStatus.LAUNCHED) {
                    c.setStatus(CampaignStatus.COMPLETED);
                    campaignRepository.save(c);
                }
            });
        }
    }

    private boolean shouldUpdateStatus(MessageStatus current, MessageStatus incoming) {
        if (current == MessageStatus.FAILED || current == MessageStatus.CONVERTED) {
            return false;
        }
        if (incoming == MessageStatus.FAILED) {
            return current == MessageStatus.QUEUED || current == MessageStatus.SENT;
        }
        List<MessageStatus> order = List.of(
                MessageStatus.QUEUED,
                MessageStatus.SENT,
                MessageStatus.DELIVERED,
                MessageStatus.OPENED,
                MessageStatus.READ,
                MessageStatus.CLICKED,
                MessageStatus.CONVERTED
        );
        return order.indexOf(incoming) > order.indexOf(current);
    }

    private MessageStatus parseStatus(String status) {
        return switch (status.toLowerCase()) {
            case "sent"      -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "failed"    -> MessageStatus.FAILED;
            case "opened"    -> MessageStatus.OPENED;
            case "read"      -> MessageStatus.READ;
            case "clicked"   -> MessageStatus.CLICKED;
            case "converted" -> MessageStatus.CONVERTED;
            default          -> MessageStatus.DELIVERED;
        };
    }

    // ── AI follow-up: recommendation (no DB writes) ────────────────────
    public FollowUpRecommendation getFollowUpRecommendation(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        long delivered = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_DELIVERED);
        long opened    = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_OPENED);
        long clicked   = campaignLogRepository.countByCampaign_IdAndStatusIn(campaignId, REACHED_CLICKED);
        long converted = campaignLogRepository.countByCampaign_IdAndStatus(campaignId, MessageStatus.CONVERTED);

        // Non-openers = delivered but never advanced past DELIVERED (funnel is monotonic)
        List<UUID> nonOpenerIds = campaignLogRepository
                .findCustomerIdsByCampaignAndStatusIn(campaignId, List.of(MessageStatus.DELIVERED));

        String audience = audienceOf(campaign);
        String suggestedChannel = escalateChannel(campaign.getChannel());

        String reason;
        String message;
        if (nonOpenerIds.isEmpty()) {
            reason  = "Every delivered message was opened — no non-openers to re-target right now.";
            message = "";
        } else {
            reason  = aiService.recommendFollowUp(audience, delivered, opened, clicked,
                    converted, nonOpenerIds.size(), suggestedChannel);
            message = aiService.generateMessageTemplate(
                    "Re-engage customers from \"" + audience + "\" who received but did NOT open "
                            + "the previous message. Use a fresh hook and a clear reason to look again.");
        }

        return FollowUpRecommendation.builder()
                .sourceCampaignId(campaignId)
                .basis("non-openers")
                .targetCount(nonOpenerIds.size())
                .suggestedChannel(suggestedChannel)
                .suggestedMessage(message)
                .reason(reason)
                .build();
    }

    // ── AI follow-up: create explicit-membership segment + campaign, then launch ──
    @Transactional
    public CampaignResponse createAndLaunchFollowUp(UUID sourceCampaignId, FollowUpRequest req) {
        Campaign source = campaignRepository.findById(sourceCampaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        List<UUID> targetIds = campaignLogRepository
                .findCustomerIdsByCampaignAndStatusIn(sourceCampaignId, List.of(MessageStatus.DELIVERED));
        if (targetIds.isEmpty()) {
            throw new RuntimeException("No non-openers to re-target.");
        }

        String audience = audienceOf(source);

        // 1) Explicit-membership segment. rules is valid JSON but never executed,
        //    because we pre-materialize segment_customers below and
        //    getOrMaterializeSegmentCustomers() returns existing rows first.
        Segment segment = segmentRepository.save(Segment.builder()
                .name("Re-engage · " + truncate(source.getName(), 60))
                .description("Non-openers from \"" + audience + "\"")
                .nlQuery("Follow-up: customers who did not open \"" + audience + "\"")
                .rules("{\"operator\":\"AND\",\"conditions\":[],\"source\":\"followup\","
                        + "\"parentCampaignId\":\"" + sourceCampaignId + "\"}")
                .customerCount(targetIds.size())
                .build());

        UUID segId = segment.getId();
        segmentCustomerRepository.saveAll(targetIds.stream()
                .map(cid -> SegmentCustomer.builder().segmentId(segId).customerId(cid).build())
                .toList());

        // 2) Draft campaign (marketer-approved overrides win)
        String channel = nonBlank(req != null ? req.getChannel() : null, escalateChannel(source.getChannel()));
        String name    = nonBlank(req != null ? req.getName() : null, "Re-engage · " + truncate(source.getName(), 40));
        String message = nonBlank(req != null ? req.getMessage() : null,
                aiService.generateMessageTemplate("Re-engage non-openers from \"" + audience + "\"."));

        Campaign followUp = campaignRepository.save(Campaign.builder()
                .name(name)
                .segment(segment)
                .messageTemplate(message)
                .channel(channel)
                .status(CampaignStatus.DRAFT)
                .build());

        // 3) Launch it (membership already materialized → sends to exactly these customers)
        return launchCampaign(followUp.getId());
    }

    // ── helpers ────────────────────────────────────────────────────────
    private String audienceOf(Campaign c) {
        return c.getSegment().getDescription() != null
                ? c.getSegment().getDescription()
                : c.getSegment().getNlQuery();
    }

    private String escalateChannel(String original) {
        if (original == null) return "whatsapp";
        return switch (original.toLowerCase()) {
            case "whatsapp" -> "sms";
            case "sms"      -> "email";
            default          -> "whatsapp";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private CampaignResponse toResponse(Campaign c) {
        UUID id = c.getId();

        long total     = campaignLogRepository.countByCampaign_Id(id);
        long queued    = campaignLogRepository.countByCampaign_IdAndStatus(id, MessageStatus.QUEUED);
        long failed    = campaignLogRepository.countByCampaign_IdAndStatus(id, MessageStatus.FAILED);

        // Cumulative funnel counts (reached-stage-or-beyond) — THE FIX.
        long sent      = campaignLogRepository.countByCampaign_IdAndStatusIn(id, REACHED_SENT);
        long delivered = campaignLogRepository.countByCampaign_IdAndStatusIn(id, REACHED_DELIVERED);
        long opened    = campaignLogRepository.countByCampaign_IdAndStatusIn(id, REACHED_OPENED);
        long clicked   = campaignLogRepository.countByCampaign_IdAndStatusIn(id, REACHED_CLICKED);
        long converted = campaignLogRepository.countByCampaign_IdAndStatus(id, MessageStatus.CONVERTED);

        BigDecimal totalRevenue = campaignLogRepository.sumOrderValueByCampaignId(id);

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
                .queued(queued)
                .sent(sent)
                .delivered(delivered)
                .failed(failed)
                .opened(opened)
                .clicked(clicked)
                .converted(converted)
                .totalRevenue(totalRevenue)
                .build();
    }
}