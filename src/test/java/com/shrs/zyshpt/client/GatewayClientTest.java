package com.shrs.zyshpt.client;

import com.shrs.zyshpt.mock.MockGateway;
import com.shrs.zyshpt.util.SmUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GatewayClientTest {

    private static MockGateway gateway;
    private static GatewayClient client;
    private static String serverPublicKey;
    private static String clientPrivateKey;

    @BeforeClass
    public static void setUp() throws IOException {
        Map<String, String> clientKeys = SmUtil.sm2GenerateKeyPairHex();
        Map<String, String> serverKeys = SmUtil.sm2GenerateKeyPairHex();
        serverPublicKey = serverKeys.get("publicKey");
        clientPrivateKey = clientKeys.get("privateKey");
        gateway = new MockGateway(0, "demo-app-secret",
                serverKeys.get("privateKey"), clientKeys.get("publicKey"));
        client = new GatewayClient("http://127.0.0.1:" + gateway.port(), "2000026", "demo-app-secret",
                serverPublicKey, clientPrivateKey);
    }

    @AfterClass
    public static void tearDown() {
        gateway.close();
    }

    @Test
    public void fullRoundTrip() throws IOException {
        assertEquals("{\"name\":\"xieguojun\"}", client.call("api1", "{\"name\":\"xieguojun\"}"));
    }

    @Test
    public void chinesePayloadRoundTrip() throws IOException {
        String payload = "{\"msg\":\"测试中文消息\"}";
        assertEquals(payload, client.call("api2", payload));
    }

    @Test
    public void authFailureReturns401() {
        // appSecret 与网关不一致 -> 认证签名校验失败 -> 401
        GatewayClient badClient = new GatewayClient("http://127.0.0.1:" + gateway.port(),
                "2000026", "wrong-app-secret",
                serverPublicKey, clientPrivateKey);
        try {
            badClient.call("api1", "{}");
            throw new AssertionError("应抛出 IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("401"));
        }
    }
    @Test
    public void urlTrailingSlashTolerated() throws IOException {
        GatewayClient withSlash = new GatewayClient("http://127.0.0.1:" + gateway.port() + "/",
                "2000026", "demo-app-secret", serverPublicKey, clientPrivateKey);
        assertEquals("{}", withSlash.call("api1", "{}"));
    }
}
