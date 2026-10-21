# zyshpt-client

JDK 1.8 轻量客户端工程：**国密（SM2/SM3/SM4）+ HTTP 接口调用**，依赖尽量精简。

## 技术栈

| 能力 | 实现 | 依赖 |
| --- | --- | --- |
| 国密 SM2/SM3/SM4 | BouncyCastle 轻量级 API（`bcprov-jdk15to18`） | **唯一运行期依赖** |
| HTTP 调用 | JDK 自带 `HttpURLConnection` | 无 |
| 单元测试 | JUnit 4（仅 test scope） | 不进产物 |

> 国内网络拉取依赖慢时，可在 `~/.m2/settings.xml` 配置阿里云镜像：
> ```xml
> <mirror>
>   <id>aliyun</id>
>   <mirrorOf>central</mirrorOf>
>   <url>https://maven.aliyun.com/repository/public</url>
> </mirror>
> ```

## 快速开始

```bash
mvn test                                                   # 跑单测（含国标测试向量）
mvn compile exec:java -Dexec.mainClass="com.shrs.zyshpt.demo.CryptoDemo"
mvn compile exec:java -Dexec.mainClass="com.shrs.zyshpt.demo.HttpDemo"
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

## 目录结构

```
src/main/java/com/shrs/zyshpt
├── demo/          # 可运行的用法示例
└── util/
    ├── SmUtil.java    # SM2/SM3/SM4
    └── HttpUtil.java  # 轻量 HTTP 客户端
src/test/java      # 国标测试向量 + 本地 HTTP 回环测试
```
