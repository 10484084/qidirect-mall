package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.dto.admin.SmsTemplateSaveRequest;
import com.qidirect.mall.domain.entity.OperationLog;
import com.qidirect.mall.domain.entity.SmsTemplate;
import com.qidirect.mall.domain.vo.SmsTemplateVO;
import com.qidirect.mall.infra.mapper.OperationLogMapper;
import com.qidirect.mall.infra.mapper.SmsTemplateMapper;
import com.qidirect.mall.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台短信模板管理。code 唯一。
 */
@Service
@RequiredArgsConstructor
public class AdminSmsTemplateService {

    private final SmsTemplateMapper smsTemplateMapper;
    private final OperationLogMapper operationLogMapper;

    public List<SmsTemplateVO> list() {
        return smsTemplateMapper.selectList(Wrappers.<SmsTemplate>lambdaQuery()
                        .orderByDesc(SmsTemplate::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public void create(SmsTemplateSaveRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        SmsTemplate template = new SmsTemplate();
        apply(template, request);
        try {
            smsTemplateMapper.insert(template);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "模板编码已存在");
        }
        logOperation(adminId, "SMS_CREATE", template.getId(),
                JSONUtil.createObj()
                        .set("code", template.getCode())
                        .set("status", template.getStatus())
                        .toString());
    }

    @Transactional
    public void update(Long id, SmsTemplateSaveRequest request) {
        SmsTemplate template = smsTemplateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "短信模板不存在");
        }
        Long adminId = SecurityUtils.currentUserId();
        apply(template, request);
        try {
            smsTemplateMapper.updateById(template);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "模板编码已存在");
        }
        logOperation(adminId, "SMS_UPDATE", id,
                JSONUtil.createObj()
                        .set("code", template.getCode())
                        .set("status", template.getStatus())
                        .toString());
    }

    private void apply(SmsTemplate template, SmsTemplateSaveRequest request) {
        if (request.status() != null && request.status() != 0 && request.status() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "模板状态不正确");
        }
        template.setCode(request.code().trim());
        template.setProviderId(StrUtil.nullToEmpty(request.providerId()));
        template.setContent(request.content());
        template.setStatus(request.status() == null ? 1 : request.status());
    }

    private SmsTemplateVO toVO(SmsTemplate template) {
        return new SmsTemplateVO(template.getId(), template.getCode(), template.getProviderId(),
                template.getContent(), template.getStatus(), template.getCreatedAt());
    }

    private void logOperation(Long adminId, String action, Long templateId, String detail) {
        OperationLog log = new OperationLog();
        log.setOperator("admin#" + adminId);
        log.setModule("SMS");
        log.setAction(action);
        log.setTarget(String.valueOf(templateId));
        log.setDetail(detail);
        log.setIp("");
        operationLogMapper.insert(log);
    }
}
