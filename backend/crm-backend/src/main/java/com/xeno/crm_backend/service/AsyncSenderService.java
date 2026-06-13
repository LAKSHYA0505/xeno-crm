package com.xeno.crm_backend.service;

import com.xeno.crm_backend.domain.entity.CampaignEvent;
import com.xeno.crm_backend.domain.entity.CampaignLog;
import com.xeno.crm_backend.domain.enums.MessageStatus;
import com.xeno.crm_backend.repository.CampaignEventRepository;
import com.xeno.crm_backend.repository.CampaignLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncSenderService {

    private final CampaignLogRepository   campaignLogRepository;
    private final CampaignEventRepository campaignEventRepository;
    private final RestTemplate            restTemplate;

    @Value("${channel.service.url:http://localhost:9090}")
    private String channelServiceUrl;

    @Async("taskExecutor")
    public void sendAll(List<UUID> logIds, String channel) {
        log.info("[async] Starting sendAll for {} logs", logIds.size());

        for (UUID logId : logIds) {
            try {
                CampaignLog freshLog = campaignLogRepository.findByIdWithCustomer(logId)
                        .orElse(null);
                if (freshLog == null) {
                    log.warn("[async] Log not found: {}", logId);
                    continue;
                }

                String url = channelServiceUrl + "/send";

                Map<String, Object> payload = Map.of(
                        "logId",     freshLog.getId().toString(),
                        "recipient", freshLog.getCustomer().getPhone(),
                        "message",   freshLog.getPersonalizedMessage(),
                        "channel",   channel
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity =
                        new HttpEntity<>(payload, headers);

                restTemplate.postForEntity(url, entity, String.class);

                freshLog.setStatus(MessageStatus.SENT);
                freshLog.setSentAt(LocalDateTime.now());
                campaignLogRepository.save(freshLog);

                campaignEventRepository.save(CampaignEvent.builder()
                        .log(freshLog)
                        .eventType("sent")
                        .occurredAt(LocalDateTime.now())
                        .build());

            } catch (Exception e) {
                log.warn("[async] Failed logId {}: {}", logId, e.getMessage());
                campaignLogRepository.findByIdWithCustomer(logId).ifPresent(l -> {
                    l.setStatus(MessageStatus.FAILED);
                    campaignLogRepository.save(l);
                });
            }
        }
        log.info("[async] sendAll complete for {} logs", logIds.size());
    }
}
