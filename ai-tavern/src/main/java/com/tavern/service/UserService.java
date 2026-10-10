package com.tavern.service;

import com.github.pagehelper.PageHelper;
import com.tavern.config.BillingProperties;
import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.common.JwtUtil;
import com.tavern.common.PageResult;
import com.tavern.dto.LoginRequest;
import com.tavern.dto.RegisterRequest;
import com.tavern.entity.*;
import com.tavern.mapper.*;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final TokenLogMapper tokenLogMapper;
    private final CheckInMapper checkInMapper;
    private final RechargeMapper rechargeMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final BillingProperties billingProperties;

    public Map<String, Object> register(RegisterRequest req) {
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank() ? req.getUsername() : req.getNickname());
        // 对话按积分扣费；token_balance 保留为等价 Token 展示（points * rate）
        user.setPoints(100);
        user.setTokenBalance(billingProperties.tokensFromPoints(100));
        user.setMemberLevel(0);
        user.setDailyFreeToken(2000L);
        user.setRole(0);
        user.setStatus(1);
        user.setConsecutiveDays(0);
        userMapper.insert(user);
        return buildLoginResult(user);
    }

    public Map<String, Object> login(LoginRequest req) {
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }
        if (!BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        try {
            String key = "stats:dau:" + LocalDate.now();
            stringRedisTemplate.opsForSet().add(key, String.valueOf(user.getId()));
        } catch (Exception ignored) {
        }
        return buildLoginResult(user);
    }

    private Map<String, Object> buildLoginResult(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("user", sanitize(user));
        return map;
    }

    public User getProfile(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return sanitize(user);
    }

    public User updateProfile(Long userId, User profile) {
        profile.setId(userId);
        userMapper.updateProfile(profile);
        return getProfile(userId);
    }

    public Map<String, Object> tokenBalance(Long userId) {
        User user = userMapper.findById(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("points", user.getPoints() == null ? 0 : user.getPoints());
        map.put("tokenBalance", user.getTokenBalance());
        map.put("tokensPerPoint", billingProperties.getTokensPerPoint());
        map.put("dailyFreeToken", user.getDailyFreeToken());
        map.put("memberLevel", user.getMemberLevel());
        return map;
    }

    public PageResult<TokenLog> tokenLogs(Long userId, int page, int size) {
        PageHelper.startPage(page, size);
        return PageResult.of(tokenLogMapper.listByUser(userId));
    }

    @Transactional
    public Map<String, Object> recharge(Long userId, BigDecimal amount) {
        return recharge(userId, amount, null, null, true);
    }

    @Transactional
    public Map<String, Object> recharge(Long userId, BigDecimal amount, String packageId, String channel, boolean mock) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额无效");
        }
        if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            throw new BusinessException("单笔充值金额过大");
        }
        int points = resolveRechargePoints(packageId, amount);
        User user = userMapper.findById(userId);
        long before = user.getPoints() == null ? 0L : user.getPoints();
        userMapper.addPoints(userId, points);
        // 同步等价 Token，便于后台统计
        userMapper.addToken(userId, billingProperties.tokensFromPoints(points));

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo("R" + System.currentTimeMillis() + userId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setTokens((long) points);
        order.setPayType(channel == null || channel.isBlank() ? "mock" : channel);
        order.setStatus(1);
        rechargeMapper.insert(order);

        TokenLog tokenLog = new TokenLog();
        tokenLog.setUserId(userId);
        tokenLog.setTokensUsed(points);
        tokenLog.setBalanceBefore(before);
        tokenLog.setBalanceAfter(before + points);
        tokenLog.setType(2);
        tokenLogMapper.insert(tokenLog);

        Map<String, Object> map = new HashMap<>();
        map.put("points", points);
        map.put("tokens", billingProperties.tokensFromPoints(points));
        map.put("bonus", 0);
        map.put("balance", before + points);
        map.put("pointsBalance", before + points);
        map.put("tokensPerPoint", billingProperties.getTokensPerPoint());
        map.put("packageId", packageId);
        map.put("channel", channel == null ? "mock" : channel);
        map.put("mock", mock);
        return map;
    }

    /** 套餐直接发积分；未指定套餐时按金额档位 */
    private int resolveRechargePoints(String packageId, BigDecimal amount) {
        if (packageId != null) {
            return switch (packageId) {
                case "trial" -> 20;
                case "basic" -> 100;
                case "pro" -> 600;
                case "whale" -> 2000;
                default -> Math.max(10, amount.intValue() * 10);
            };
        }
        if (amount.compareTo(BigDecimal.valueOf(98)) >= 0) return 2000;
        if (amount.compareTo(BigDecimal.valueOf(30)) >= 0) return 600;
        if (amount.compareTo(BigDecimal.valueOf(6)) >= 0) return 100;
        return Math.max(10, amount.intValue() * 10);
    }

    @Transactional
    public Map<String, Object> checkIn(Long userId) {
        LocalDate today = LocalDate.now();
        if (checkInMapper.find(userId, today) != null) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
        }
        User user = userMapper.findById(userId);
        int consecutive = 1;
        if (user.getLastCheckIn() != null && user.getLastCheckIn().equals(today.minusDays(1))) {
            consecutive = (user.getConsecutiveDays() == null ? 0 : user.getConsecutiveDays()) + 1;
        }
        int points = 10 + Math.min(consecutive, 7) * 2;
        long before = user.getPoints() == null ? 0L : user.getPoints();
        // 签到只加积分；tokens 参数传 0，不再发 Token
        userMapper.checkIn(userId, today, consecutive, points, 0);

        CheckIn ci = new CheckIn();
        ci.setUserId(userId);
        ci.setCheckDate(today);
        ci.setPoints(points);
        ci.setConsecutive(consecutive);
        checkInMapper.insert(ci);

        TokenLog tokenLog = new TokenLog();
        tokenLog.setUserId(userId);
        tokenLog.setTokensUsed(points);
        tokenLog.setBalanceBefore(before);
        tokenLog.setBalanceAfter(before + points);
        tokenLog.setType(3);
        tokenLogMapper.insert(tokenLog);

        Map<String, Object> map = new HashMap<>();
        map.put("points", points);
        map.put("tokens", 0);
        map.put("consecutive", consecutive);
        return map;
    }

    public List<CheckIn> checkInRecords(Long userId) {
        return checkInMapper.listRecent(userId);
    }

    public PageResult<User> adminList(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<User> list = userMapper.list(keyword);
        list.forEach(this::sanitize);
        return PageResult.of(list);
    }

    public void banUser(Long id, int status) {
        userMapper.updateStatus(id, status);
    }

    public void setMemberLevel(Long id, int level) {
        userMapper.updateMemberLevel(id, level);
    }

    private User sanitize(User user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }
}
