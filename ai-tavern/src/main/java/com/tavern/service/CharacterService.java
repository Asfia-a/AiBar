package com.tavern.service;

import com.github.pagehelper.PageHelper;
import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.common.PageResult;
import com.tavern.common.RedisConstant;
import com.tavern.common.TargetType;
import com.tavern.dto.CharacterRequest;
import com.tavern.entity.Category;
import com.tavern.entity.CharacterCard;
import com.tavern.entity.Comment;
import com.tavern.entity.Favorite;
import com.tavern.entity.Notification;
import com.tavern.entity.Rating;
import com.tavern.entity.Tag;
import com.tavern.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private static final int TARGET_CHARACTER = TargetType.CHARACTER;

    private final CharacterMapper characterMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final FavoriteMapper favoriteMapper;
    private final RatingMapper ratingMapper;
    private final CommentMapper commentMapper;
    private final NotificationMapper notificationMapper;
    private final CacheService cacheService;

    public PageResult<CharacterCard> list(Integer status, Long categoryId, Long tagId, String keyword,
                                          Long creatorId, int page, int size) {
        PageHelper.startPage(page, size);
        List<CharacterCard> list = characterMapper.list(status, categoryId, tagId, keyword, creatorId);
        fillTags(list);
        return PageResult.of(list);
    }

    public CharacterCard detail(Long id, Long userId) {
        CharacterCard card = characterMapper.findById(id);
        if (card == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        card.setTags(tagMapper.findByCharacterId(id));
        if (userId != null) {
            card.setFavorited(favoriteMapper.find(userId, id) != null);
            Rating rating = ratingMapper.find(userId, id);
            card.setMyScore(rating == null ? null : rating.getScore());
            card.setOwnedByMe(userId.equals(card.getCreatorId()));
        } else {
            card.setOwnedByMe(false);
        }
        return card;
    }

    /**
     * 热门角色列表缓存，TTL 短一些，兼顾性能和新鲜度。
     * 每次调用都会往埋点流水里记一条，管理端的命中率对比图读的就是它。
     */
    public List<CharacterCard> hot(int limit) {
        return cacheService.getList(RedisConstant.HOT_CHARACTERS, "hot-characters",
                CharacterCard.class, () -> {
                    List<CharacterCard> list = characterMapper.hot(limit);
                    fillTags(list);
                    return list;
                });
    }

    public List<CharacterCard> newest(int limit) {
        List<CharacterCard> list = characterMapper.newest(limit);
        fillTags(list);
        return list;
    }

    public PageResult<CharacterCard> search(String keyword, int page, int size) {
        PageHelper.startPage(page, size);
        List<CharacterCard> list = characterMapper.search(keyword);
        fillTags(list);
        return PageResult.of(list);
    }

    @Transactional
    public CharacterCard create(Long userId, CharacterRequest req) {
        CharacterCard card = fromRequest(req);
        card.setCreatorId(userId);
        card.setStatus(0);
        card.setVersion(1);
        if (card.getTemperature() == null) card.setTemperature(new BigDecimal("0.70"));
        if (card.getTopP() == null) card.setTopP(new BigDecimal("0.90"));
        if (card.getMaxTokens() == null) card.setMaxTokens(500);
        characterMapper.insert(card);
        bindTags(card.getId(), resolveTagIds(req.getTagIds(), req.getTagNames()));
        evictCharacterCaches();
        return detail(card.getId(), userId);
    }

    @Transactional
    public CharacterCard update(Long userId, Long id, CharacterRequest req) {
        CharacterCard existing = characterMapper.findById(id);
        if (existing == null || !existing.getCreatorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        CharacterCard card = fromRequest(req);
        card.setId(id);
        card.setCreatorId(userId);
        // 仅创作者可改；不改 status，已有会话使用 prompt_snapshot，不受影响
        characterMapper.update(card);
        tagMapper.clearBindings(id);
        bindTags(id, resolveTagIds(req.getTagIds(), req.getTagNames()));
        evictCharacterCaches();
        return detail(id, userId);
    }

    public void publish(Long userId, Long id) {
        CharacterCard card = characterMapper.findById(id);
        if (card == null || !card.getCreatorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        characterMapper.updateStatus(id, 1);
        evictCharacterCaches();
        notifyUser(userId, "audit", "角色已提交审核", "角色「" + card.getName() + "」已进入审核队列");
    }

    /** 创作者删除角色卡；已有会话保留（人设快照仍在），仅移除角色与关联数据 */
    @Transactional
    public void delete(Long userId, Long id) {
        CharacterCard card = characterMapper.findById(id);
        if (card == null || !card.getCreatorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        tagMapper.clearBindings(id);
        favoriteMapper.deleteByCharacter(id);
        ratingMapper.deleteByCharacter(id);
        commentMapper.deleteByCharacter(id);
        int n = characterMapper.deleteByCreator(id, userId);
        if (n == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        evictCharacterCaches();
    }

    @Transactional
    public CharacterCard replica(Long userId, Long id) {
        CharacterCard src = characterMapper.findById(id);
        if (src == null || src.getStatus() != 2) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        CharacterCard copy = new CharacterCard();
        copy.setName(src.getName() + "（复刻）");
        copy.setAvatar(src.getAvatar());
        copy.setDescription(src.getDescription());
        copy.setPersonality(src.getPersonality());
        copy.setBackgroundStory(src.getBackgroundStory());
        copy.setExampleDialogue(src.getExampleDialogue());
        copy.setGreeting(src.getGreeting());
        copy.setCreatorId(userId);
        copy.setCategoryId(src.getCategoryId());
        copy.setStatus(0);
        copy.setSourceId(src.getId());
        copy.setVersion(1);
        copy.setTemperature(src.getTemperature());
        copy.setTopP(src.getTopP());
        copy.setMaxTokens(src.getMaxTokens());
        characterMapper.insert(copy);
        List<Tag> tags = tagMapper.findByCharacterId(id);
        for (Tag t : tags) {
            tagMapper.bind(copy.getId(), t.getId());
        }
        return detail(copy.getId(), userId);
    }

    @Transactional
    public Map<String, Object> toggleFavorite(Long userId, Long characterId) {
        Favorite fav = favoriteMapper.find(userId, characterId);
        boolean favorited;
        if (fav == null) {
            favoriteMapper.insert(userId, characterId);
            characterMapper.updateFavoriteCount(characterId, 1);
            favorited = true;
        } else {
            favoriteMapper.delete(userId, characterId);
            characterMapper.updateFavoriteCount(characterId, -1);
            favorited = false;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("favorited", favorited);
        return map;
    }

    public List<CharacterCard> favorites(Long userId) {
        List<CharacterCard> list = favoriteMapper.listCharacters(userId);
        fillTags(list);
        return list;
    }

    @Transactional
    public Map<String, Object> rate(Long userId, Long characterId, int score) {
        if (score < 1 || score > 5) {
            throw new BusinessException("评分需在 1-5 之间");
        }
        ratingMapper.upsert(userId, characterId, score);
        Double avg = ratingMapper.avgScore(characterId);
        int count = ratingMapper.countByCharacter(characterId);
        characterMapper.updateRating(characterId, avg == null ? 0 : avg, count);
        Map<String, Object> map = new HashMap<>();
        map.put("rating", BigDecimal.valueOf(avg == null ? 0 : avg).setScale(2, RoundingMode.HALF_UP));
        map.put("ratingCount", count);
        return map;
    }

    public PageResult<Comment> comments(Long characterId, int page, int size) {
        PageHelper.startPage(page, size);
        return PageResult.of(commentMapper.listByTarget(TARGET_CHARACTER, characterId));
    }

    public Comment addComment(Long userId, Long characterId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("评论不能为空");
        }
        Comment c = new Comment();
        c.setUserId(userId);
        c.setTargetType(TARGET_CHARACTER);
        c.setTargetId(characterId);
        c.setCharacterId(characterId);
        c.setContent(content.trim());
        commentMapper.insert(c);
        return c;
    }

    public void audit(Long characterId, int status, String reason) {
        CharacterCard card = characterMapper.findById(characterId);
        if (card == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        // status: 2通过 4驳回 3下架
        characterMapper.updateStatus(characterId, status);
        String title = switch (status) {
            case 2 -> "角色审核通过";
            case 4 -> "角色审核驳回";
            case 3 -> "角色已下架";
            default -> "角色状态变更";
        };
        String content = "角色「" + card.getName() + "」" + title + (reason == null ? "" : "：" + reason);
        notifyUser(card.getCreatorId(), "audit", title, content);
    }

    public PageResult<CharacterCard> auditList(int page, int size, Integer status) {
        int st = status == null ? 1 : status;
        PageHelper.startPage(page, size);
        List<CharacterCard> list = characterMapper.listByStatus(st);
        fillTags(list);
        return PageResult.of(list);
    }

    /** 管理端查看任意状态角色卡详情 */
    public CharacterCard adminDetail(Long id) {
        CharacterCard card = characterMapper.findById(id);
        if (card == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }
        card.setTags(tagMapper.findByCharacterId(id));
        return card;
    }

    private void notifyUser(Long userId, String type, String title, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        notificationMapper.insert(n);
    }

    private void bindTags(Long characterId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        for (Long tagId : tagIds) {
            if (tagId == null) continue;
            tagMapper.bind(characterId, tagId);
            tagMapper.incrUsage(tagId);
        }
    }

    /** 合并 tagIds + 按名称查/建标签 */
    public List<Long> resolveTagIds(List<Long> tagIds, List<String> tagNames) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (tagIds != null) {
            for (Long id : tagIds) {
                if (id != null) ids.add(id);
            }
        }
        if (tagNames != null) {
            String[] palette = {"#E6A23C", "#F56C6C", "#409EFF", "#67C23A", "#909399", "#FF6B35", "#B37FEB"};
            for (String raw : tagNames) {
                if (raw == null) continue;
                String name = raw.trim();
                if (name.isEmpty()) continue;
                if (name.length() > 30) name = name.substring(0, 30);
                Tag tag = tagMapper.findByName(name);
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(name);
                    tag.setColor(palette[Math.floorMod(name.hashCode(), palette.length)]);
                    tagMapper.insert(tag);
                }
                if (tag.getId() != null) ids.add(tag.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    public Long resolveCategoryId(Long categoryId, String categoryName) {
        if (categoryId != null) return categoryId;
        if (categoryName == null || categoryName.isBlank()) return null;
        String name = categoryName.trim();
        Category cat = categoryMapper.findByName(name);
        if (cat != null) return cat.getId();
        List<Category> all = categoryMapper.listAll();
        for (Category c : all) {
            if (c.getName() != null && (name.contains(c.getName()) || c.getName().contains(name))) {
                return c.getId();
            }
        }
        // 常见别名 → 线上分类
        Map<String, String> aliases = Map.of(
                "原创", "游戏动漫",
                "动漫", "游戏动漫",
                "游戏", "游戏动漫",
                "小说", "西幻异世界",
                "历史", "历史名人",
                "科幻", "科幻未来",
                "都市", "现代都市",
                "古风", "古风仙侠"
        );
        String mapped = aliases.get(name);
        if (mapped != null) {
            Category m = categoryMapper.findByName(mapped);
            if (m != null) return m.getId();
        }
        return null;
    }

    private void fillTags(List<CharacterCard> list) {
        for (CharacterCard c : list) {
            c.setTags(tagMapper.findByCharacterId(c.getId()));
        }
    }

    private CharacterCard fromRequest(CharacterRequest req) {
        CharacterCard card = new CharacterCard();
        card.setName(req.getName());
        card.setAvatar(req.getAvatar());
        card.setDescription(req.getDescription());
        card.setPersonality(req.getPersonality());
        card.setBackgroundStory(req.getBackgroundStory());
        card.setExampleDialogue(req.getExampleDialogue());
        card.setGreeting(req.getGreeting());
        card.setCategoryId(resolveCategoryId(req.getCategoryId(), req.getCategoryName()));
        card.setTemperature(req.getTemperature());
        card.setTopP(req.getTopP());
        card.setMaxTokens(req.getMaxTokens());
        return card;
    }

    public Object categories() {
        return categoryMapper.listAll();
    }

    /**
     * 标签几乎不变但每次首页都要读，缓存起来。
     * 角色增删改时会调 {@link #evictTagCache()}，所以不用担心读到过期标签。
     */
    public Object tags() {
        return cacheService.getList(RedisConstant.TAG_LIST, "tags", Tag.class, tagMapper::listAll);
    }

    /** 角色写操作后清缓存。热门榜和标签都依赖角色数据 */
    public void evictTagCache() {
        cacheService.evict(RedisConstant.TAG_LIST);
    }

    public void evictHotCache() {
        cacheService.evict(RedisConstant.HOT_CHARACTERS);
    }

    /** 角色数据变动后统一失效，避免各处漏调导致缓存里留着旧数据 */
    private void evictCharacterCaches() {
        evictHotCache();
        evictTagCache();
    }
}
