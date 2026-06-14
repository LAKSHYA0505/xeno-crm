package com.xeno.crm_backend.controller;

import com.xeno.crm_backend.dto.request.NLQueryRequest;
import com.xeno.crm_backend.dto.request.SegmentRequest;
import com.xeno.crm_backend.dto.response.SegmentResponse;
import com.xeno.crm_backend.service.AIService;
import com.xeno.crm_backend.service.SegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/segments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SegmentController {


    private final SegmentService segmentService;
    private final AIService aiService;

    // Step 1 — NL text → Gemini → rules JSON + preview
    // This is where AI-native happens
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseNLQuery(
            @RequestBody NLQueryRequest request) {
        String rulesJson = aiService.parseNLToRules(request.getQuery());
        Map<String, Object> preview = segmentService.previewRules(rulesJson);
        preview.put("message", aiService.generateMessageTemplate(request.getQuery()));
        return ResponseEntity.ok(preview);
    }

    // Step 2 — Save the segment after marketer approves preview
    @PostMapping
    public ResponseEntity<SegmentResponse> createSegment(
            @RequestBody SegmentRequest request) {
        return ResponseEntity.ok(segmentService.createSegment(request));
    }

    @GetMapping
    public ResponseEntity<List<SegmentResponse>> getAllSegments() {
        return ResponseEntity.ok(segmentService.getAllSegments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SegmentResponse> getSegment(@PathVariable UUID id) {
        return ResponseEntity.ok(segmentService.getSegmentById(id));
    }

    @PostMapping("/generate-message")
    public ResponseEntity<Map<String, String>> generateMessage(
            @RequestBody Map<String, String> body) {
        String msg = aiService.generateMessageTemplate(
                body.get("segmentDescription")
        );
        return ResponseEntity.ok(Map.of("message", msg));
    }

    // NL refine of an AI-drafted message
    @PostMapping("/refine-message")
    public ResponseEntity<Map<String, String>> refineMessage(
            @RequestBody Map<String, String> body) {
        String refined = aiService.refineMessage(
                body.getOrDefault("message", ""),
                body.getOrDefault("instruction", ""),
                body.getOrDefault("segmentDescription", ""));
        return ResponseEntity.ok(Map.of("message", refined));
    }
}