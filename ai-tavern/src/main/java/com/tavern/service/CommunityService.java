package com.tavern.service;

import com.github.pagehelper.PageHelper;
import com.tavern.common.*;
import com.tavern.entity.Comment;
import com.tavern.entity.NarrativeWork;
import com.tavern.mapper.CommentMapper;
import com.tavern.mapper.InteractionMapper;
import com.tavern.mapper.NarrativeWorkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 社区互动：作品浏览、点赞、收藏、评论，以及频控去重。
 *
 * <p>计数走两处：MySQL 是权威数据（用于后台追溯与最终一致），
 * Redis ZSet 只做热榜排序与列表页快速读取。发布/点赞/收藏时同步 {@code ZINCRBY}，
 * 列表页读 ZSet 拿顺序再回表取详情，避免每次 {@code ORDER BY like_count} 全表排序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final NarrativeWorkMapper workMapper;
    private final CommentMapper commentMapper;
    private final InteractionMapper interactionMapper;
    private final RateLimitService rateLimit;
    private final ContentAuditService contentAuditService;
    private final StringRedisTemplate redis;

    /** 列表页单批最多查询的目标数，防止 IN 条件过长 */
    private static final int BATCH_LIMIT = 200;

    // ---------------- 广场浏览 ----------------

    /**
     * 社区广场。
     *
     * @param sort latest 最新发布 / hot 最热（点赞优先）
     */
    public PageResult<NarrativeWork> works(Long userId, String sort, int page, int size) {
        List<NarrativeWork> list;
        if ("hot".equalsIgnoreCase(sort)) {
            // 热榜：先从 ZSet 取前 N 个 id，再按 id 倒序回表，最后按 ZSet 分数重排
            Set<String> top = redis.opsForZSet().reverseRange(RedisConstant.WORK_LIKE_ZSET, 0, (long) page * size - 1);
            List<NarrativeWork> fetched = new ArrayList<>();
            if (top != null && !top.isEmpty()) {
                List<Long> ids = new ArrayList<>(top.size());
                for (String s : top) {
                    try {
                        ids.add(Long.parseLong(s));
                    } catch (NumberFormatException ignored) {
                        // ZSet 里混入了非数字 member，忽略即可
                    }
                }
                if (!ids.isEmpty()) {
                    fetched.addAll(workMapper.findByIds(ids));
                }
            }
            list = sortByZSet(fetched, top);
        } else {
            PageHelper.startPage(page, size);
            list = workMapper.listPublished();
        }
        fillInteraction(userId, list);
        // 热榜走 ZSet 时不能用 PageHelper 的 total，这里直接查总数
        return PageResult.of(list, workMapper.countPublished(), page, size);
    }

    /** 按 ZSet 里的顺序重排，ZSet 中缺失的作品按 id 倒序补在后面 */
    private List<NarrativeWork> sortByZSet(List<NarrativeWork> works, Set<String> order) {
        if (order == null || order.isEmpty()) {
            works.sort((a, b) -> Long.compare(b.getId(), a.getId()));
            return works;
        }
        Map<Long, NarrativeWork> byId = new HashMap<>();
        for (NarrativeWork w : works) {
            byId.put(w.getId(), w);
        }
        List<NarrativeWork> ordered = new ArrayList<>(works.size());
        for (String s : order) {
            try {
                NarrativeWork w = byId.remove(Long.parseLong(s));
                if (w != null) {
                    ordered.add(w);
                }
            } catch (NumberFormatException ignored) {
                // 同上，跳过脏数据
            }
        }
        ordered.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return ordered;
    }

    /** 作品详情。未过审的只有作者本人能看到 */
    public NarrativeWork detail(Long userId, Long id) {
        NarrativeWork work = workMapper.findById(id);
        if (work == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        boolean mine = Objects.equals(work.getUserId(), userId);
        if (!isPublic(work) && !mine) {
            // 不暴露"存在但未过审"这个信息，对外统一按不存在处理
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        fillInteraction(userId, List.of(work));
        return work;
    }

    private boolean isPublic(NarrativeWork work) {
        return work.getStatus() != null && work.getStatus() == 1
                && work.getAuditStatus() != null && work.getAuditStatus() == 1;
    }

    private void fillInteraction(Long userId, List<NarrativeWork> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (NarrativeWork w : list) {
            ids.add(w.getId());
            w.setLikeCount(0);
            w.setCollectCount(0);
            w.setCommentCount(0);
            w.setLiked(false);
            w.setCollected(false);
        }
        if (ids.size() > BATCH_LIMIT) {
            ids = ids.subList(0, BATCH_LIMIT);
        }
        Map<String, Long> likes = toCountMap(interactionMapper.likeCounts(TargetType.NARRATIVE, ids));
        Map<String, Long> collects = toCountMap(interactionMapper.collectCounts(TargetType.NARRATIVE, ids));
        Set<Long> liked = new HashSet<>(interactionMapper.likedIds(userId, TargetType.NARRATIVE, ids));
        Set<Long> collected = new HashSet<>(interactionMapper.collectedIds(userId, TargetType.NARRATIVE, ids));
        for (NarrativeWork w : list) {
            String key = TargetType.NARRATIVE + ":" + w.getId();
            w.setLikeCount(likes.getOrDefault(key, 0L).intValue());
            w.setCollectCount(collects.getOrDefault(key, 0L).intValue());
            w.setCommentCount((int) commentMapper.countByTarget(TargetType.NARRATIVE, w.getId()));
            w.setLiked(liked.contains(w.getId()));
            w.setCollected(collected.contains(w.getId()));
        }
    }

    private Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object k = r.get("target_key");
            Object c = r.get("cnt");
            if (k != null && c instanceof Number num) {
                map.put(String.valueOf(k), num.longValue());
            }
        }
        return map;
    }

    // ---------------- 点赞 / 收藏 ----------------

    /** 点赞。已点赞则取消，返回切换后的状态 */
    public Map<String, Object> toggleLike(Long userId, Long workId) {
        NarrativeWork work = requireWork(workId);
        boolean liked = interactionMapper.likedBy(userId, TargetType.NARRATIVE, workId) > 0;
        if (liked) {
            interactionMapper.unlike(userId, TargetType.NARRATIVE, workId);
            rateLimit.clearDedup(RedisConstant.DEDUP_LIKE_PREFIX, TargetType.NARRATIVE, workId, userId);
            redis.opsForZSet().incrementScore(RedisConstant.WORK_LIKE_ZSET, String.valueOf(workId), -1.0);
        } else {
            if (!rateLimit.allow(RedisConstant.RATE_LIKE_PREFIX, userId)) {
                throw new BusinessException("点赞太频繁了，稍等一下");
            }
            if (!rateLimit.firstTime(RedisConstant.DEDUP_LIKE_PREFIX, TargetType.NARRATIVE, workId, userId)) {
                // Redis 占位说已经点过，以数据库为准再确认一次，防止占位残留导致漏记
                if (interactionMapper.likedBy(userId, TargetType.NARRATIVE, workId) > 0) {
                    return likeState(userId, work);
                }
            }
            interactionMapper.like(userId, TargetType.NARRATIVE, workId);
            redis.opsForZSet().incrementScore(RedisConstant.WORK_LIKE_ZSET, String.valueOf(workId), 1.0);
        }
        return likeState(userId, work);
    }

    private Map<String, Object> likeState(Long userId, NarrativeWork work) {
        Map<String, Object> map = new HashMap<>();
        map.put("liked", interactionMapper.likedBy(userId, TargetType.NARRATIVE, work.getId()) > 0);
        map.put("likeCount", interactionMapper.likeCount(TargetType.NARRATIVE, work.getId()));
        return map;
    }

    /** 收藏。已收藏则取消 */
    public Map<String, Object> toggleCollect(Long userId, Long workId) {
        NarrativeWork work = requireWork(workId);
        boolean collected = interactionMapper.collectedBy(userId, TargetType.NARRATIVE, workId) > 0;
        if (collected) {
            interactionMapper.uncollect(userId, TargetType.NARRATIVE, workId);
            rateLimit.clearDedup(RedisConstant.DEDUP_COLLECT_PREFIX, TargetType.NARRATIVE, workId, userId);
            redis.opsForZSet().incrementScore(RedisConstant.WORK_COLLECT_ZSET, String.valueOf(workId), -1.0);
        } else {
            rateLimit.firstTime(RedisConstant.DEDUP_COLLECT_PREFIX, TargetType.NARRATIVE, workId, userId);
            interactionMapper.collect(userId, TargetType.NARRATIVE, workId);
            redis.opsForZSet().incrementScore(RedisConstant.WORK_COLLECT_ZSET, String.valueOf(workId), 1.0);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("collected", interactionMapper.collectedBy(userId, TargetType.NARRATIVE, workId) > 0);
        map.put("collectCount", interactionMapper.collectCount(TargetType.NARRATIVE, workId));
        return map;
    }

    private NarrativeWork requireWork(Long workId) {
        NarrativeWork work = workMapper.findById(workId);
        if (work == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return work;
    }

    // ---------------- 评论 ----------------

    public PageResult<Comment> comments(Long workId, int page, int size) {
        requireWork(workId);
        PageHelper.startPage(page, size);
        return PageResult.of(commentMapper.listByTarget(TargetType.NARRATIVE, workId));
    }

    /**
     * 发评论。三道关：频控挡住刷屏，敏感词挡住违规，重复内容去重挡住复制粘贴。
     */
    public Comment addComment(Long userId, Long workId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("评论不能为空");
        }
        requireWork(workId);
        if (!rateLimit.allow(RedisConstant.RATE_COMMENT_PREFIX, userId)) {
            long remain = rateLimit.remainSeconds(RedisConstant.RATE_COMMENT_PREFIX, userId);
            throw new BusinessException("评论太频繁了，" + Math.max(remain, 1) + " 秒后再试");
        }
        String text = content.trim();
        if (text.length() > 500) {
            throw new BusinessException("评论最多 500 字");
        }
        List<String> hit = contentAuditService.auditSync(text);
        if (!hit.isEmpty()) {
            throw new BusinessException("评论包含不合适的内容，已拦截");
        }
        if (!rateLimit.firstTime(RedisConstant.DEDUP_COMMENT_PREFIX, TargetType.NARRATIVE, workId, userId)) {
            throw new BusinessException("刚才发过一样的评论了");
        }
        Comment c = new Comment();
        c.setUserId(userId);
        c.setTargetType(TargetType.NARRATIVE);
        c.setTargetId(workId);
        c.setContent(text);
        commentMapper.insert(c);
        return c;
    }

    public void deleteComment(Long userId, Long commentId) {
        if (commentMapper.deleteOwn(commentId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    // ---------------- 后台追溯 ----------------

    /**
     * 某作品的全部互动记录，供管理端追溯刷量行为。
     * 频控只挡住高频，挡不住"隔几分钟点一次"的长周期刷量，所以留痕后才查得到。
     */
    public Map<String, Object> records(Long workId) {
        requireWork(workId);
        Map<String, Object> map = new HashMap<>();
        map.put("likes", interactionMapper.likeRecords(TargetType.NARRATIVE, workId));
        map.put("comments", commentMapper.records(TargetType.NARRATIVE, workId));
        map.put("likeCount", interactionMapper.likeCount(TargetType.NARRATIVE, workId));
        map.put("commentCount", commentMapper.countByTarget(TargetType.NARRATIVE, workId));
        return map;
    }
}
