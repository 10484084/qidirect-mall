package com.qidirect.mall.domain.dto.store;

import jakarta.validation.constraints.Size;

/**
 * 门店资料修改（昵称、头像）。
 */
public record StoreProfileUpdateRequest(
        @Size(max = 64, message = "昵称长度不能超过 64 位")
        String nickname,

        @Size(max = 255, message = "头像地址过长")
        String avatar) {
}
