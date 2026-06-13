package com.xeno.crm_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    private final RestTemplate restTemplate;

    public String parseNLToRules(String naturalLanguageQuery) {
        String prompt = """
            You are a CRM query parser for a D2C footwear brand called SoleStreet.
            Convert the following natural language segment description into a JSON rules object.
            
            Available fields:
            - last_order_days_ago (number): days since customer's last order
            - total_spent (number): total amount spent across all orders in INR
            - order_count (number): total number of orders placed
            - city (string): customer's city (e.g. Mumbai, Delhi, Bangalore)
            - gender (string): male, female, or other
            - product_category (string): bought at least one order in this category —
              running, casual, lifestyle, or comfort
            
            Available operators: gt, gte, lt, lte, eq
            
            Examples:
            - "female customers in Mumbai who bought running shoes"
              → gender eq female, city eq Mumbai, product_category eq running
            - "customers who spent over 5000 but haven't ordered in 60 days"
              → total_spent gt 5000, last_order_days_ago gt 60
            
            Return ONLY valid JSON in this exact format, no explanation, no markdown, no code blocks:
            {"operator":"AND","conditions":[{"field":"field_name","op":"operator","value":"value"}]}
            
            Query: %s
            """.formatted(naturalLanguageQuery);

        return cleanJsonResponse(callGroq(prompt));
    }

    public String generateCampaignSummary(
            long sent, long delivered, long opened,
            long clicked, long failed, String segmentDescription) {

        String prompt = """
            Summarize this marketing campaign performance for a non-technical marketer.
            Be concise, warm, and actionable. Max 3 sentences.
            
            Campaign target: %s
            Sent: %d
            Delivered: %d (%.1f%%)
            Opened: %d (%.1f%%)
            Clicked: %d (%.1f%%)
            Failed: %d
            
            Give one specific recommendation at the end.
            """.formatted(
                segmentDescription,
                sent,
                delivered, sent > 0 ? (delivered * 100.0 / sent) : 0,
                opened, delivered > 0 ? (opened * 100.0 / delivered) : 0,
                clicked, opened > 0 ? (clicked * 100.0 / opened) : 0,
                failed);

        return callGroq(prompt);
    }

    public String generateMessageTemplate(String segmentDescription) {
        String prompt = """
            Write a personalized WhatsApp message for a D2C sneaker brand called SoleStreet.
            Target audience: %s
            
            Rules:
            - Use {{name}} as placeholder for customer name
            - Use {{last_product}} as placeholder for their last purchased product
            - Keep it under 160 characters
            - Friendly, conversational tone
            - Include a call to action
            - No hashtags
            
            Return ONLY the message text, nothing else.
            """.formatted(segmentDescription);

        return callGroq(prompt);
    }

    // ---------------------------------------------------
    // Core Groq API caller (OpenAI-compatible format)
    // ---------------------------------------------------
    private String callGroq(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 500
        );

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    groqApiUrl, entity, Map.class);

            var choices = (List<?>) response.getBody().get("choices");
            var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            return ((String) message.get("content")).trim();

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("AI service rate limited. Please try again in a moment.");
            }
            throw new RuntimeException("AI API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    private String cleanJsonResponse(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}