package com.shrs.zyshpt.util;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 国密算法工具类（基于 BouncyCastle 轻量级 API，无需注册 Provider）。
 *
 * <ul>
 *   <li>SM2：非对称加解密、签名验签（sm2p256v1 曲线，密文默认 C1C3C2 格式）</li>
 *   <li>SM3：消息摘要（256 位）</li>
 *   <li>SM4：对称加解密（ECB/CBC，PKCS7Padding）</li>
 * </ul>
 */
public final class SmUtil {

    /** SM2 推荐曲线 sm2p256v1 */
    private static final X9ECParameters SM2_X9_PARAMS = GMNamedCurves.getByName("sm2p256v1");
    private static final ECDomainParameters SM2_DOMAIN = new ECDomainParameters(
            SM2_X9_PARAMS.getCurve(), SM2_X9_PARAMS.getG(), SM2_X9_PARAMS.getN(), SM2_X9_PARAMS.getH());

    /** SM2 签名默认 userId（与主流实现互通；签名/验签双方必须一致） */
    public static final byte[] DEFAULT_SM2_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom RANDOM = new SecureRandom();

    private SmUtil() {
    }

    // ==================== SM3 摘要 ====================

    /** SM3 摘要，返回 32 字节 */
    public static byte[] sm3(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return out;
    }

    /** SM3 摘要（UTF-8），返回 64 位 hex 字符串 */
    public static String sm3Hex(String data) {
        return Hex.toHexString(sm3(data.getBytes(StandardCharsets.UTF_8)));
    }

    // ==================== SM4 对称加解密 ====================

    /** 生成 16 字节（128 位）SM4 密钥 */
    public static byte[] sm4Key() {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        return key;
    }

    /** SM4 ECB 加密（PKCS7Padding） */
    public static byte[] sm4Encrypt(byte[] key, byte[] data) {
        return sm4Process(true, key, null, data);
    }

    /** SM4 ECB 解密（PKCS7Padding） */
    public static byte[] sm4Decrypt(byte[] key, byte[] data) {
        return sm4Process(false, key, null, data);
    }

    /** SM4 CBC 加密（PKCS7Padding），iv 为 16 字节 */
    public static byte[] sm4EncryptCbc(byte[] key, byte[] iv, byte[] data) {
        return sm4Process(true, key, iv, data);
    }

    /** SM4 CBC 解密（PKCS7Padding），iv 为 16 字节 */
    public static byte[] sm4DecryptCbc(byte[] key, byte[] iv, byte[] data) {
        return sm4Process(false, key, iv, data);
    }

