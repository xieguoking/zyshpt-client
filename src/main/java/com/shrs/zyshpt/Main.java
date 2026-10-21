package com.shrs.zyshpt;

import com.shrs.zyshpt.client.GatewayClient;
import com.shrs.zyshpt.mock.MockGateway;
import com.shrs.zyshpt.util.SmUtil;

import java.util.Map;

/**
 * 入口演示：本地 Mock 网关完整链路（客户端加密/签名 -> 网关验签/解密 -> 应答加密 -> 客户端验签/解密）。
 * 对接真实平台时，将 GatewayClient 指向正式地址并使用线下交换的密钥，例如：
 * <pre>
 * new GatewayClient("https://openapi.rsj.sh.gov.cn", appKey, appSecret, 服务端SM2公钥, 客户端SM2私钥)
 *         .call(apiid, "{\"name\":\"xieguojun\"}");
 * </pre>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // 演示密钥对：真实对接时由双方各自生成 SM2 密钥对并交换公钥
        Map<String, String> clientKeys = SmUtil.sm2GenerateKeyPairHex();
        Map<String, String> serverKeys = SmUtil.sm2GenerateKeyPairHex();

        String payload = "{\"name\":\"xieguojun\"}";
        System.out.println("[客户端] 请求明文 = " + payload);

        try (MockGateway gateway = new MockGateway(0, "demo-app-secret",
                serverKeys.get("privateKey"), clientKeys.get("publicKey"))) {
            GatewayClient client = new GatewayClient("http://127.0.0.1:" + gateway.port(),
                    "2000026", "demo-app-secret",
                    serverKeys.get("publicKey"), clientKeys.get("privateKey"));
            String response = client.call("b120b58edde14c2c9e10f9e553034887", payload);
            System.out.println("[客户端] 应答明文 = " + response);
        }
    }
}
