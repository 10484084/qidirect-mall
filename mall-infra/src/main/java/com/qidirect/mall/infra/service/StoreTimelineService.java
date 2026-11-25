package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.StoreEvent;
import com.qidirect.mall.domain.vo.StoreEventVO;
import com.qidirect.mall.infra.mapper.StoreEventMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 门店时间线：按加盟 / 预存 / 订货 / 提成 / 业绩 / 提现归类筛选，门店端强制只能看自己。
 */
@Service
@RequiredArgsConstructor
public class StoreTimelineService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final Map<String, List<String>> CATEGORY_TYPES = Map.of(
            "join", List.of("JOIN", "UPGRADE", "LEVEL_CHANGE"),
            "recharge", List.of("RECHARGE", "OFFLINE"),
            "order", List.of("ORDER", "ORDER_PAID", "ORDER_CANCEL", "SHIP", "ORDER_FINISH"),
            "commission", List.of("COMMISSION"),
            "bonus", List.of("BONUS", "BONUS_REVERSAL", "ADJUST"),
            "withdraw", List.of("WITHDRAW_APPLY", "WITHDRAW_AUDIT", "WITHDRAW_PAID", "WITHDRAW_FAIL"));

    private final StoreEventMapper storeEventMapper;
    private final StoreLevelMapper storeLevelMapper;

    public PageResult<StoreEventVO> storePage(Long storeId, String category, long page, long size) {
        LambdaQueryWrapper<StoreEvent> query = Wrappers.<StoreEvent>lambdaQuery()
                .eq(StoreEvent::getStoreId, storeId);
        applyCategory(query, category);
        return page(query, page, size);
    }

    public PageResult<StoreEventVO> adminPage(Long storeId, String category, long page, long size) {
        LambdaQueryWrapper<StoreEvent> query = Wrappers.<StoreEvent>lambdaQuery()
                .eq(StoreEvent::getStoreId, storeId);
        applyCategory(query, category);
        return page(query, page, size);
    }

    private PageResult<StoreEventVO> page(LambdaQueryWrapper<StoreEvent> query, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        query.orderByDesc(StoreEvent::getCreatedAt).orderByDesc(StoreEvent::getId);
        IPage<StoreEvent> result = storeEventMapper.selectPage(new Page<>(current, pageSize), query);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private void applyCategory(LambdaQueryWrapper<StoreEvent> query, String category) {
        if (StrUtil.isBlank(category)) {
            return;
        }
        List<String> eventTypes = CATEGORY_TYPES.get(category.trim().toLowerCase());
        if (eventTypes == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "不支持的时间线类型");
        }
        query.in(StoreEvent::getEventType, eventTypes);
    }

    private StoreEventVO toVO(StoreEvent event) {
        return new StoreEventVO(
                event.getId(),
                event.getStoreId(),
                event.getEventType(),
                categoryOf(event.getEventType()),
                StrUtil.nullToEmpty(event.getBizNo()),
                event.getTitle(),
                event.getAmount(),
                event.getDirection(),
                event.getAccount(),
                formatDetail(event.getDetail()),
                parseImages(event.getProofImages()),
                event.getOperatorType(),
                event.getOperatorId(),
                event.getCreatedAt());
    }

    /**
     * 把事件明细 JSON 转成门店能看懂的中文，避免页面直接暴露 {"levelId":1} 这类原始结构。
     */
    private String formatDetail(String detail) {
        if (StrUtil.isBlank(detail)) {
            return "";
        }
        try {
            JSONObject obj = JSONUtil.parseObj(detail);
            Long levelId = obj.getLong("levelId");
            if (levelId == null) {
                return "";
            }
            String target = levelName(levelId);
            Long beforeLevelId = obj.getLong("beforeLevelId");
            if (beforeLevelId != null && beforeLevelId > 0) {
                return levelName(beforeLevelId) + " → " + target;
            }
            return target == null ? "" : "开通" + target;
        } catch (Exception e) {
            return "";
        }
    }

    private String levelName(Long levelId) {
        if (levelId == null || levelId <= 0) {
            return "";
        }
        var level = storeLevelMapper.selectById(levelId);
        return level == null ? "" : level.getName();
    }

    private String categoryOf(String eventType) {
        if (StrUtil.isBlank(eventType)) {
            return "";
        }
        for (Map.Entry<String, List<String>> entry : CATEGORY_TYPES.entrySet()) {
            if (entry.getValue().contains(eventType)) {
                return entry.getKey();
            }
        }
        return "other";
    }

    private List<String> parseImages(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
