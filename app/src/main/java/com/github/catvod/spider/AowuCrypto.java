package com.github.catvod.spider;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 嗷呜级加密工具（静态方法）
 *
 * 职责：ext 加密/解密、MD5 校验、密钥管理
 * 对应嗷呜的 woshinidie.bin + DexNative 解密链
 */
public class AowuCrypto {

    // 16 字节 AES-128 密钥（生产环境应从 assets/woshinidie.bin 读取）
    private static final String DEFAULT_KEY = "AowuSpider2026!!";

    /** 解密 ext 加密字符串 */
    public static String decrypt(String encrypted) throws Exception {
        return decrypt(encrypted, DEFAULT_KEY);
    }

    public static String decrypt(String encrypted, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decoded = Base64.decode(encrypted, Base64.DEFAULT);
        return new String(cipher.doFinal(decoded), "UTF-8");
    }

    /** 加密（用于生成 api.json 的 ext） */
    public static String encrypt(String plain) throws Exception {
        return encrypt(plain, DEFAULT_KEY);
    }

    public static String encrypt(String plain, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return Base64.encodeToString(cipher.doFinal(plain.getBytes("UTF-8")), Base64.DEFAULT);
    }

    /** MD5 校验（嗷呜 DEX 中有 md5 mismatch 检测） */
    public static String md5(String input) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
