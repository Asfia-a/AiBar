package com.tavern.service;

import com.tavern.common.BusinessException;
import com.tavern.common.RedisConstant;
import com.tavern.entity.Notification;
import com.tavern.entity.SensitiveWord;
import com.tavern.entity.ViolationLog;
import com.tavern.mapper.NotificationMapper;
import com.tavern.mapper.ViolationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内容审核。
 * <p>
 * 两个入口：
 * <ul>
 *   <li>{@link #auditSync} —— 发送前同步拦截，命中直接拒掉。用于用户输入、评论、叙事作品发布。</li>
 *   <li>{@link #auditAsync} —— 事后留痕，用于模型输出这类拦不住也不该拦的场景。</li>
 * </ul>
 * 匹配统一走 {@link SensitiveNormalizer} 归一化 + {@link SensitiveWordMatcher} 自动机，
 * 词库从 Redis 读（缺失时回源数据库），自动机在首次加载后常驻。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAuditService {

    private final ViolationMapper violationMapper;
    private final NotificationMapper notificationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SensitiveNormalizer normalizer;
    private final SensitiveWordMatcher matcher;

    @Value("${audit.cache-ttl-seconds:600}")
    private long cacheTtlSeconds;

    /**
     * 同步审核：返回命中的敏感词列表，空列表表示通过。
     * 调用方据此决定是否拒绝。
     */
    public List<String> auditSync(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return doMatch(content);
    }

    /**
     * 事后审核：命中则记违规日志并通知用户。
     * 模型输出走这里——输出已经流式推给用户了，拦不住也不必拦，
     * 但要留痕以便后台追溯。
     */
    @Async("taskExecutor")
    public void auditAsync(Long userId, Long sessionId, Long messageId, String content) {
        try {
            List<String> hit = doMatch(content);
            if (hit.isEmpty()) {
                return;
            }
            ViolationLog logEntity = new ViolationLog();
            logEntity.setUserId(userId);
            logEntity.setSessionId(sessionId);
            logEntity.setMessageId(messageId);
            logEntity.setContent(truncate(content));
            logEntity.setWords(String.join(",", hit));
            violationMapper.insert(logEntity);

            Notification n = new Notification();
            n.setUserId(userId);
            n.setType("violation");
            n.setTitle("内容违规提醒");
            n.setContent("您的内容触发敏感词：" + logEntity.getWords());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("内容审核失败: {}", e.getMessage());
        }
    }

    /** 违规时同步落库，用于用户输入这类「拦住就不再发」的场景 */
    public void recordSyncHit(Long userId, Long sessionId, Long messageId, String content, List<String> hit) {
        try {
            ViolationLog logEntity = new ViolationLog();
            logEntity.setUserId(userId);
            logEntity.setSessionId(sessionId);
            logEntity.setMessageId(messageId);
            logEntity.setContent(truncate(content));
            logEntity.setWords(String.join(",", hit));
            violationMapper.insert(logEntity);

            Notification n = new Notification();
            n.setUserId(userId);
            n.setType("violation");
            n.setTitle("内容违规提醒");
            n.setContent("您的消息触发敏感词：" + logEntity.getWords() + "，已被拦截");
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("违规记录失败: {}", e.getMessage());
        }
    }

    /** 违规日志，供管理端查看 */
    public List<ViolationLog> listViolations() {
        return violationMapper.listAll();
    }

    /** 词库列表，供管理端维护页 */
    public List<SensitiveWord> listWords() {
        return violationMapper.listWords();
    }

    /**
     * 新增敏感词。写库后清 Redis 缓存并自增版本号，
     * 审核侧下次匹配会检测到版本变化并重建自动机。
     */
    public void addWord(String word, Integer level) {
        if (!StringUtils.hasText(word)) {
            throw new BusinessException("敏感词不能为空");
        }
        SensitiveWord w = new SensitiveWord();
        w.setWord(word.trim());
        w.setLevel(level == null ? 1 : level);
        violationMapper.insertWord(w);
        refreshWordCache();
    }

    public void updateWord(Long id, String word, Integer level) {
        SensitiveWord w = new SensitiveWord();
        w.setId(id);
        w.setWord(word == null ? null : word.trim());
        w.setLevel(level);
        violationMapper.updateWord(w);
        refreshWordCache();
    }

    public void deleteWord(Long id) {
        violationMapper.deleteWord(id);
        refreshWordCache();
    }

    /** 把最新词库写回 Redis 并自增版本号，让各实例的自动机失效重建 */
    private void refreshWordCache() {
        try {
            List<String> words = violationMapper.listWords().stream()
                    .map(SensitiveWord::getWord)
                    .filter(w -> w != null && !w.isBlank())
                    .toList();
            stringRedisTemplate.delete(RedisConstant.SENSITIVE_WORDS);
            if (!words.isEmpty()) {
                stringRedisTemplate.opsForSet().add(RedisConstant.SENSITIVE_WORDS, words.toArray(new String[0]));
                stringRedisTemplate.expire(RedisConstant.SENSITIVE_WORDS, Duration.ofSeconds(cacheTtlSeconds));
            }
            stringRedisTemplate.opsForValue().increment(RedisConstant.SENSITIVE_VERSION);
        } catch (Exception e) {
            log.warn("刷新敏感词缓存失败，将回源数据库: {}", e.getMessage());
        }
    }

    private List<String> doMatch(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        ensureMatcher();
        String normalized = normalizer.normalize(content);
        return List.copyOf(matcher.match(normalized));
    }

    /** 词库版本变化时（增删改词）调用，下次匹配时重建 */
    public void invalidateMatcher() {
        matcher.build(List.of());
    }

    /**
     * 自动机懒加载 + 版本校验。
     * 每次匹配都读一次 Redis 里的版本号开销太大，这里用本地 volatile 记录
     * 上次加载的版本，只有版本变化才重建。
     */
    private volatile String loadedVersion;

    private void ensureMatcher() {
        String version = readVersion();
        if (version == null) {
            // Redis 不可用时退化为每次重建，规模小的情况下代价可接受
            rebuild();
            return;
        }
        if (!version.equals(loadedVersion) || matcher.wordCount() == 0) {
            rebuild();
            loadedVersion = version;
        }
    }

    private void rebuild() {
        matcher.build(loadNormalizedWords());
    }

    private String readVersion() {
        try {
            String v = stringRedisTemplate.opsForValue().get(RedisConstant.SENSITIVE_VERSION);
            if (v != null) {
                return v;
            }
            // 首次或词库刚被改动：用条数当版本号兜底
            Set<String> members = stringRedisTemplate.opsForSet().members(RedisConstant.SENSITIVE_WORDS);
            return "size:" + (members == null ? 0 : members.size());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 取词库并归一化，使词库与待检文本处于同一形式 */
    private List<String> loadNormalizedWords() {
        List<String> raw = loadSensitiveWords();
        return raw.stream()
                .filter(w -> w != null && !w.isBlank())
                .map(normalizer::normalizeWord)
                .filter(w -> !w.isBlank())
                .distinct()
                .toList();
    }

    private List<String> loadSensitiveWords() {
        try {
            Set<String> cached = stringRedisTemplate.opsForSet().members(RedisConstant.SENSITIVE_WORDS);
            if (cached != null && !cached.isEmpty()) {
                return new java.util.ArrayList<>(cached);
            }
        } catch (Exception ignored) {
        }
        List<SensitiveWord> words = violationMapper.listWords();
        return words == null ? List.of() : words.stream()
                .map(SensitiveWord::getWord)
                .filter(w -> w != null && !w.isBlank())
                .collect(Collectors.toList());
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
