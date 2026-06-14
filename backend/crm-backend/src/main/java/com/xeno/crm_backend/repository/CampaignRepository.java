package com.xeno.crm_backend.repository;

import com.xeno.crm_backend.domain.entity.Campaign;
import com.xeno.crm_backend.domain.enums.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findAllByOrderByCreatedAtDesc();
    //Returns all campaigns sorted newest first. Used for the campaigns list page.


    List<Campaign> findByStatus(CampaignStatus status);
    //Filter campaigns by status (DRAFT, LAUNCHED, COMPLETED). Could be used to find all active campaigns or all completed ones for analytics.


}