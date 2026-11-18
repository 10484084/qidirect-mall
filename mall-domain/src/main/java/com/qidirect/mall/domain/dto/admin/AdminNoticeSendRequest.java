package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台发送站内消息。storeIds 为空表示全部门店。
 */
public record AdminNoticeSendRequest(
        @NotBlank(message = "消息标题不能为空")
        String title,

        @NotBlank(message = "消息内容不能为空")
        String content,

        List<Long> storeIds,

        @Size(max = 32, message = "业务类型过长")
        String bizType) {
}