    private static byte[] sm4Process(boolean encrypt, byte[] key, byte[] iv, byte[] data) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 密钥长度必须为 16 字节");
        }
        if (iv != null && iv.length != 16) {
            throw new IllegalArgumentException("SM4 CBC 模式 iv 长度必须为 16 字节");
        }
        BufferedBlockCipher cipher = iv == null
                ? new PaddedBufferedBlockCipher(new SM4Engine())
                : new PaddedBufferedBlockCipher(new CBCBlockCipher(new SM4Engine()));
        CipherParameters params = iv == null
                ? new KeyParameter(key)
                : new ParametersWithIV(new KeyParameter(key), iv);
        try {
            cipher.init(encrypt, params);
            byte[] out = new byte[cipher.getOutputSize(data.length)];
            int len = cipher.processBytes(data, 0, data.length, out, 0);
            len += cipher.doFinal(out, len);
            return Arrays.copyOf(out, len);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4 " + (encrypt ? "加密" : "解密") + "失败", e);
        }
    }

    // ==================== SM2 非对称加解密 / 签名 ====================

    /**
     * 生成 SM2 密钥对。
     *
     * @return publicKey（04 || X || Y 非压缩 hex，130 位）、privateKey（32 字节 hex，64 位）
     */
    public static Map<String, String> sm2GenerateKeyPairHex() {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(SM2_DOMAIN, RANDOM));
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        Map<String, String> keys = new LinkedHashMap<String, String>();
        keys.put("publicKey", sm2PublicToHex((ECPublicKeyParameters) keyPair.getPublic()));
        keys.put("privateKey", sm2PrivateToHex((ECPrivateKeyParameters) keyPair.getPrivate()));
        return keys;
    }

    /** 公钥参数 -> hex（04 || X || Y，非压缩格式） */
    public static String sm2PublicToHex(ECPublicKeyParameters publicKey) {
        return Hex.toHexString(publicKey.getQ().getEncoded(false));
    }

    /** 私钥参数 -> hex（定长 64 位） */
    public static String sm2PrivateToHex(ECPrivateKeyParameters privateKey) {
        String d = privateKey.getD().toString(16);
        StringBuilder sb = new StringBuilder(64);
        for (int i = d.length(); i < 64; i++) {
            sb.append('0');
        }
        return sb.append(d).toString();
    }

    /** hex -> 公钥参数 */
    public static ECPublicKeyParameters sm2PublicFromHex(String publicKeyHex) {
        ECPoint q = SM2_X9_PARAMS.getCurve().decodePoint(Hex.decode(publicKeyHex));
        return new ECPublicKeyParameters(q, SM2_DOMAIN);
    }

    /** hex -> 私钥参数 */
    public static ECPrivateKeyParameters sm2PrivateFromHex(String privateKeyHex) {
        return new ECPrivateKeyParameters(new BigInteger(1, Hex.decode(privateKeyHex)), SM2_DOMAIN);
    }

    /** SM2 加密（C1C3C2 格式，GM/T 0003 标准）。仅适合短数据，大报文请用 SM4。 */
    public static byte[] sm2Encrypt(String publicKeyHex, byte[] data) {
        return sm2Encrypt(publicKeyHex, data, true);
    }

    /**
     * SM2 加密。
     *
     * @param c1c3c2 true=新标准 C1C3C2；false=旧标准 C1C2C3（与对方系统对接时以其约定为准）
     */
    public static byte[] sm2Encrypt(String publicKeyHex, byte[] data, boolean c1c3c2) {
        SM2Engine engine = new SM2Engine(c1c3c2 ? SM2Engine.Mode.C1C3C2 : SM2Engine.Mode.C1C2C3);
        try {
            engine.init(true, new ParametersWithRandom(sm2PublicFromHex(publicKeyHex), RANDOM));
            return engine.processBlock(data, 0, data.length);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    /** SM2 解密（C1C3C2 格式），与 {@link #sm2Encrypt(String, byte[])} 配对使用 */
    public static byte[] sm2Decrypt(String privateKeyHex, byte[] data) {
        return sm2Decrypt(privateKeyHex, data, true);
    }

    /** SM2 解密 */
    public static byte[] sm2Decrypt(String privateKeyHex, byte[] data, boolean c1c3c2) {
        SM2Engine engine = new SM2Engine(c1c3c2 ? SM2Engine.Mode.C1C3C2 : SM2Engine.Mode.C1C2C3);
        try {
            engine.init(false, sm2PrivateFromHex(privateKeyHex));
            return engine.processBlock(data, 0, data.length);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 解密失败", e);
        }
    }

    /** SM2 签名（默认 userId），签名值为 r||s，64 字节 */
    public static byte[] sm2Sign(String privateKeyHex, byte[] data) {
        return sm2Sign(privateKeyHex, data, DEFAULT_SM2_USER_ID);
    }

    /** SM2 签名，可自定义 userId */
    public static byte[] sm2Sign(String privateKeyHex, byte[] data, byte[] userId) {
        SM2Signer signer = new SM2Signer();
        signer.init(true, new ParametersWithID(
                new ParametersWithRandom(sm2PrivateFromHex(privateKeyHex), RANDOM), userId));
        signer.update(data, 0, data.length);
        try {
            return signer.generateSignature();
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /** SM2 验签（默认 userId） */
    public static boolean sm2Verify(String publicKeyHex, byte[] data, byte[] sign) {
        return sm2Verify(publicKeyHex, data, sign, DEFAULT_SM2_USER_ID);
    }

    /** SM2 验签，userId 必须与签名方一致 */
    public static boolean sm2Verify(String publicKeyHex, byte[] data, byte[] sign, byte[] userId) {
        SM2Signer verifier = new SM2Signer();
        verifier.init(false, new ParametersWithID(sm2PublicFromHex(publicKeyHex), userId));
        verifier.update(data, 0, data.length);
        return verifier.verifySignature(sign);
    }
}
