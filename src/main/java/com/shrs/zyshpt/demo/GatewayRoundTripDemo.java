package com.shrs.zyshpt.demo;

import com.shrs.zyshpt.client.GatewayClient;
import com.shrs.zyshpt.mock.MockGateway;
import com.shrs.zyshpt.util.SmUtil;

import java.util.Map;

/**
 * 本地 Mock 网关完整链路演示（无需任何参数与凭证）：
 * 客户端加密/签名 -> 网关验签/解密 -> 应答加密 -> 客户端验签/解密。
 * 正式调用请使用 com.shrs.zyshpt.GatewayMain。
 */
public class GatewayRoundTripDemo {

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
