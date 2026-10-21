package com.shrs.zyshpt.util;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 HTTP 客户端（基于 JDK 自带 HttpURLConnection，零第三方依赖）。
 *
 * <p>既支持文本（GET / POST JSON / POST form），也支持二进制 Body 与响应头读取（网关加密报文用）。
 * 统一 UTF-8 编码，默认超时：连接 10s、读取 30s。</p>
 */
public final class HttpUtil {

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 30_000;

    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded;charset=UTF-8";
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    private static volatile boolean trustAll = false;
    private static volatile SSLSocketFactory trustAllFactory;

    private HttpUtil() {
    }

    /** 内网/自签名证书 HTTPS 场景可放开校验；会跳过证书与主机名校验，生产公网环境慎用 */
    public static void setTrustAll(boolean trustAll) {
        HttpUtil.trustAll = trustAll;
    }

    // ==================== 文本请求 ====================

    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        return execute(url, "GET", headers, null).bodyText();
    }

    public static String postJson(String url, String json) throws IOException {
        return postJson(url, json, null);
    }

    public static String postJson(String url, String json, Map<String, String> headers) throws IOException {
        return postText(url, CONTENT_TYPE_JSON, json, headers);
    }

    public static String postForm(String url, Map<String, String> form) throws IOException {
        return postForm(url, form, null);
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers) throws IOException {
        return postText(url, CONTENT_TYPE_FORM, encodeForm(form), headers);
    }

    // ==================== 二进制请求（网关加密报文） ====================

    /** POST 二进制 Body，返回含响应头的 Response（网关客户端使用） */
    public static Response post(String url, Map<String, String> headers, byte[] body) throws IOException {
        return execute(url, "POST", headers, body);
    }

    /** 通用请求：非 2xx 响应抛出 IOException（携带状态码与响应体） */
    public static Response execute(String url, String method, Map<String, String> headers,
                                   byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod(method);
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    conn.setRequestProperty(h.getKey(), h.getValue());
                }
            }
            applyTrustAllIfNeeded(conn);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body);
                    os.flush();
                } finally {
                    os.close();
                }
            }
            int code = conn.getResponseCode();
            Map<String, List<String>> respHeaders = conn.getHeaderFields();
            InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            byte[] respBody = in == null ? new byte[0] : readAll(in);
            if (code >= 400) {
                throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + ": "
                        + new String(respBody, StandardCharsets.UTF_8));
            }
            return new Response(code, respBody, respHeaders);
        } finally {
            conn.disconnect();
        }
    }

    /** HTTP 响应：状态码、响应头、原始 Body */
    public static class Response {
        public final int code;
        public final byte[] body;
        private final Map<String, List<String>> headers;

        Response(int code, byte[] body, Map<String, List<String>> headers) {
            this.code = code;
            this.body = body;
            this.headers = headers;
        }

        /** 按名称取响应头（大小写不敏感），不存在返回 null */
        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    // ==================== 内部工具 ====================

    private static String postText(String url, String contentType, String body,
                                   Map<String, String> headers) throws IOException {
        Map<String, String> all = new LinkedHashMap<String, String>();
        if (headers != null) {
            all.putAll(headers);
        }
        all.put("Content-Type", contentType);
        return execute(url, "POST", all, body.getBytes(StandardCharsets.UTF_8)).bodyText();
    }

    private static String encodeForm(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 必然存在
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    private static void applyTrustAllIfNeeded(HttpURLConnection conn) {
        if (!trustAll || !(conn instanceof HttpsURLConnection)) {
            return;
        }
        HttpsURLConnection https = (HttpsURLConnection) conn;
        https.setSSLSocketFactory(getTrustAllFactory());
        https.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                return true;
            }
        });
    }

    private static SSLSocketFactory getTrustAllFactory() {
        if (trustAllFactory == null) {
            synchronized (HttpUtil.class) {
                if (trustAllFactory == null) {
                    try {
                        TrustManager[] trustAllManagers = new TrustManager[]{new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }};
                        SSLContext ctx = SSLContext.getInstance("TLS");
                        ctx.init(null, trustAllManagers, new SecureRandom());
                        trustAllFactory = ctx.getSocketFactory();
                    } catch (Exception e) {
                        throw new IllegalStateException("初始化 trust-all SSLContext 失败", e);
                    }
                }
            }
        }
        return trustAllFactory;
    }
}
