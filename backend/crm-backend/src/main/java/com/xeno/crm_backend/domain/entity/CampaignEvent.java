package com.xeno.crm_backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private CampaignLog log;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt = LocalDateTime.now();
}