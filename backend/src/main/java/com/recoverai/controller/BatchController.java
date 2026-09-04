package com.recoverai.controller;

import com.recoverai.dto.request.BatchProcessRequest;
import com.recoverai.dto.response.BatchResultResponse;
import com.recoverai.service.BatchProcessorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchProcessorService batchProcessorService;

    @PostMapping("/process")
    public BatchResultResponse process(@Valid @RequestBody BatchProcessRequest request) {
        return batchProcessorService.processBatch(request);
    }
}