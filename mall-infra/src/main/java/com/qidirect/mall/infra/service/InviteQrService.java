package com.qidirect.mall.infra.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.vo.InviteQrVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 生成邀请二维码，内容为带邀请码的注册链接。
 */
@Service
public class InviteQrService {

    private static final int QR_SIZE = 400;

    private final String baseUrl;

    public InviteQrService(@Value("${invite.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public InviteQrVO build(Store store) {
        String inviteUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?")
                + "inviteCode=" + store.getInviteCode();
        QrConfig config = new QrConfig(QR_SIZE, QR_SIZE);
        BufferedImage image = QrCodeUtil.generate(inviteUrl, config);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException("邀请二维码生成失败", e);
        }
        boolean joined = store.getLevelId() != null && store.getLevelId() > 0;
        return new InviteQrVO(store.getId(), store.getInviteCode(), inviteUrl, joined,
                "data:image/png;base64," + Base64.encode(out.toByteArray()));
    }
}
