package com.qidirect.mall.infra.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.AgreementSaveRequest;
import com.qidirect.mall.domain.entity.Agreement;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.vo.AgreementAdminVO;
import com.qidirect.mall.infra.mapper.AgreementMapper;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台协议管理。version 唯一，启用状态供门店端签署使用。
 */
@Service
@RequiredArgsConstructor
public class AdminAgreementService {

    private final AgreementMapper agreementMapper;
    private final OperationLogMapper operationLogMapper;

    public List<AgreementAdminVO> list() {
        return agreementMapper.selectList(Wrappers.<Agreement>lambdaQuery()
                        .orderByDesc(Agreement::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public void create(AgreementSaveRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        Agreement agreement = new Agreement();
        apply(agreement, request);
        try {
            agreementMapper.insert(agreement);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "协议版本已存在");
        }
        logOperation(adminId, "AGREEMENT_CREATE", agreement.getId(),
                JSONUtil.createObj()
                        .set("title", agreement.getTitle())
                        .set("version", agreement.getVersion())
                        .set("status", agreement.getStatus())
                        .toString());
    }

    @Transactional
    public void update(Long id, AgreementSaveRequest request) {
        Agreement agreement = agreementMapper.selectById(id);
        if (agreement == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "协议不存在");
        }
        Long adminId = SecurityUtils.currentUserId();
        apply(agreement, request);
        try {
            agreementMapper.updateById(agreement);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "协议版本已存在");
        }
        logOperation(adminId, "AGREEMENT_UPDATE", id,
                JSONUtil.createObj()
                        .set("title", agreement.getTitle())
                        .set("version", agreement.getVersion())
                        .set("status", agreement.getStatus())
                        .toString());
    }

    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "协议状态不正确");
        }
        Agreement agreement = agreementMapper.selectById(id);
        if (agreement == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "协议不存在");
        }
        agreement.setStatus(status);
        agreementMapper.updateById(agreement);
        logOperation(SecurityUtils.currentUserId(), "AGREEMENT_STATUS", id,
                JSONUtil.createObj().set("status", status).toString());
    }

    private void apply(Agreement agreement, AgreementSaveRequest request) {
        if (request.status() != null && request.status() != 0 && request.status() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "协议状态不正确");
        }
        agreement.setTitle(request.title().trim());
        agreement.setVersion(request.version().trim());
        agreement.setContent(request.content());
        agreement.setStatus(request.status() == null ? 1 : request.status());
    }

    private AgreementAdminVO toVO(Agreement agreement) {
        return new AgreementAdminVO(agreement.getId(), agreement.getTitle(), agreement.getVersion(),
                agreement.getContent(), agreement.getStatus(), agreement.getCreatedAt());
    }

    private void logOperation(Long adminId, String action, Long agreementId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("AGREEMENT");
        log.setAction(action);
        log.setTarget(String.valueOf(agreementId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}
