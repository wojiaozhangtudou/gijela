package com.gijela.morpheus.chat.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM token 加解密工具。
 *
 * <p>密钥来源：构造时传入的 base64 32 字节 key；为空时退化为根据固定盐 SHA-256 派生的本地默认 key（仅 dev）。</p>
 *
 * <p>密文格式：base64( IV(12) || cipher || tag )，整体一段 base64 字符串。</p>
 */
public class TokenCipher {

    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);
    private static final String ALG = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] DEV_SALT = "gijela-chat-mcp-dev-salt".getBytes(StandardCharsets.UTF_8);

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(String base64Key) {
        this.key = resolveKey(base64Key);
    }

    private SecretKey resolveKey(String base64Key) {
        try {
            byte[] raw;
            if (base64Key == null || base64Key.isBlank()) {
                log.warn("[token-cipher] cipher-key 未配置，使用 dev 派生默认 key（生产环境请显式配置 chat.mcp.cipher-key）");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                raw = md.digest(DEV_SALT);
            } else {
                raw = Base64.getDecoder().decode(base64Key.trim());
                if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                    throw new IllegalArgumentException("cipher-key base64 解码后长度必须为 16/24/32 字节，实际=" + raw.length);
                }
            }
            return new SecretKeySpec(raw, ALG);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 TokenCipher 失败: " + e.getMessage(), e);
        }
    }

    /** 加密；plain == null 返回 null；空串原样返回空串。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        if (plain.isEmpty()) return "";
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipher = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH + cipher.length);
            buf.put(iv).put(cipher);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密；密文为空返回原值；解密失败抛 IllegalStateException。 */
    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        if (cipherText.isEmpty()) return "";
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不合法");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] cipher = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, cipher, 0, cipher.length);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(cipher), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败: " + e.getMessage(), e);
        }
    }

    /** 生成展示用掩码：null/空 → null；少于 4 位 → "***"；其它 → "***" + 后 4 位。 */
    public static String mask(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        if (plain.length() <= 4) return "***";
        return "***" + plain.substring(plain.length() - 4);
    }
}
