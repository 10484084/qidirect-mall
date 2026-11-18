package com.qidirect.mall.infra.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 敏感数据加解密与重复比对摘要。
 * AES/CBC/PKCS5Padding，每次加密使用随机 IV 并将 IV 前置拼接到密文中。
 */
@Component
public class DataCipher {

    private static final int IV_LENGTH = 16;

    private final byte[] key;
    private final String salt;

    public DataCipher(@Value("${security.aes-key}") String aesKey,
                      @Value("${security.hash-salt}") String salt) {
        byte[] bytes = aesKey == null ? new byte[0] : aesKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            throw new IllegalStateException("security.aes-key 长度必须为 16/24/32 字节");
        }
        this.key = bytes;
        this.salt = salt == null ? "" : salt;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        byte[] iv = RandomUtil.randomBytes(IV_LENGTH);
        byte[] encrypted = aes(iv).encrypt(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[IV_LENGTH + encrypted.length];
        System.arraycopy(iv, 0, out, 0, IV_LENGTH);
        System.arraycopy(encrypted, 0, out, IV_LENGTH, encrypted.length);
        return Base64.encode(out);
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }
        byte[] all = Base64.decode(cipherText);
        if (all.length <= IV_LENGTH) {
            return "";
        }
        byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH);
        byte[] data = Arrays.copyOfRange(all, IV_LENGTH, all.length);
        return new String(aes(iv).decrypt(data), StandardCharsets.UTF_8);
    }

    /**
     * 身份证号、营业执照号等重复比对摘要，先做去空格与大写归一化。
     */
    public String hash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return DigestUtil.sha256Hex(value.trim().toUpperCase() + salt);
    }

    private AES aes(byte[] iv) {
        return new AES(Mode.CBC, Padding.PKCS5Padding, key, iv);
    }
}
