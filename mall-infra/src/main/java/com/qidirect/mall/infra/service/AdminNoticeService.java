package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.dto.admin.AdminNoticeSendRequest;
import com.qidirect.mall.domain.entity.Notice;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.infra.mapper.NoticeMapper;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台站内消息管理：分页查看与批量发送。
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int TARGET_STORE = 1;

    private final NoticeMapper noticeMapper;
    private final StoreMapper storeMapper;
    private final OperationLogMapper operationLogMapper;

    public PageResult<Notice> page(Integer targetType, Long targetId, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<Notice> result = noticeMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<Notice>lambdaQuery()
                        .eq(targetType != null, Notice::getTargetType, targetType)
                        .eq(targetId != null, Notice::getTargetId, targetId)
                        .orderByDesc(Notice::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * storeIds 为空表示全部门店。标题与内容按 notice 表长度截断。
     */
    @Transactional
    public int send(AdminNoticeSendRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        boolean toAll = request.storeIds() == null || request.storeIds().isEmpty();
        List<Long> storeIds = resolveStoreIds(request.storeIds());
        String title = StrUtil.maxLength(StrUtil.nullToEmpty(request.title()), 60);
        String content = StrUtil.maxLength(StrUtil.nullToEmpty(request.content()), 500);
        String bizType = StrUtil.nullToEmpty(request.bizType());
        int sent = 0;
        for (Long storeId : storeIds) {
            if (storeId == null) {
                continue;
            }
            Notice notice = new Notice();
            notice.setTargetType(TARGET_STORE);
            notice.setTargetId(storeId);
            notice.setTitle(title);
            notice.setContent(content);
            notice.setBizType(bizType);
            notice.setBizNo("");
            notice.setReaded(0);
            noticeMapper.insert(notice);
            sent++;
        }
        logOperation(adminId, "NOTICE_SEND", toAll ? "ALL" : String.valueOf(storeIds.size()),
                JSONUtil.createObj()
                        .set("title", title)
                        .set("bizType", bizType)
                        .set("storeIds", storeIds)
                        .set("count", sent)
                        .toString());
        return sent;
    }

    private List<Long> resolveStoreIds(List<Long> storeIds) {
        if (storeIds != null && !storeIds.isEmpty()) {
            return new ArrayList<>(storeIds.stream().filter(java.util.Objects::nonNull).distinct().toList());
        }
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery().select(Store::getId))
                .stream().map(Store::getId).toList();
    }

    private void logOperation(Long adminId, String action, String target, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("NOTICE");
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}
