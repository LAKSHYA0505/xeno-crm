package com.xeno.crm_backend.domain.entity;

import com.xeno.crm_backend.domain.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "campaign_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "customer_id"}))
public class CampaignLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "personalized_message", columnDefinition = "TEXT")
    private String personalizedMessage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MessageStatus status = MessageStatus.QUEUED;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
