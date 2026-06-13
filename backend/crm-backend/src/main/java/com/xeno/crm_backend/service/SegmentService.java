package com.xeno.crm_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xeno.crm_backend.domain.entity.Segment;
import com.xeno.crm_backend.dto.request.SegmentRequest;
import com.xeno.crm_backend.dto.response.CustomerResponse;
import com.xeno.crm_backend.dto.response.SegmentResponse;
import com.xeno.crm_backend.repository.SegmentRepository;
import com.xeno.crm_backend.repository.SegmentCustomerRepository;
import com.xeno.crm_backend.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final SegmentCustomerRepository segmentCustomerRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------
    // CREATE — just saves segment + customerCount
    // segment_customers materialized at campaign launch
    // ---------------------------------------------------
    @Transactional
    public SegmentResponse createSegment(SegmentRequest request) {
        List<UUID> matchingIds = executeRules(request.getRules());

        Segment segment = Segment.builder()
                .name(request.getName())
                .description(request.getDescription())
                .nlQuery(request.getNlQuery())
                .rules(request.getRules())
                .customerCount(matchingIds.size())
                .build();

        return toResponse(segmentRepository.save(segment));
    }

    // ---------------------------------------------------
    // PREVIEW — runs rules, returns count + 5 samples
    // Called after AI parses NL query, before saving
    // ---------------------------------------------------
    public Map<String, Object> previewRules(String rulesJson) {
        List<UUID> matchingIds = executeRules(rulesJson);

        List<UUID> sampleIds = matchingIds.stream().limit(5).toList();
        List<CustomerResponse> sample = customerService
                .getCustomersWithStatsByIds(sampleIds);

        Map<String, Object> result = new HashMap<>();
        result.put("count", matchingIds.size());
        result.put("sample", sample);
        result.put("rules", rulesJson);
        return result;
    }

    // ---------------------------------------------------
    // GET ALL / GET ONE
    // ---------------------------------------------------
    public List<SegmentResponse> getAllSegments() {
        return segmentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SegmentResponse getSegmentById(UUID id) {
        return toResponse(segmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Segment not found: " + id)));
    }

    // ---------------------------------------------------
    // Used by CampaignService at launch time
    // ---------------------------------------------------
    public List<UUID> executeRules(String rulesJson) {
        try {
            JsonNode root       = objectMapper.readTree(rulesJson);
            String operator     = root.has("operator")
                    ? root.get("operator").asText() : "AND";
            JsonNode conditions = root.get("conditions");

            Map<String, Object> params        = new HashMap<>();
            List<String>        whereClauses  = new ArrayList<>();
            List<String>        havingClauses = new ArrayList<>();

            int i = 0;
            for (JsonNode condition : conditions) {
                String field     = condition.get("field").asText();
                String op        = condition.get("op").asText();
                String value     = condition.get("value").asText();
                String paramName = "param" + i;

                buildClauses(field, op, value, paramName,
                        params, whereClauses, havingClauses);
                i++;
            }

            // Build WHERE part
            String whereSQL = "WHERE 1=1";
            if (!whereClauses.isEmpty()) {
                whereSQL += " AND " + String.join(" AND ", whereClauses);
            }

            // Build HAVING part
            String joiner    = operator.equalsIgnoreCase("OR") ? " OR " : " AND ";
            String havingSQL = havingClauses.isEmpty() ? ""
                    : "HAVING " + String.join(joiner, havingClauses);

            String sql = """
                SELECT c.id::text
                FROM customers c
                LEFT JOIN orders o ON o.customer_id = c.id
                %s
                GROUP BY c.id
                %s
                """.formatted(whereSQL, havingSQL);

            jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
            params.forEach(query::setParameter);

            List<String> rawIds = query.getResultList();
            return rawIds.stream().map(UUID::fromString).toList();

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute segment rules: "
                    + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------
    // RULES BUILDER
    // Scalar customer fields → WHERE (correct semantics)
    // Aggregate order fields  → HAVING (correct semantics)
    // All values parameterized → no SQL injection
    // ---------------------------------------------------
    private void buildClauses(String field, String op, String value,
                              String paramName, Map<String, Object> params,
                              List<String> whereClauses,
                              List<String> havingClauses) {
        String sqlOp = switch (op) {
            case "gt"  -> ">";
            case "gte" -> ">=";
            case "lt"  -> "<";
            case "lte" -> "<=";
            case "eq"  -> "=";
            default    -> "=";
        };

        switch (field) {
            case "last_order_days_ago" -> {
                params.put(paramName, Double.parseDouble(value));
                havingClauses.add(
                        "EXTRACT(DAY FROM (NOW() - MAX(o.ordered_at))) %s :%s"
                                .formatted(sqlOp, paramName));
            }
            case "total_spent" -> {
                params.put(paramName, Double.parseDouble(value));
                havingClauses.add(
                        "COALESCE(SUM(o.amount), 0) %s :%s"
                                .formatted(sqlOp, paramName));
            }
            case "order_count" -> {
                params.put(paramName, Integer.parseInt(value));
                havingClauses.add(
                        "COUNT(o.id) %s :%s"
                                .formatted(sqlOp, paramName));
            }
            // Scalar fields → WHERE, not HAVING
            case "city" -> {
                params.put(paramName, value);
                whereClauses.add("c.city = :%s".formatted(paramName));
            }
            case "gender" -> {
                params.put(paramName, value);
                whereClauses.add("c.gender = :%s".formatted(paramName));
            }
        }
    }

    // ---------------------------------------------------
    // Mapper
    // ---------------------------------------------------
    private SegmentResponse toResponse(Segment s) {
        return SegmentResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .nlQuery(s.getNlQuery())
                .rules(s.getRules())
                .customerCount(s.getCustomerCount())
                .createdAt(s.getCreatedAt())
                .build();
    }
}