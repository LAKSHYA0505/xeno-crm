package com.xeno.crm_backend.repository;

import com.xeno.crm_backend.domain.entity.CampaignLog;
import com.xeno.crm_backend.domain.enums.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignLogRepository extends JpaRepository<CampaignLog, UUID> {

    @Query("SELECT l FROM CampaignLog l JOIN FETCH l.customer JOIN FETCH l.campaign WHERE l.id = :id")
    Optional<CampaignLog> findByIdWithCustomer(@Param("id") UUID id);

    List<CampaignLog> findByCampaign_Id(UUID campaignId);

    long countByCampaign_Id(UUID campaignId);

    long countByCampaign_IdAndStatus(UUID campaignId, MessageStatus status);
    //Count messages with a specific status for a campaign

    List<CampaignLog> findByCampaign_IdAndStatus(UUID campaignId, MessageStatus status);

    @Query("SELECT COALESCE(SUM(l.orderValue), 0) FROM CampaignLog l WHERE l.campaign.id = :campaignId")
    BigDecimal sumOrderValueByCampaignId(@Param("campaignId") UUID campaignId);

    long countByCampaign_IdAndStatusIn(UUID campaignId, Collection<MessageStatus> statuses);
}