package com.shrs.zyshpt.util;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmUtilTest {

    @Test
    public void sm3KnownVector() {
        // GB/T 32905-2016 标准测试向量
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0",
                SmUtil.sm3Hex("abc"));
        assertEquals("1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b",
                SmUtil.sm3Hex(""));
    }

    @Test
    public void sm4EcbKnownVector() {
        // GB/T 32907-2016 标准测试向量（首块）
        byte[] key = Hex.decode("0123456789abcdeffedcba9876543210");
        byte[] plain = Hex.decode("0123456789abcdeffedcba9876543210");
        byte[] cipher = SmUtil.sm4Encrypt(key, plain);
        assertEquals("681edf34d206965e86b3e94f536e4246", Hex.toHexString(cipher, 0, 16));
        assertArrayEquals(plain, SmUtil.sm4Decrypt(key, cipher));
    }

    @Test
    public void sm4CbcRoundTrip() {
        byte[] key = SmUtil.sm4Key();
        byte[] iv = SmUtil.sm4Key(); // 任意 16 字节可作 iv
        byte[] data = "测试数据 CBC mode 123".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, SmUtil.sm4DecryptCbc(key, iv, SmUtil.sm4EncryptCbc(key, iv, data)));
    }

    @Test
    public void sm2EncryptDecryptRoundTrip() {
        java.util.Map<String, String> keyPair = SmUtil.sm2GenerateKeyPairHex();
        byte[] data = "SM2 加解密测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = SmUtil.sm2Encrypt(keyPair.get("publicKey"), data);
        assertArrayEquals(data, SmUtil.sm2Decrypt(keyPair.get("privateKey"), cipher));
    }

    @Test
    public void sm2SignVerify() {
        java.util.Map<String, String> keyPair = SmUtil.sm2GenerateKeyPairHex();
        byte[] data = "SM2 签名测试".getBytes(StandardCharsets.UTF_8);
        byte[] sign = SmUtil.sm2Sign(keyPair.get("privateKey"), data);
        assertTrue(SmUtil.sm2Verify(keyPair.get("publicKey"), data, sign));

        sign[0] ^= 0x01; // 篡改签名
        assertFalse(SmUtil.sm2Verify(keyPair.get("publicKey"), data, sign));
    }
    @Test
    public void sm4GcmRoundTripAndTamper() {
        byte[] key = SmUtil.sm4Key();
        byte[] iv = SmUtil.randomBytes(12);
        byte[] data = "SM4 GCM 测试".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = SmUtil.sm4GcmEncrypt(key, iv, data);
        assertArrayEquals(data, SmUtil.sm4GcmDecrypt(key, iv, cipher));
        cipher[0] ^= 0x01; // 篡改密文
        try {
            SmUtil.sm4GcmDecrypt(key, iv, cipher);
            throw new AssertionError("篡改后应解密失败");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void sm4Cmac() {
        byte[] key = "1234567890abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] msg = "nonce:2000026:1691128890979".getBytes(StandardCharsets.UTF_8);
        byte[] mac = SmUtil.sm4Cmac(key, msg);
        assertEquals(16, mac.length);
        assertArrayEquals(mac, SmUtil.sm4Cmac(key, msg));
        assertFalse(Arrays.equals(mac, SmUtil.sm4Cmac(key,
                "nonce:2000026:1691128890980".getBytes(StandardCharsets.UTF_8))));
    }
}
