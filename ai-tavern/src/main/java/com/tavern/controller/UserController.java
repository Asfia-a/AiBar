package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.entity.User;
import com.tavern.interceptor.UserContext;
import com.tavern.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Result<?> profile() {
        return Result.ok(userService.getProfile(UserContext.getUserId()));
    }

    @PutMapping("/profile")
    public Result<?> updateProfile(@RequestBody User profile) {
        return Result.ok(userService.updateProfile(UserContext.getUserId(), profile));
    }

    @GetMapping("/token/balance")
    public Result<?> balance() {
        return Result.ok(userService.tokenBalance(UserContext.getUserId()));
    }

    @GetMapping("/token/logs")
    public Result<?> logs(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.tokenLogs(UserContext.getUserId(), page, size));
    }

    @PostMapping("/recharge")
    public Result<?> recharge(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String packageId = body.get("packageId") == null ? null : body.get("packageId").toString();
        String channel = body.get("channel") == null ? "mock" : body.get("channel").toString();
        boolean mock = body.get("mock") == null || Boolean.parseBoolean(body.get("mock").toString());
        return Result.ok(userService.recharge(UserContext.getUserId(), amount, packageId, channel, mock));
    }

    @PostMapping("/check-in")
    public Result<?> checkIn() {
        return Result.ok(userService.checkIn(UserContext.getUserId()));
    }

    @GetMapping("/check-in/record")
    public Result<?> checkInRecord() {
        return Result.ok(userService.checkInRecords(UserContext.getUserId()));
    }
}
