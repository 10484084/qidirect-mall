package com.qidirect.mall.domain.vo;

/**
 * 邀请二维码。qrCode 为 data:image/png;base64,... 可直接用于 img src。
 */
public record InviteQrVO(
        Long storeId,
        String inviteCode,
        String inviteUrl,
        boolean joined,
        String qrCode) {
}
