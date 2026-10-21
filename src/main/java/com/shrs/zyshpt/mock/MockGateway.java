package com.shrs.zyshpt.mock;

import com.shrs.zyshpt.util.SmUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 按规范实现的本地 Mock 网关：校验认证签名与报文验签 -> 解密 -> 加密应签应答。
 * 仅供自测/联调（Main 与单元测试复用），生产对接不使用。
 */
public class MockGateway implements AutoCloseable {

    private final HttpServer server;
    private final String appSecret;
    private final String serverPrivateKey; // 服务端私钥：解密请求密钥、签署应答
    private final String clientPublicKey;  // 客户端公钥：加密应答密钥、验签请求

    /** port 传 0 表示随机可用端口，通过 {@link #port()} 获取 */
    public MockGateway(int port, String appSecret, String serverPrivateKey,
                       String clientPublicKey) throws IOException {
        this.appSecret = appSecret;
        this.serverPrivateKey = serverPrivateKey;
        this.clientPublicKey = clientPublicKey;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", new com.sun.net.httpserver.HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                MockGateway.this.handle(exchange);
            }
        });
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            byte[] cipher = readAll(exchange.getRequestBody());
            Map<String, String> h = headers(exchange);

            // 1. 网关认证：重算 SM4CMAC 比对（时间戳窗口与 nonce 唯一性由真实网关保证）
            byte[] key = Arrays.copyOf(appSecret.getBytes(StandardCharsets.UTF_8), 16);
            String message = h.get("shrs_uuid") + ":" + h.get("shrs_clientId") + ":" + h.get("shrs_timestamp");
            byte[] expected = SmUtil.sm4Cmac(key, message.getBytes(StandardCharsets.UTF_8));
            if (!Arrays.equals(expected, Base64.getDecoder().decode(h.get("shrs_signature")))) {
                respondJson(exchange, 401, "{\"code\":\"401\",\"message\":\"signature mismatch\"}");
                return;
            }

            // 2. 验签（客户端公钥）-> 解密密钥（服务端私钥）-> 解密报文
            byte[] sign = Base64.getDecoder().decode(h.get("x-shdata-signture"));
            if (!SmUtil.sm2Verify(clientPublicKey, cipher, sign)) {
                respondJson(exchange, 401, "{\"code\":\"401\",\"message\":\"signature verify failed\"}");
                return;
            }
            byte[] sm4Key = SmUtil.sm2Decrypt(serverPrivateKey, Base64.getDecoder().decode(h.get("x-shdata-secret")));
            byte[] plain = SmUtil.sm4GcmDecrypt(sm4Key, Base64.getDecoder().decode(h.get("x-shdata-iv")), cipher);
            System.out.println("[Mock网关] 收到明文 = " + new String(plain, StandardCharsets.UTF_8));

            // 3. 反向加密应签应答（应答明文 = 回显请求明文）
            byte[] respKey = SmUtil.sm4Key();
            byte[] respIv = SmUtil.randomBytes(12);
            byte[] respCipher = SmUtil.sm4GcmEncrypt(respKey, respIv, plain);
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().add("x-shdata-iv", Base64.getEncoder().encodeToString(respIv));
            exchange.getResponseHeaders().add("x-shdata-secret",
                    Base64.getEncoder().encodeToString(SmUtil.sm2Encrypt(clientPublicKey, respKey)));
            exchange.getResponseHeaders().add("x-shdata-signture",
                    Base64.getEncoder().encodeToString(SmUtil.sm2Sign(serverPrivateKey, respCipher)));
            exchange.sendResponseHeaders(200, respCipher.length);
            OutputStream os = exchange.getResponseBody();
            os.write(respCipher);
            os.close();
        } catch (Exception e) {
            respondJson(exchange, 500, "{\"code\":\"500\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /** 响应头转大小写不敏感 Map，方便按规范名取值 */
    private static Map<String, String> headers(HttpExchange exchange) {
        Map<String, String> h = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> e : exchange.getRequestHeaders().entrySet()) {
            if (e.getKey() != null && !e.getValue().isEmpty()) {
                h.put(e.getKey(), e.getValue().get(0));
            }
        }
        return h;
    }

    private static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
