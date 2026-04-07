package org.cycle.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token 工具类
 * 提供随机 token 生成与 sha256 哈希方法
 */
public class TokenUtil {
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成Token
     * @param byteLength
     * @return
     */
    public static String generateToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String generateToken() {
        return generateToken(32);
    }

    public static String sha256(String input) {
        // 对提供的 token 做 SHA-256 并用 Base64Url 编码，便于存储和比较
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}


