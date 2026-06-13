package com.xeno.crm_backend.repository;

import com.xeno.crm_backend.domain.entity.CampaignEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignEventRepository extends JpaRepository<CampaignEvent, UUID> {

    List<CampaignEvent> findByLog_IdOrderByOccurredAtAsc(UUID logId);
    //Get all events for a specific message log, in chronological order
    //to show -> sent at 10:01 → delivered at 10:03 → opened at 10:07 → clicked at 10:09
}