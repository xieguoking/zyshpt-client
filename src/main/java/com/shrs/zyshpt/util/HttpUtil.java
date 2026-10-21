package com.shrs.zyshpt.util;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * 轻量 HTTP 客户端（基于 JDK 自带 HttpURLConnection，零第三方依赖）。
 *
 * <p>支持 GET / POST(JSON) / POST(form)，统一 UTF-8 编码，默认超时：连接 10s、读取 30s。</p>
 */
public final class HttpUtil {

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 30_000;

    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded;charset=UTF-8";

    private static volatile boolean trustAll = false;
    private static volatile SSLSocketFactory trustAllFactory;

    private HttpUtil() {
    }

    /** 内网/自签名证书 HTTPS 场景可放开校验；会跳过证书与主机名校验，生产公网环境慎用 */
    public static void setTrustAll(boolean trustAll) {
        HttpUtil.trustAll = trustAll;
    }

    // ==================== GET ====================

    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        return execute(url, "GET", null, null, headers);
    }

    // ==================== POST ====================

    public static String postJson(String url, String json) throws IOException {
        return postJson(url, json, null);
    }

    public static String postJson(String url, String json, Map<String, String> headers) throws IOException {
        return execute(url, "POST", CONTENT_TYPE_JSON, json, headers);
    }

    public static String postForm(String url, Map<String, String> form) throws IOException {
        return postForm(url, form, null);
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers) throws IOException {
        return execute(url, "POST", CONTENT_TYPE_FORM, encodeForm(form), headers);
    }

    /** 通用请求：非 2xx 响应抛出 IOException（携带状态码与响应体） */
    public static String execute(String url, String method, String contentType,
                                 String body, Map<String, String> headers) throws IOException {
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
                if (contentType != null) {
                    conn.setRequestProperty("Content-Type", contentType);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(bytes);
                    os.flush();
                } finally {
                    os.close();
                }
            }
            int code = conn.getResponseCode();
            InputStream in = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String respBody = in == null ? "" : readAll(in);
            if (code >= 400) {
                throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + ": " + respBody);
            }
            return respBody;
        } finally {
            conn.disconnect();
        }
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

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        in.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
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
