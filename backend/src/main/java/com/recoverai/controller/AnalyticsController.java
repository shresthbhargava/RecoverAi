package com.recoverai.controller;

import com.recoverai.analytics.AnalyticsService;
import com.recoverai.dto.response.AnalyticsSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary() {
        return analyticsService.getSummary();
    }

    // NOTE: /by-failure-type, /strategy-distribution, /time-series are added
    // in Phase 6 once the batch processor is producing enough case volume/variety
    // for those breakdowns to be meaningful.
}
