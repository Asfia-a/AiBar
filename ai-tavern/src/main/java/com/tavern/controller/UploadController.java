package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        String url = uploadService.upload(file);
        return Result.ok(Map.of("url", url));
    }
}
