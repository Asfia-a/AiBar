package com.tavern.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.common.PageResult;
import com.tavern.dto.WorldTreeRequest;
import com.tavern.entity.WorldTree;
import com.tavern.mapper.WorldTreeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldTreeService {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 2;
    public static final int STATUS_OFFLINE = 3;

    private final WorldTreeMapper worldTreeMapper;
    private final ObjectMapper objectMapper;

    public PageResult<WorldTree> list(Integer status, Long creatorId, String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        return PageResult.of(worldTreeMapper.list(status, creatorId, keyword));
    }

    public List<WorldTree> hot(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        return worldTreeMapper.hot(n);
    }

    public WorldTree detail(Long id, Long viewerId) {
        WorldTree w = worldTreeMapper.findById(id);
        if (w == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "世界树不存在");
        }
        boolean owner = viewerId != null && viewerId.equals(w.getCreatorId());
        if (!owner && (w.getStatus() == null || w.getStatus() != STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "世界树不存在或未发布");
        }
        w.setOwnedByMe(owner);
        w.setTree(parseTree(w.getTreeJson()));
        // 列表字段不必再带原始 JSON 出去
        w.setTreeJson(null);
        if (!owner) {
            worldTreeMapper.incrViewCount(id);
            w.setViewCount((w.getViewCount() == null ? 0 : w.getViewCount()) + 1);
        }
        return w;
    }

    public WorldTree create(Long userId, WorldTreeRequest req, boolean publish) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String json = toTreeJson(req.getTree());
        WorldTree w = new WorldTree();
        w.setName(req.getName().trim());
        w.setCover(blankToNull(req.getCover()));
        w.setDescription(trimTo(req.getDescription(), 500));
        w.setTreeJson(json);
        w.setNodeCount(countNodes(parseTree(json)));
        w.setCreatorId(userId);
        w.setStatus(publish ? STATUS_PUBLISHED : STATUS_DRAFT);
        worldTreeMapper.insert(w);
        return detail(w.getId(), userId);
    }

    public WorldTree update(Long userId, Long id, WorldTreeRequest req) {
        WorldTree existing = requireOwner(id, userId);
        existing.setName(req.getName().trim());
        existing.setCover(blankToNull(req.getCover()));
        existing.setDescription(trimTo(req.getDescription(), 500));
        String json = toTreeJson(req.getTree());
        existing.setTreeJson(json);
        existing.setNodeCount(countNodes(parseTree(json)));
        existing.setCreatorId(userId);
        worldTreeMapper.update(existing);
        return detail(id, userId);
    }

    public void publish(Long userId, Long id) {
        requireOwner(id, userId);
        worldTreeMapper.updateStatus(id, userId, STATUS_PUBLISHED);
    }

    public void offline(Long userId, Long id) {
        requireOwner(id, userId);
        worldTreeMapper.updateStatus(id, userId, STATUS_OFFLINE);
    }

    public void delete(Long userId, Long id) {
        requireOwner(id, userId);
        worldTreeMapper.delete(id, userId);
    }

    private WorldTree requireOwner(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        WorldTree w = worldTreeMapper.findById(id);
        if (w == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "世界树不存在");
        }
        if (!userId.equals(w.getCreatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return w;
    }

    private String toTreeJson(Object tree) {
        try {
            if (tree == null) {
                throw new BusinessException("世界树内容不能为空");
            }
            JsonNode node = objectMapper.valueToTree(tree);
            if (!node.isArray() || node.isEmpty()) {
                throw new BusinessException("世界树至少需要一个根节点");
            }
            return objectMapper.writeValueAsString(node);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("世界树序列化失败: {}", e.getMessage());
            throw new BusinessException("世界树内容格式无效");
        }
    }

    private Object parseTree(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("世界树反序列化失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private int countNodes(Object tree) {
        if (!(tree instanceof List<?> list)) {
            return 0;
        }
        int n = 0;
        for (Object item : list) {
            n += 1;
            if (item instanceof Map<?, ?> map) {
                Object children = map.get("children");
                if (children instanceof List<?>) {
                    n += countNodes(children);
                }
            }
        }
        return n;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String trimTo(String s, int max) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
