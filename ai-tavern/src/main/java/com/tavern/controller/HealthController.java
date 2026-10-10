package com.tavern.controller;

import com.tavern.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<?> health() {
        return Result.ok(Map.of("status", "UP"));
    }
}
