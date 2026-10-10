package com.tavern.service;

import com.tavern.common.RedisConstant;
import com.tavern.entity.CharacterCard;
import com.tavern.mapper.CharacterMapper;
import com.tavern.mapper.ChatMessageMapper;
import com.tavern.mapper.ChatSessionMapper;
import com.tavern.mapper.TokenLogMapper;
import com.tavern.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserMapper userMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CharacterMapper characterMapper;
    private final TokenLogMapper tokenLogMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public Map<String, Object> stats() {
        Map<String, Object> map = new HashMap<>();
        map.put("userCount", userMapper.countAll());
        map.put("todayNewUsers", userMapper.countTodayNew());
        map.put("sessionCount", sessionMapper.countAll());
        map.put("todaySessions", sessionMapper.countToday());
        map.put("todayTokens", messageMapper.sumTodayTokens());
        map.put("totalTokens", messageMapper.sumAllTokens());
        map.put("pendingAudit", characterMapper.countByStatus(1));
        map.put("onlineCharacters", characterMapper.countByStatus(2));
        long dau = 0;
        try {
            Long size = stringRedisTemplate.opsForSet().size(RedisConstant.DAU_PREFIX + LocalDate.now());
            dau = size == null ? 0 : size;
        } catch (Exception ignored) {
        }
        map.put("dau", dau);
        return map;
    }

    public Map<String, Object> trend(int days) {
        Map<String, Object> map = new HashMap<>();
        map.put("users", userMapper.userTrend(days));
        map.put("sessions", sessionMapper.sessionTrend(days));
        map.put("tokens", tokenLogMapper.tokenTrend(days));
        return map;
    }

    public List<CharacterCard> hotCharacters(int limit) {
        return characterMapper.hot(limit);
    }
}
