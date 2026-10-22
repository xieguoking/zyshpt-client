# zyshpt-client

JDK 1.8 轻量客户端工程：**国密（SM2/SM3/SM4）+ HTTP 接口调用**，依赖尽量精简。

## 技术栈

| 能力 | 实现 | 依赖 |
| --- | --- | --- |
| 国密 SM2/SM3/SM4 | BouncyCastle 轻量级 API（`bcprov-jdk15to18`） | **唯一运行期依赖** |
| HTTP 调用 | JDK 自带 `HttpURLConnection` | 无 |
| 单元测试 | JUnit 4（仅 test scope） | 不进产物 |

## 快速开始

```bash
mvn test        # 全部单测：国标向量 + HTTP 回环 + 网关全链路 + 命令行参数解析
mvn exec:java   # GatewayMain 命令行入口（无参数时列出缺失必填项并打印用法）
mvn -q compile exec:java -Dexec.mainClass=com.shrs.zyshpt.demo.GatewayRoundTripDemo  # 免凭证全链路演示
```

## 国密用法

```java
// SM3 摘要
String digest = SmUtil.sm3Hex("data");

// SM4 对称加解密（ECB / CBC + PKCS7）
byte[] key = SmUtil.sm4Key();
byte[] cipher = SmUtil.sm4Encrypt(key, "报文".getBytes(StandardCharsets.UTF_8));
byte[] plain  = SmUtil.sm4Decrypt(key, cipher);

// SM2 密钥对 / 加解密 / 签名验签
Map<String, String> kp = SmUtil.sm2GenerateKeyPairHex();   // publicKey / privateKey
byte[] c2 = SmUtil.sm2Encrypt(kp.get("publicKey"), data);
byte[] p2 = SmUtil.sm2Decrypt(kp.get("privateKey"), c2);
byte[] sign = SmUtil.sm2Sign(kp.get("privateKey"), data);
boolean ok  = SmUtil.sm2Verify(kp.get("publicKey"), data, sign);
```

对接注意（见 `SmUtil` 注释）：

- **SM2 密文格式**：默认 C1C3C2（GM/T 0003 新标准）；对方是老系统时用 `sm2Encrypt(pk, data, false)`（C1C2C3）。
- **SM2 userId**：签名默认 `1234567812345678`，双方必须一致，可自定义。
- **密钥格式**：公钥为非压缩 hex（`04||X||Y`，130 位），私钥为 32 字节 hex（64 位）。
- SM2 只适合短数据（如加解密对称密钥），大报文用 SM4。

## HTTP 用法

```java
String resp = HttpUtil.get("http://host/api");                       // GET
String resp = HttpUtil.postJson(url, "{\"k\":\"v\"}");               // POST JSON
String resp = HttpUtil.postForm(url, paramMap);                      // POST form
HttpUtil.get(url, headerMap);                                        // 带请求头
HttpUtil.setTrustAll(true);                                          // 内网自签名 HTTPS（慎用）
```

统一 UTF-8；默认连接超时 10s / 读取 30s（`HttpUtil.CONNECT_TIMEOUT_MS` 可改）；非 2xx 抛 `IOException` 并携带响应体。

## 网关客户端（spec/open-gateway.md）

```java
GatewayClient client = new GatewayClient(
        "https://openapi.rsj.sh.gov.cn",           // 网关地址
        appKey, appSecret,                          // 平台分配
        服务端SM2公钥, 客户端SM2私钥);                // 双方各自生成密钥对、线下交换公钥
String resp = client.call(apiid, "{\"name\":\"xieguojun\"}");  // 传 JSON 明文，返回应答明文
```

`call()` 内部按规范完成：随机 SM4 密钥/IV -> `SM4/GCM` 加密报文 -> 服务端公钥 `SM2(C1C3C2)` 加密密钥、
客户端私钥对密文 `SM3WithSM2` 签名 -> 经 `x-shdata-iv / x-shdata-secret / x-shdata-signture` Header 传输；
网关认证四要素经 `shrs_signature / shrs_timestamp / shrs_clientId / shrs_uuid` Header 传输
（签名 = `Base64(SM4CMAC(uuid:appKey:timestamp))`，密钥取 appSecret 前 16 字节不足补零）。
应答先验签再解密；认证失败网关返回 401/403，将以 IOException（含响应体）抛出。

命令行调用（`GatewayMain`，缺必填参数会列出全部缺失项并打印用法，退出码 1；调用失败退出码 2）：

```bash
mvn -q compile exec:java -Dexec.args='--url https://openapi.rsj.sh.gov.cn \
  --app-key 2000026 --app-secret <AppSecret> \
  --server-public-key <服务端SM2公钥hex> --client-private-key <客户端SM2私钥hex> \
  --apiid <接口ID> --payload "{\"name\":\"xieguojun\"}"'
```

- 必填：`--url`、`--app-key`、`--app-secret`、`--server-public-key`、`--client-private-key`
- 可选（未输入取默认值）：`--apiid`（默认 `b120b58edde14c2c9e10f9e553034887`）、`--payload`（默认 `{"name":"xieguojun"}`）、`--trust-all`（默认 `false`，自签名 HTTPS 环境跳过证书校验）
- 写法支持 `--name value` 与 `--name=value`，`--help` 查看完整用法

对接约定（与平台联调时的核对点）：

- `x-shdata-signture` 按规范原文拼写（非 signature）；Header 名大小写不敏感
- 签名值为 DER 编码（规范示例 `MEUCIQD...` 即 DER 格式），GCM 输出 = 密文 + 16 字节认证标签
- `x-shdata-secret` 为 SM2 C1C3C2 原始密文（C1 以 04 开头的非压缩点）Base64
- 本地联调可用 `MockGateway`（按规范实现服务端验签/解密/加密应答）

## 目录结构

```
src/main/java/com/shrs/zyshpt
├── GatewayMain.java       # 命令行入口（必填/可选参数解析）
├── client/GatewayClient.java  # 网关安全客户端（规范 2.1~2.4 全量实现）
├── mock/MockGateway.java      # 本地模拟网关（自测/联调用）
├── demo/                      # 示例：国密、HTTP、免凭证网关全链路演示
└── util/
    ├── SmUtil.java        # SM2/SM3/SM4（含 GCM、CMAC）
    └── HttpUtil.java      # 轻量 HTTP 客户端（文本 + 二进制）
src/test/java          # 国标向量 + HTTP 回环 + 网关客户端全链路
```
