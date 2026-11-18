package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.RechargeActivitySaveRequest;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.RechargeActivity;
import com.qidirect.mall.domain.entity.RechargeActivityTier;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.RechargeActivityAdminVO;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.mapper.RechargeActivityMapper;
import com.qidirect.mall.infra.mapper.RechargeActivityTierMapper;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台预存活动管理。活动与档位为多对多，保存时整体重建档位关联。
 */
@Service
@RequiredArgsConstructor
public class AdminRechargeActivityService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final RechargeActivityMapper rechargeActivityMapper;
    private final RechargeActivityTierMapper rechargeActivityTierMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final OperationLogMapper operationLogMapper;

    public PageResult<RechargeActivityAdminVO> page(String keyword, Integer status, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String kw = StrUtil.trimToNull(keyword);
        LambdaQueryWrapper<RechargeActivity> query = Wrappers.<RechargeActivity>lambdaQuery()
                .like(kw != null, RechargeActivity::getTitle, kw)
                .eq(status != null, RechargeActivity::getStatus, status)
                .orderByDesc(RechargeActivity::getId);
        IPage<RechargeActivity> result = rechargeActivityMapper.selectPage(new Page<>(current, pageSize), query);
        return PageResult.of(toVOs(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public void create(RechargeActivitySaveRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        List<Long> levelIds = validateLevelIds(request.levelIds());
        RechargeActivity activity = new RechargeActivity();
        apply(activity, request);
        rechargeActivityMapper.insert(activity);
        saveTiers(activity.getId(), levelIds);
        logOperation(adminId, "RECHARGE_ACTIVITY_CREATE", activity.getId(),
                JSONUtil.createObj()
                        .set("title", activity.getTitle())
                        .set("status", activity.getStatus())
                        .set("levelIds", levelIds)
                        .toString());
    }

    @Transactional
    public void update(Long id, RechargeActivitySaveRequest request) {
        RechargeActivity activity = rechargeActivityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "预存活动不存在");
        }
        Long adminId = SecurityUtils.currentUserId();
        List<Long> levelIds = validateLevelIds(request.levelIds());
        apply(activity, request);
        rechargeActivityMapper.updateById(activity);
        rechargeActivityTierMapper.delete(Wrappers.<RechargeActivityTier>lambdaQuery()
                .eq(RechargeActivityTier::getActivityId, id));
        saveTiers(id, levelIds);
        logOperation(adminId, "RECHARGE_ACTIVITY_UPDATE", id,
                JSONUtil.createObj()
                        .set("title", activity.getTitle())
                        .set("status", activity.getStatus())
                        .set("levelIds", levelIds)
                        .toString());
    }

    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "活动状态不正确");
        }
        RechargeActivity activity = rechargeActivityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "预存活动不存在");
        }
        activity.setStatus(status);
        rechargeActivityMapper.updateById(activity);
        logOperation(SecurityUtils.currentUserId(), "RECHARGE_ACTIVITY_STATUS", id,
                JSONUtil.createObj().set("status", status).toString());
    }

    private void apply(RechargeActivity activity, RechargeActivitySaveRequest request) {
        if (request.status() != null && request.status() != 0 && request.status() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "活动状态不正确");
        }
        activity.setTitle(request.title().trim());
        activity.setLimitPerStore(request.limitPerStore() == null ? 0 : Math.max(request.limitPerStore(), 0));
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setStatus(request.status() == null ? 1 : request.status());
    }

    /**
     * 档位去重并校验存在性，避免写入无效关联。
     */
    private List<Long> validateLevelIds(List<Long> levelIds) {
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(levelIds));
        distinct.removeIf(Objects::isNull);
        if (distinct.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "适用档位不能为空");
        }
        List<StoreLevel> levels = storeLevelMapper.selectBatchIds(distinct);
        if (levels.size() != distinct.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在无效的档位");
        }
        return distinct;
    }

    private void saveTiers(Long activityId, List<Long> levelIds) {
        for (Long levelId : levelIds) {
            RechargeActivityTier tier = new RechargeActivityTier();
            tier.setActivityId(activityId);
            tier.setLevelId(levelId);
            rechargeActivityTierMapper.insert(tier);
        }
    }

    private List<RechargeActivityAdminVO> toVOs(List<RechargeActivity> activities) {
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> activityIds = activities.stream().map(RechargeActivity::getId).toList();
        List<RechargeActivityTier> tiers = rechargeActivityTierMapper.selectList(
                Wrappers.<RechargeActivityTier>lambdaQuery()
                        .in(RechargeActivityTier::getActivityId, activityIds)
                        .orderByAsc(RechargeActivityTier::getId));
        Map<Long, List<Long>> tierMap = tiers.stream().collect(Collectors.groupingBy(
                RechargeActivityTier::getActivityId, LinkedHashMap::new,
                Collectors.mapping(RechargeActivityTier::getLevelId, Collectors.toList())));
        List<Long> levelIds = tiers.stream().map(RechargeActivityTier::getLevelId).distinct().toList();
        Map<Long, String> levelNames = levelIds.isEmpty()
                ? Map.of()
                : storeLevelMapper.selectBatchIds(levelIds).stream()
                        .collect(Collectors.toMap(StoreLevel::getId, StoreLevel::getName, (a, b) -> a));
        return activities.stream().map(activity -> {
            List<Long> ids = tierMap.getOrDefault(activity.getId(), List.of());
            List<String> names = ids.stream().map(levelNames::get).filter(Objects::nonNull).toList();
            return new RechargeActivityAdminVO(activity.getId(), activity.getTitle(),
                    activity.getLimitPerStore(), activity.getStartTime(), activity.getEndTime(),
                    activity.getStatus(), ids, names, activity.getCreatedAt());
        }).toList();
    }

    private void logOperation(Long adminId, String action, Long activityId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("RECHARGE_ACTIVITY");
        log.setAction(action);
        log.setTarget(String.valueOf(activityId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}
