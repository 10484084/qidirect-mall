package com.qidirect.mall.infra.identity;

/**
 * 身份核验网关：核验姓名与身份证号是否一致（公安二要素），不涉及人脸。
 */
public interface IdentityVerifyGateway {

    /**
     * @return true 表示一致；false 表示不一致或查无此人
     */
    boolean verify(String realName, String idCard);
}
