package com.shrs.zyshpt;

import com.shrs.zyshpt.mock.MockGateway;
import com.shrs.zyshpt.util.SmUtil;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GatewayMainTest {

    /** 五个必填参数的最小合法输入 */
    private static String[] requiredOnly() {
        return new String[]{
                "--url", "https://openapi.rsj.sh.gov.cn",
                "--app-key", "2000026",
                "--app-secret", "secret",
                "--server-public-key", "04AA",
                "--client-private-key", "1122"};
    }

    @Test
    public void fullOptions() {
        GatewayMain.Options o = GatewayMain.parse(new String[]{
                "--url", "https://gw.example.com",
                "--app-key", "1001",
                "--app-secret", "s",
                "--server-public-key", "04AA",
                "--client-private-key", "BB",
                "--apiid", "abc123",
                "--payload", "{\"a\":1}",
                "--trust-all"});
        assertEquals("https://gw.example.com", o.url);
        assertEquals("1001", o.appKey);
        assertEquals("abc123", o.apiid);
        assertEquals("{\"a\":1}", o.payload);
        assertTrue(o.trustAll);
    }

    @Test
    public void optionalUsesDefaults() {
        GatewayMain.Options o = GatewayMain.parse(requiredOnly());
        assertEquals("https://openapi.rsj.sh.gov.cn", o.url);
        assertEquals("b120b58edde14c2c9e10f9e553034887", o.apiid);
        assertEquals("{\"name\":\"xieguojun\"}", o.payload);
        assertFalse(o.trustAll);
    }

    @Test
    public void equalsForm() {
        GatewayMain.Options o = GatewayMain.parse(new String[]{
                "--url=https://gw.example.com", "--app-key=1001", "--app-secret=s",
                "--server-public-key=04AA", "--client-private-key=BB"});
        assertEquals("https://gw.example.com", o.url);
        assertEquals("1001", o.appKey);
    }

    @Test
    public void missingRequiredListsAll() {
        try {
            GatewayMain.parse(new String[]{"--url", "https://x"});
            fail("应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("--app-key"));
            assertTrue(e.getMessage().contains("--app-secret"));
            assertTrue(e.getMessage().contains("--server-public-key"));
            assertTrue(e.getMessage().contains("--client-private-key"));
        }
    }

    @Test
    public void unknownAndMissingValueRejected() {
        try {
            GatewayMain.parse(new String[]{"--ur", "x", "--url", "y", "--app-key", "a",
                    "--app-secret", "b", "--server-public-key", "c", "--client-private-key", "d"});
            fail("未知参数应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("--ur"));
        }
        try { // --url 后跟了下一个选项，缺少参数值
            GatewayMain.parse(new String[]{"--url", "--app-key", "a", "--app-secret", "b",
                    "--server-public-key", "c", "--client-private-key", "d"});
            fail("缺少参数值应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("url"));
        }
    }

    @Test
    public void mainEndToEndAgainstMockGateway() throws IOException {
        // 直接驱动 main()：完整命令行 -> GatewayClient -> Mock 网关 -> 解密应答
        Map<String, String> clientKeys = SmUtil.sm2GenerateKeyPairHex();
        Map<String, String> serverKeys = SmUtil.sm2GenerateKeyPairHex();
        try (MockGateway gateway = new MockGateway(0, "demo-app-secret",
                serverKeys.get("privateKey"), clientKeys.get("publicKey"))) {
            GatewayMain.main(new String[]{
                    "--url", "http://127.0.0.1:" + gateway.port() + "/", // 末尾带 / 也能容忍
                    "--app-key", "2000026",
                    "--app-secret", "demo-app-secret",
                    "--server-public-key", serverKeys.get("publicKey"),
                    "--client-private-key", clientKeys.get("privateKey"),
                    "--apiid", "demo-api",
                    "--payload", "{\"cli\":true}",
                    "--trust-all"});
        }
    }
}
