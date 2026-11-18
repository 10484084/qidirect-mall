package com.qidirect.mall.infra.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Agreement;
import com.qidirect.mall.domain.entity.StoreAgreement;
import com.qidirect.mall.domain.vo.AgreementVO;
import com.qidirect.mall.infra.mapper.AgreementMapper;
import com.qidirect.mall.infra.mapper.StoreAgreementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 协议正文与签署留痕。建单时只校验勾选，支付成功后写入 store_agreement。
 */
@Service
@RequiredArgsConstructor
public class AgreementService {

    private final AgreementMapper agreementMapper;
    private final StoreAgreementMapper storeAgreementMapper;

    public AgreementVO active() {
        Agreement agreement = requireActive();
        return new AgreementVO(agreement.getId(), agreement.getTitle(),
                agreement.getVersion(), agreement.getContent());
    }

    public Agreement requireActive() {
        Agreement agreement = agreementMapper.selectOne(Wrappers.<Agreement>lambdaQuery()
                .eq(Agreement::getStatus, 1)
                .orderByDesc(Agreement::getId)
                .last("LIMIT 1"));
        if (agreement == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "未配置启用的协议");
        }
        return agreement;
    }

    /**
     * @param signType 1 线上勾选，2 纸质上传
     */
    public void sign(Long storeId, Agreement agreement, int signType, List<String> proofImages, String ip) {
        StoreAgreement record = new StoreAgreement();
        record.setStoreId(storeId);
        record.setAgreementId(agreement.getId());
        record.setVersion(agreement.getVersion());
        record.setSignType(signType);
        if (proofImages != null && !proofImages.isEmpty()) {
            record.setProofImages(JSONUtil.toJsonStr(proofImages));
        }
        record.setIp(ip == null ? "" : ip);
        storeAgreementMapper.insert(record);
    }
}
