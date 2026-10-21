package com.shrs.zyshpt.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpUtilTest {

    private static HttpServer server;
    private static String base;

    @BeforeClass
    public static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> respond(exchange, 200, "{\"ok\":true}"));
        server.createContext("/echo", exchange -> respond(exchange, 200, readBody(exchange)));
        server.createContext("/boom", exchange -> respond(exchange, 500, "server error"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterClass
    public static void stopServer() {
        server.stop(0);
    }

    @Test
    public void get() throws IOException {
        assertEquals("{\"ok\":true}", HttpUtil.get(base + "/ping"));
    }

    @Test
    public void postJson() throws IOException {
        assertEquals("{\"name\":\"zyshpt\"}", HttpUtil.postJson(base + "/echo", "{\"name\":\"zyshpt\"}"));
    }

    @Test
    public void postForm() throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("a", "1");
        form.put("name", "中文");
        String resp = HttpUtil.postForm(base + "/echo", form);
        assertTrue(resp.contains("a=1") && resp.contains("name="));
    }

    @Test
    public void errorResponseThrowsWithBody() {
        try {
            HttpUtil.get(base + "/boom");
            throw new AssertionError("应抛出 IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("500") && e.getMessage().contains("server error"));
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
