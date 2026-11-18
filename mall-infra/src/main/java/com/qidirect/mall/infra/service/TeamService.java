package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.entity.StoreLevel;
import com.qidirect.mall.domain.vo.TeamMemberVO;
import com.qidirect.mall.infra.mapper.StoreLevelMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 团队树按层懒加载：只能查看自己或自己子树下的直接下级，path 前缀越权直接拒绝。
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final StoreMapper storeMapper;
    private final StoreLevelMapper storeLevelMapper;
    private final StoreGuard storeGuard;
    private final StorePathCache storePathCache;

    public List<TeamMemberVO> children(Long viewerStoreId, Long parentId) {
        Store viewer = storeGuard.requireStore(viewerStoreId);
        Long targetParentId = parentId == null || parentId == 0 ? viewerStoreId : parentId;
        Store parent = storeGuard.requireStore(targetParentId);
        assertInSubtree(viewer, parent);

        List<Store> children = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                .eq(Store::getParentId, targetParentId)
                .orderByAsc(Store::getId));
        if (children.isEmpty()) {
            return List.of();
        }

        List<Long> childIds = children.stream().map(Store::getId).toList();
        Map<Long, Long> childCounts = storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .select(Store::getParentId)
                        .in(Store::getParentId, childIds))
                .stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(Store::getParentId, Collectors.counting()));
        Map<Long, StoreLevel> levels = levelMap(children.stream()
                .map(Store::getLevelId).filter(Objects::nonNull).distinct().toList());

        return children.stream()
                .map(child -> toVO(child, levels.get(child.getLevelId()),
                        childCounts.getOrDefault(child.getId(), 0L)))
                .toList();
    }

    private void assertInSubtree(Store viewer, Store target) {
        String viewerPath = storePathCache.getPath(viewer);
        String targetPath = StrUtil.nullToEmpty(target.getPath());
        if (StrUtil.isBlank(viewerPath) || !targetPath.startsWith(viewerPath)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "无权访问该团队节点");
        }
    }

    private Map<Long, StoreLevel> levelMap(Collection<Long> levelIds) {
        if (levelIds.isEmpty()) {
            return Map.of();
        }
        return storeLevelMapper.selectBatchIds(levelIds).stream()
                .collect(Collectors.toMap(StoreLevel::getId, level -> level));
    }

    private TeamMemberVO toVO(Store store, StoreLevel level, long childCount) {
        boolean joined = store.getLevelId() != null && store.getLevelId() > 0;
        return new TeamMemberVO(
                store.getId(),
                StrUtil.blankToDefault(store.getName(), StrUtil.nullToEmpty(store.getNickname())),
                maskPhone(store.getPhone()),
                StrUtil.nullToEmpty(store.getAvatar()),
                store.getLevelId() == null ? 0L : store.getLevelId(),
                store.getLevelId() == null || store.getLevelId() == 0
                        ? "未加盟" : (level == null ? "" : level.getName()),
                joined,
                store.getStatus(),
                store.getJoinTime(),
                childCount);
    }

    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return StrUtil.nullToEmpty(phone);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
