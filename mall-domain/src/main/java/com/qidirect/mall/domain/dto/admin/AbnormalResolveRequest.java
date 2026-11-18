package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 异常单处置：REOPEN 补开通 / REFUND 原路退款 / CLOSE 仅关闭。
 */
public record AbnormalResolveRequest(
        @NotBlank(message = "处置方式不能为空")
        String action,
        @Size(max = 255, message = "备注过长")
        String remark,
        List<String> proofImages) {
}
