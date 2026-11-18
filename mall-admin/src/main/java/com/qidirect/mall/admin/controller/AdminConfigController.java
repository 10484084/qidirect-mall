package com.qidirect.mall.admin.controller;

import cn.hutool.json.JSONUtil;
import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.ConfigSaveRequest;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.SysConfig;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.SysConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台系统配置。修改配置时写操作日志留痕。
 */
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigService sysConfigService;
    private final OperationLogMapper operationLogMapper;

    @GetMapping
    public ApiResponse<List<SysConfig>> list() {
        return ApiResponse.ok(sysConfigService.list());
    }

    @PutMapping("/{key}")
    public ApiResponse<Void> update(@PathVariable String key,
                                    @Valid @RequestBody ConfigSaveRequest request) {
        sysConfigService.set(key, request.value(), request.remark());
        Long adminId = SecurityUtils.currentUserId();
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("CONFIG");
        log.setAction("CONFIG_UPDATE");
        log.setTarget(key);
        log.setDetail(JSONUtil.createObj()
                .set("key", key)
                .set("value", request.value())
                .set("remark", request.remark())
                .toString());
        log.setIp("");
        operationLogMapper.insert(log);
        return ApiResponse.ok();
    }
}
