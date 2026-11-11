package com.tavern.service;

import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.dto.request.WorldInfoRequest;
import com.tavern.entity.CharacterCard;
import com.tavern.entity.WorldInfoEntry;
import com.tavern.mapper.CharacterMapper;
import com.tavern.mapper.WorldInfoMapper;
import com.tavern.prompt.TokenEstimator;
import com.tavern.prompt.WorldInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 世界书的管理侧（增删改查 + 命中预演）。
 *
 * <p>和 {@link WorldInfoService} 的分工：
 * <ul>
 *   <li>{@code prompt.WorldInfoService}：纯函数，负责「这一轮该插哪几条」，对话链路在调；</li>
 *   <li>本类：负责权限、字段兜底、落库，以及给管理页做「不花钱的命中预演」。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorldInfoAdminService {

    /** 单个角色最多存多少条，防止关键词爆炸式匹配拖慢每一轮对话 */
    private static final int MAX_ENTRIES_PER_CHARACTER = 80;
    /** 预演固定预算：和真实链路同量级（6000 * 0.25） */
    private static final int PREVIEW_BUDGET = 1500;
    private static final Set<String> POSITIONS = Set.of("before_char", "after_char", "in_chat");

    private final WorldInfoMapper worldInfoMapper;
    private final CharacterMapper characterMapper;
    private final WorldInfoService worldInfoService;

    /** 管理页首屏：角色条目（不过滤 enabled）+ 我的全局条目 */
    public Map<String, Object> list(Long userId, Long characterId) {
        requireCharacterOwner(userId, characterId);
        List<WorldInfoEntry> entries = worldInfoMapper.listByCharacter(characterId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entries", entries);
        out.put("global", worldInfoMapper.listGlobalByUser(userId));
        out.put("maxEntries", MAX_ENTRIES_PER_CHARACTER);
        return out;
    }

    @Transactional
    public WorldInfoEntry create(Long userId, Long characterId, WorldInfoRequest req) {
        requireCharacterOwner(userId, characterId);
        if (worldInfoMapper.listByCharacter(characterId).size() >= MAX_ENTRIES_PER_CHARACTER) {
            throw new BusinessException("这个角色的世界书条目已达上限 " + MAX_ENTRIES_PER_CHARACTER + " 条");
        }
        WorldInfoEntry entry = new WorldInfoEntry();
        entry.setCharacterId(characterId);
        entry.setUserId(userId);
        normalize(entry, req);
        worldInfoMapper.insert(entry);
        return worldInfoMapper.findById(entry.getId());
    }

    /** 全局条目：character_id 为空，只对创建者自己的对话生效 */
    @Transactional
    public WorldInfoEntry createGlobal(Long userId, WorldInfoRequest req) {
        if (worldInfoMapper.listGlobalByUser(userId).size() >= MAX_ENTRIES_PER_CHARACTER) {
            throw new BusinessException("全局条目已达上限 " + MAX_ENTRIES_PER_CHARACTER + " 条");
        }
        WorldInfoEntry entry = new WorldInfoEntry();
        entry.setCharacterId(null);
        entry.setUserId(userId);
        normalize(entry, req);
        worldInfoMapper.insert(entry);
        return worldInfoMapper.findById(entry.getId());
    }

    @Transactional
    public WorldInfoEntry update(Long userId, Long id, WorldInfoRequest req) {
        WorldInfoEntry entry = requireEditable(userId, id);
        normalize(entry, req);
        worldInfoMapper.update(entry);
        return worldInfoMapper.findById(id);
    }

    @Transactional
    public void toggle(Long userId, Long id, boolean enabled) {
        requireEditable(userId, id);
        worldInfoMapper.updateEnabled(id, enabled ? 1 : 0);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireEditable(userId, id);
        worldInfoMapper.delete(id);
    }

    /**
     * 按角色导出条目为 JSON 友好结构（前端下载成文件）。
     *
     * <p>不含 id：导入时一律新建，避免把 A 库的 id 带进 B 库造成混乱。
     * 字段名与 {@link WorldInfoRequest} 对齐，导入侧可以直接复用同一套兜底逻辑。
     */
    public Map<String, Object> export(Long userId, Long characterId) {
        CharacterCard card = requireCharacterOwner(userId, characterId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (WorldInfoEntry e : worldInfoMapper.listByCharacter(characterId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", e.getTitle());
            m.put("keys", e.getKeys());
            m.put("content", e.getContent());
            m.put("position", e.getPosition());
            m.put("depth", e.getDepth());
            m.put("orderNo", e.getOrderNo());
            m.put("probability", e.getProbability());
            m.put("constant", e.isConstant());
            m.put("enabled", e.isEnabled());
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "ai-tavern-worldbook");
        out.put("version", 1);
        out.put("character", Map.of("id", characterId, "name", nvl(card.getName(), "")));
        out.put("entries", items);
        return out;
    }

    /**
     * 导入条目：逐条走 {@link #normalize} 兜底，脏条目跳过而不是让整批失败。
     *
     * <p>角色条目上限照常生效——导入到 80 条为止，多出来的算 skipped。
     */
    @Transactional
    public Map<String, Object> importEntries(Long userId, Long characterId,
                                             List<Map<String, Object>> items) {
        requireCharacterOwner(userId, characterId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException("文件里没有可导入的条目");
        }
        int remaining = Math.max(MAX_ENTRIES_PER_CHARACTER - worldInfoMapper.listByCharacter(characterId).size(), 0);
        int imported = 0;
        int skipped = 0;
        for (Map<String, Object> raw : items) {
            WorldInfoRequest req = toRequest(raw);
            if (req == null) {
                skipped++;
                continue;
            }
            if (imported >= remaining) {
                skipped++;
                continue;
            }
            WorldInfoEntry entry = new WorldInfoEntry();
            entry.setCharacterId(characterId);
            entry.setUserId(userId);
            try {
                normalize(entry, req);
            } catch (BusinessException e) {
                // 缺触发词之类的脏条目跳过，别让一条垃圾拖死整批
                skipped++;
                continue;
            }
            worldInfoMapper.insert(entry);
            imported++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", imported);
        out.put("skipped", skipped);
        return out;
    }

    /** 拖拽排序：ids 按界面上的新顺序给出，order_no 重新按步长 10 编 */
    @Transactional
    public void reorder(Long userId, Long characterId, List<Long> ids) {
        requireCharacterOwner(userId, characterId);
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("没有要排序的条目");
        }
        Set<Long> own = worldInfoMapper.listByCharacter(characterId).stream()
                .map(WorldInfoEntry::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!own.containsAll(ids)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        int order = 0;
        for (Long id : ids) {
            worldInfoMapper.updateOrderNo(id, order);
            order += 10;
        }
    }

    /** 导入文件里的单条 → 请求体；没有正文的条目返回 null（视为脏数据跳过） */
    private WorldInfoRequest toRequest(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String content = strOf(raw.get("content"));
        if (content == null || content.isBlank()) {
            return null;
        }
        WorldInfoRequest req = new WorldInfoRequest();
        req.setTitle(strOf(raw.get("title")));
        req.setKeys(strOf(raw.get("keys")));
        req.setContent(content);
        req.setPosition(strOf(raw.get("position")));
        req.setDepth(intOf(raw.get("depth")));
        req.setOrderNo(intOf(raw.get("orderNo")));
        req.setProbability(intOf(raw.get("probability")));
        // constant / enabled 兼容布尔与 1/0 两种写法，导出文件手改过也能吃进来
        req.setConstant(boolOf(raw.get("constant"), false)
                || boolOf(raw.get("constantOn") != null ? raw.get("constantOn") : raw.get("constant_on"), false));
        req.setEnabled(boolOf(raw.get("enabled"), true));
        return req;
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer intOf(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(String.valueOf(v).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean boolOf(Object v, boolean fallback) {
        if (v == null) {
            return fallback;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
            return false;
        }
        return fallback;
    }

    private static String nvl(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    /**
     * 命中预演：拿一段文本跑一遍激活逻辑，返回命中了哪几条、各占多少 token。
     *
     * <p>不写库、不调模型，所以管理页可以随便点——这也是最容易演示的一点：
     * 改一个关键词，命中列表立刻变。
     */
    public Map<String, Object> preview(Long userId, Long characterId, String text, Integer budget) {
        requireCharacterOwner(userId, characterId);
        String sample = text == null ? "" : text;
        int limit = budget == null || budget <= 0 ? PREVIEW_BUDGET : Math.min(budget, 8000);

        List<WorldInfoEntry> candidates = worldInfoMapper.findActiveForCharacter(characterId, userId);
        List<WorldInfoEntry> activated = worldInfoService.activate(candidates, sample, limit);

        List<Map<String, Object>> hits = new ArrayList<>();
        int used = 0;
        for (WorldInfoEntry e : activated) {
            int cost = TokenEstimator.estimate(e.getContent());
            used += cost;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            m.put("position", e.getPosition());
            m.put("depth", e.getDepth());
            m.put("orderNo", e.getOrderNo());
            m.put("constant", e.isConstant());
            m.put("keys", e.getKeys());
            m.put("content", e.getContent());
            m.put("tokens", cost);
            hits.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hits", hits);
        out.put("hitCount", hits.size());
        out.put("candidateCount", candidates.size());
        out.put("missedCount", Math.max(candidates.size() - hits.size(), 0));
        out.put("usedTokens", used);
        out.put("budgetTokens", limit);
        out.put("overBudget", used > limit);
        out.put("scanChars", sample.length());
        return out;
    }

    /** 字段兜底：前端漏填或填脏了，也不该让对话链路炸掉 */
    private void normalize(WorldInfoEntry entry, WorldInfoRequest req) {
        entry.setTitle(req.getTitle() == null ? null : req.getTitle().trim());
        entry.setKeys(req.getKeys() == null ? null : req.getKeys().trim());
        entry.setContent(req.getContent());

        String pos = req.getPosition();
        entry.setPosition(pos != null && POSITIONS.contains(pos) ? pos : "after_char");
        entry.setDepth(clamp(req.getDepth(), 0, 200, 4));
        entry.setOrderNo(clamp(req.getOrderNo(), 0, 10000, 100));
        entry.setProbability(clamp(req.getProbability(), 0, 100, 100));
        entry.setConstantOn(Boolean.TRUE.equals(req.getConstant()) ? 1 : 0);
        entry.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);

        // 常量条目不需要关键词，但也不能是空内容
        if (entry.isConstant()) {
            entry.setKeys(null);
        } else if (entry.getKeys() == null || entry.getKeys().isBlank()) {
            throw new BusinessException("非常量条目必须填写触发词");
        }
    }

    private CharacterCard requireCharacterOwner(Long userId, Long characterId) {
        CharacterCard card = characterMapper.findById(characterId);
        if (card == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        if (userId == null || !userId.equals(card.getCreatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return card;
    }

    private WorldInfoEntry requireEditable(Long userId, Long id) {
        WorldInfoEntry entry = worldInfoMapper.findById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (entry.getCharacterId() != null) {
            requireCharacterOwner(userId, entry.getCharacterId());
        } else if (entry.getUserId() == null || !entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return entry;
    }

    private static Integer clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
