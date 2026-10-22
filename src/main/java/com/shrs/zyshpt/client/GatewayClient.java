package com.shrs.zyshpt.client;

import com.shrs.zyshpt.util.HttpUtil;
import com.shrs.zyshpt.util.SmUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 开放网关安全客户端，实现 spec/open-gateway.md 的完整协议。
 *
 * <p>请求：随机 SM4 密钥/IV 以 SM4/GCM 加密报文，SM2(C1C3C2) 加密密钥、SM3WithSM2 签名密文，
 * 均经 x-shdata-* Header 传输；网关认证经 shrs_* Header（SM4CMAC 签名）传输。
 * 应答：先验签（服务端公钥），再解密密钥（客户端私钥），最后 SM4/GCM 解密。</p>
 */
public class GatewayClient {

    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final String serverPublicKey;  // 服务端 SM2 公钥（hex，平台方提供）
    private final String clientPrivateKey; // 客户端 SM2 私钥（hex，自行生成并保密）

    public GatewayClient(String baseUrl, String appKey, String appSecret,
                         String serverPublicKey, String clientPrivateKey) {
        this.baseUrl = baseUrl;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.serverPublicKey = serverPublicKey;
        this.clientPrivateKey = clientPrivateKey;
    }

    /** 调用网关接口：payload 为 JSON 明文，返回应答明文 */
    public String call(String apiid, String payload) throws IOException {
        byte[] plain = payload.getBytes(StandardCharsets.UTF_8);

        // 1. 报文加密（一次一密：每次随机生成 SM4 密钥与 IV）
        byte[] sm4Key = SmUtil.sm4Key();
        byte[] iv = SmUtil.randomBytes(12);
        byte[] ciphertext = SmUtil.sm4GcmEncrypt(sm4Key, iv, plain);

        // 2. 密钥加密（服务端公钥）+ 密文签名（客户端私钥）
        byte[] secret = SmUtil.sm2Encrypt(serverPublicKey, sm4Key);
        byte[] signature = SmUtil.sm2Sign(clientPrivateKey, ciphertext);

        // 3. 组装请求
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.putAll(authHeaders());
        headers.put("Content-Type", HttpUtil.CONTENT_TYPE_OCTET_STREAM);
        headers.put("x-shdata-iv", Base64.getEncoder().encodeToString(iv));
        headers.put("x-shdata-secret", Base64.getEncoder().encodeToString(secret));
        headers.put("x-shdata-signture", Base64.getEncoder().encodeToString(signature)); // 规范原文即此拼写

        // 4. 发送并处理应答（先验签后解密）；容忍地址末尾多余的 '/'
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        HttpUtil.Response resp = HttpUtil.post(base + "/" + apiid, headers, ciphertext);
        String sign = resp.header("x-shdata-signture");
        if (sign == null || resp.header("x-shdata-iv") == null || resp.header("x-shdata-secret") == null) {
            throw new IOException("应答缺少 x-shdata-* 安全头");
        }
        byte[] respCipher = resp.body;
        if (!SmUtil.sm2Verify(serverPublicKey, respCipher, Base64.getDecoder().decode(sign))) {
            throw new IOException("应答验签失败");
        }
        byte[] respKey = SmUtil.sm2Decrypt(clientPrivateKey,
                Base64.getDecoder().decode(resp.header("x-shdata-secret")));
        byte[] respIv = Base64.getDecoder().decode(resp.header("x-shdata-iv"));
        return new String(SmUtil.sm4GcmDecrypt(respKey, respIv, respCipher), StandardCharsets.UTF_8);
    }

    /** 网关认证 Header：签名 = Base64(SM4CMAC(nonce:appKey:timestamp))，密钥取 appSecret 前 16 字节（不足补零） */
    private Map<String, String> authHeaders() {
        String uuid = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        byte[] key = Arrays.copyOf(appSecret.getBytes(StandardCharsets.UTF_8), 16);
        String message = uuid + ":" + appKey + ":" + timestamp;
        byte[] mac = SmUtil.sm4Cmac(key, message.getBytes(StandardCharsets.UTF_8));

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("shrs_uuid", uuid);
        headers.put("shrs_timestamp", String.valueOf(timestamp));
        headers.put("shrs_clientId", appKey);
        headers.put("shrs_signature", Base64.getEncoder().encodeToString(mac));
        return headers;
    }
}
