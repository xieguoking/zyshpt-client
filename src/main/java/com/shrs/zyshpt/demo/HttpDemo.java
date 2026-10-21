package com.shrs.zyshpt.demo;

import com.shrs.zyshpt.util.HttpUtil;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 调用演示：用 JDK 内置 HttpServer 起一个本地服务，再用 HttpUtil 调用。
 * 实际对接时把 URL 换成目标系统接口即可。
 */
public class HttpDemo {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18080), 0);
        server.createContext("/ping", exchange -> respond(exchange, 200, "{\"ok\":true}"));
        server.createContext("/echo", exchange -> {
            InputStream in = exchange.getRequestBody();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            respond(exchange, 200, "{\"youSent\":\"" + bos.toString("UTF-8") + "\"}");
        });
        server.start();
        try {
            System.out.println("[GET ] " + HttpUtil.get("http://127.0.0.1:18080/ping"));
            System.out.println("[POST] " + HttpUtil.postJson("http://127.0.0.1:18080/echo", "{\"name\":\"zyshpt\"}"));
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
