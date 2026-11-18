package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.vo.OperationLogVO;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台操作日志查询。
 */
@Service
@RequiredArgsConstructor
public class AdminOperationLogService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final OperationLogMapper operationLogMapper;

    public PageResult<OperationLogVO> page(String module, String action, String operator,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<OperationLog> result = operationLogMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<OperationLog>lambdaQuery()
                        .eq(StrUtil.isNotBlank(module), OperationLog::getModule, StrUtil.trim(module))
                        .eq(StrUtil.isNotBlank(action), OperationLog::getAction, StrUtil.trim(action))
                        .like(StrUtil.isNotBlank(operator), OperationLog::getOperator, StrUtil.trim(operator))
                        .ge(startTime != null, OperationLog::getCreatedAt, startTime)
                        .le(endTime != null, OperationLog::getCreatedAt, endTime)
                        .orderByDesc(OperationLog::getId));
        List<OperationLogVO> records = result.getRecords().stream()
                .map(log -> new OperationLogVO(log.getId(), log.getOperator(), log.getModule(),
                        log.getAction(), log.getTarget(), log.getDetail(), log.getIp(), log.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }
}
