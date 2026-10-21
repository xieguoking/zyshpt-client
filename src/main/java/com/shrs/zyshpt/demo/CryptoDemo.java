package com.shrs.zyshpt.demo;

import com.shrs.zyshpt.util.SmUtil;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 国密 SM2/SM3/SM4 用法演示 */
public class CryptoDemo {

    public static void main(String[] args) {
        String text = "Hello, 国密 SM2/SM3/SM4";

        // 1. SM3 摘要
        System.out.println("[SM3] 摘要 = " + SmUtil.sm3Hex(text));

        // 2. SM4 对称加解密（ECB）
        byte[] sm4Key = SmUtil.sm4Key();
        byte[] sm4Cipher = SmUtil.sm4Encrypt(sm4Key, text.getBytes(StandardCharsets.UTF_8));
        String sm4Plain = new String(SmUtil.sm4Decrypt(sm4Key, sm4Cipher), StandardCharsets.UTF_8);
        System.out.println("[SM4] 密文(hex) = " + Hex.toHexString(sm4Cipher));
        System.out.println("[SM4] 解密 = " + sm4Plain);

        // 3. SM2 密钥对 + 加解密 + 签名验签
        Map<String, String> keyPair = SmUtil.sm2GenerateKeyPairHex();
        System.out.println("[SM2] 公钥 = " + keyPair.get("publicKey"));
        System.out.println("[SM2] 私钥 = " + keyPair.get("privateKey"));

        byte[] sm2Cipher = SmUtil.sm2Encrypt(keyPair.get("publicKey"), text.getBytes(StandardCharsets.UTF_8));
        String sm2Plain = new String(SmUtil.sm2Decrypt(keyPair.get("privateKey"), sm2Cipher), StandardCharsets.UTF_8);
        System.out.println("[SM2] 密文(hex) = " + Hex.toHexString(sm2Cipher));
        System.out.println("[SM2] 解密 = " + sm2Plain);

        byte[] sign = SmUtil.sm2Sign(keyPair.get("privateKey"), text.getBytes(StandardCharsets.UTF_8));
        boolean verified = SmUtil.sm2Verify(keyPair.get("publicKey"), text.getBytes(StandardCharsets.UTF_8), sign);
        System.out.println("[SM2] 验签 = " + verified);
    }
}
