package com.shrs.zyshpt;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Endpoint utilities shared by the JavaFX client and unit tests. */
public final class GatewayEndpoint {

    private GatewayEndpoint() {
    }

    /**
     * Split a complete gateway URL into {@code baseUrl} and {@code apiid}.
     *
     * <p>{@code GatewayClient} follows GatewayMain's URL convention: the base URL is followed
     * by the API ID. The JavaFX form accepts a complete interface URL, for example:
     * {@code http://172.22.22.13:8081/b120b58edde14c2c9e10f9e553034887}.
     */
    public static String[] split(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("接口URL不能为空");
        }
        if (value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            throw new IllegalArgumentException("接口URL不支持查询参数或锚点");
        }
        // 用户粘贴的完整接口地址允许以 / 结尾，如 http://host/api-id/。
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("接口URL格式不正确", e);
        }
        if (uri.getScheme() == null || uri.getScheme().trim().isEmpty()) {
            throw new IllegalArgumentException("接口URL必须包含 http 或 https 协议");
        }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("接口URL缺少主机地址");
        }

        String path = uri.getPath();
        if (path == null || path.equals("/")) {
            throw new IllegalArgumentException("接口URL最后一段必须为接口ID");
        }
        int separator = path.lastIndexOf('/');
        String apiid = path.substring(separator + 1);
        if (apiid.trim().isEmpty()) {
            throw new IllegalArgumentException("接口URL最后一段必须为接口ID");
        }

        int apiidLength = apiid.length();
        String baseUrl = value.substring(0, value.length() - apiidLength);
        return new String[]{baseUrl, URLDecoder.decode(apiid, StandardCharsets.UTF_8)};
    }
}
