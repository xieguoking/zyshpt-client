package com.shrs.zyshpt;

import com.shrs.zyshpt.client.GatewayClient;
import com.shrs.zyshpt.util.HttpUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网关客户端命令行入口。
 *
 * <pre>
 * 用法:
 *   GatewayMain --url <网关地址> --app-key <AppKey> --app-secret <AppSecret>
 *               --server-public-key <服务端SM2公钥hex> --client-private-key <客户端SM2私钥hex>
 *               [--apiid <接口ID>] [--payload <JSON报文>] [--trust-all]
 * </pre>
 */
public class GatewayMain {

    /** 必填参数 */
    private static final String[] REQUIRED = {
            "url", "app-key", "app-secret", "server-public-key", "client-private-key"};

    /** 可选参数（未输入时使用默认值，见 Options） */
    private static final Set<String> OPTIONAL = new HashSet<String>(Arrays.asList(
            "apiid", "payload", "trust-all", "help"));

    private static final String DEFAULT_APIID = "b120b58edde14c2c9e10f9e553034887";
    private static final String DEFAULT_PAYLOAD = "{\"name\":\"xieguojun\"}";

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                usage();
                return;
            }
        }
        try {
            Options options = parse(args);
            if (options.trustAll) {
                HttpUtil.setTrustAll(true); // 自签名 HTTPS 环境使用
            }
            GatewayClient client = new GatewayClient(options.url, options.appKey, options.appSecret,
                    options.serverPublicKey, options.clientPrivateKey);
            System.out.println("[GatewayMain] 请求 " + options.url + "/" + options.apiid
                    + " 明文 = " + options.payload);
            System.out.println("[GatewayMain] 应答明文 = " + client.call(options.apiid, options.payload));
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            usage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("调用失败: " + e.getMessage());
            System.exit(2);
        }
    }

    /** 解析并校验命令行参数；缺少必填、未知参数或缺参数值时抛 IllegalArgumentException */
    static Options parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("无法识别: " + arg);
            }
            int eq = arg.indexOf('=');
            String name = eq > 2 ? arg.substring(2, eq) : arg.substring(2);
            if (!Arrays.asList(REQUIRED).contains(name) && !OPTIONAL.contains(name)) {
                throw new IllegalArgumentException("未知参数: --" + name);
            }
            String value;
            if (eq > 2) {                       // --name=value
                value = arg.substring(eq + 1);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) { // --name value
                value = args[++i];
            } else if (name.equals("trust-all") || name.equals("help")) {      // 布尔开关
                value = "true";
            } else {
                throw new IllegalArgumentException("--" + name + " 缺少参数值");
            }
            values.put(name, value);
        }
        List<String> missing = new ArrayList<String>();
        for (String name : REQUIRED) {
            if (!values.containsKey(name)) {
                missing.add("--" + name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少必填参数: " + missing);
        }
        return new Options(values);
    }

    /** 解析结果（字段包内可见，便于单测） */
    static class Options {
        final String url;
        final String appKey;
        final String appSecret;
        final String serverPublicKey;
        final String clientPrivateKey;
        final String apiid;
        final String payload;
        final boolean trustAll;

        Options(Map<String, String> values) {
            String u = values.get("url"); // 去掉末尾多余的 '/'，避免拼出 //apiid
            url = u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
            appKey = values.get("app-key");
            appSecret = values.get("app-secret");
            serverPublicKey = values.get("server-public-key");
            clientPrivateKey = values.get("client-private-key");
            apiid = values.getOrDefault("apiid", DEFAULT_APIID);
            payload = values.getOrDefault("payload", DEFAULT_PAYLOAD);
            trustAll = Boolean.parseBoolean(values.get("trust-all"));
        }
    }

    private static void usage() {
        System.out.println("用法: GatewayMain --url <网关地址> --app-key <AppKey> --app-secret <AppSecret>"
                + " --server-public-key <服务端SM2公钥hex> --client-private-key <客户端SM2私钥hex>"
                + " [--apiid <接口ID>] [--payload <JSON报文>] [--trust-all]");
        System.out.println();
        System.out.println("必填参数:");
        System.out.println("  --url <网关地址>              如 https://openapi.rsj.sh.gov.cn");
        System.out.println("  --app-key <AppKey>            平台分配的 AppKey");
        System.out.println("  --app-secret <AppSecret>      平台分配的 AppSecret");
        System.out.println("  --server-public-key <hex>     服务端 SM2 公钥（04 开头，130 位 hex）");
        System.out.println("  --client-private-key <hex>    客户端 SM2 私钥（64 位 hex）");
        System.out.println("可选参数:");
        System.out.println("  --apiid <接口ID>              默认: " + DEFAULT_APIID);
        System.out.println("  --payload <JSON报文>          默认: " + DEFAULT_PAYLOAD + "（含空格需加引号）");
        System.out.println("  --trust-all                   跳过 HTTPS 证书校验，默认: false");
        System.out.println("参数支持 \"--name value\" 与 \"--name=value\" 两种写法，--help 查看本帮助");
    }
}
