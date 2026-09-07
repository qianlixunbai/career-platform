package com.careerplatform.ai.controller;

import com.careerplatform.ai.dto.rag.*;
import com.careerplatform.ai.service.RagService;
import com.careerplatform.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning-plans/{planId}/materials")
public class RagController {
    private final RagService rag;
    public RagController(RagService rag) { this.rag = rag; }
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @GetMapping("/rag/status")
    public Availability status(@PathVariable Long planId, @CurrentUserId Long userId) {
        return new Availability(rag.available(planId, userId));
    }
    @PostMapping("/rag/query")
    public RagAnswer query(@PathVariable Long planId, @CurrentUserId Long userId, @Valid @RequestBody RagQueryRequest request) {
        return rag.query(planId, userId, request);
    }
    @GetMapping("/{materialId}/chunks/{chunkId}")
    public RagCitation source(@PathVariable Long planId, @PathVariable Long materialId, @PathVariable Long chunkId,
                              @CurrentUserId Long userId) {
        return rag.source(planId, materialId, chunkId, userId, null);
    }
    public record Availability(boolean available) { }
}
