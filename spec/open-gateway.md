## 2. 安全规范

​	为了保证接口数据传输安全及防止数据被篡改，通过采用国密算法（SM2、SM3、SM4）对传输内容进行加密与签名，保障数据传输的机密性、完整性及抗抵赖性。具体安全机制如下：

### 2.1 算法定义与密钥管理

1. **对称加密（SM4）**：
   - **算法模式**：约定采用 **`SM4/GCM/NoPadding`** 模式。
   - **密钥长度**：SM4 密钥长度固定为 **128 位（16 字节）**。
   - **初始向量（IV）长度**：在 `SM4/GCM/NoPadding` 模式下，推荐并约定初始向量 IV 长度为 **96 位（12 字节）**。
   - **数据格式**：接口 HTTP Body 直接传输 **SM4 加密后的原始二进制密文数据（Binary Data）**。
   - **密钥生成**：每次请求/应答必须生成全新的随机 128 位 SM4 密钥（Session Key）及 96 位初始向量（IV），实现一次一密（One-Time Pad）。
2. **非对称加密（SM2）**：
   - **加密模式**：约定采用 **`C1C3C2`** 排列模式。
   - **密钥传输**：用于对每次随机生成的 SM4 密钥进行非对称加密传输。
   - 客户端使用服务端的 SM2 公钥加密请求的 SM4 密钥；服务端应答时使用客户端的 SM2 公钥加密应答的 SM4 密钥。
   - **密钥生成**：对接双方各自生成，并将公钥交换给对方。密钥需定期更换。
3. **数字签名与验签（SM3WithSM2）**：
   - 用于保证数据的完整性与防篡改。
   - **签名原文**：签名针对 **SM4 加密后的二进制密文数据** 进行计算。
   - 发送方使用自身的 SM2 私钥结合 SM3 哈希算法（SM3WithSM2）对 SM4 密文二进制数据进行签名；接收方在收到报文后，先使用发送方的 SM2 公钥对密文进行验签，验签通过后再解密 Body。
   - **密钥生成**：使用数据加密的SM2密钥。

---

### 2.2 HTTP Header 规范

​	传输过程中，数据媒体类型、对称加密的 IV、加密后的 SM4 密钥以及数字签名均通过 HTTP Header 进行传递。注意：HTTP Header不区分大小写。

| Header 名称         | 说明                                                         | 示例 / 格式                |
| :------------------ | :----------------------------------------------------------- | :------------------------- |
| `Content-Type`      | 标识传输内容为二进制流数据                                   | `application/octet-stream` |
| `x-shdata-iv`       | SM4 加密使用的初始向量（IV），经过 Base64 编码               | `dGhpcyBpcyBhbiBpdi...`    |
| `x-shdata-secret`   | 使用接收方 SM2 公钥（C1C3C2 模式）加密后的 SM4 密钥，经过 Base64 编码 | `MEQCID...`                |
| `x-shdata-signture` | 使用发送方 SM2 私钥对 **SM4 密文** 生成的 SM3WithSM2 签名值，经过 Base64 编码 | `MEUCIQD...`               |



---

### 2.3 数据交互流程

#### 2.3.1 请求处理流程（客户端 -> 服务端）

1. **生成随机密钥与 IV**：
   - 客户端随机生成 128 位（16 字节）SM4 密钥 `Key_req` 和 96 位（12 字节）初始向量 `IV_req`。
2. **报文加密**：
   - 客户端使用 `Key_req` 和 `IV_req` 以 **`SM4/GCM/NoPadding`** 模式对请求明文进行加密，得到二进制密文 `Ciphertext_req`。
3. **密钥加密**：
   - 客户端使用**服务端 SM2 公钥**以 **`C1C3C2`** 模式加密 `Key_req`，得到 `Secret_req`。
4. **生成签名**：
   - 客户端使用**客户端 SM2 私钥**对 **二进制密文 `Ciphertext_req`** 计算 `SM3WithSM2` 签名，得到 `Signature_req`。
5. **组装 HTTP 请求**：
   - 设置 Header `Content-Type`: `application/octet-stream`
   - 设置 Header `x-shdata-iv`: Base64(`IV_req`)
   - 设置 Header `x-shdata-secret`: Base64(`Secret_req`)
   - 设置 Header `x-shdata-signture`: Base64(`Signature_req`)
   - HTTP Body 设置为二进制密文 `Ciphertext_req` 后发送请求。

#### 2.3.2 接收与验签解密流程（服务端处理）

1. **接收请求与校验 Header**：
   - 校验 `Content-Type` 是否为 `application/octet-stream`。
   - 读取 Header 中的 `x-shdata-iv`、`x-shdata-secret`、`x-shdata-signture` 以及 Body 中的二进制密文 `Ciphertext_req`。
2. **验证签名（先验签）**：
   - 服务端使用**客户端 SM2 公钥**对 Body 中的二进制密文 `Ciphertext_req` 和 Header 中的 `x-shdata-signture` 进行 `SM3WithSM2` 验签。若验签失败，直接拒绝处理。
3. **解密 SM4 密钥**：
   - 验签通过后，服务端使用**服务端 SM2 私钥**以 **`C1C3C2`** 模式解密 `x-shdata-secret`，获取 `Key_req`。
4. **解密 Body 内容**：
   - 服务端使用 `Key_req` 和 `x-shdata-iv` 以 **`SM4/GCM/NoPadding`** 模式对二进制密文 `Ciphertext_req` 进行解密，还原明文。

---

#### 2.3.3 应答安全规范（服务端 -> 客户端）

应答数据采用与请求完全一致的安全加密和签名机制：

1. **生成独立应答密钥与加密**：
   - 服务端为本次应答重新随机生成 128 位（16 字节）的 `Key_resp` 和 96 位（12 字节）的 `IV_resp`。
   - 服务端使用 `Key_resp` 和 `IV_resp` 以 **`SM4/GCM/NoPadding`** 模式对应答明文进行加密，生成二进制密文 `Ciphertext_resp`。
2. **应答密钥加密与签名**：
   - 服务端使用**客户端 SM2 公钥**以 **`C1C3C2`** 模式加密 `Key_resp` 并写入 Header `x-shdata-secret`。
   - 服务端将 `IV_resp` Base64 编码后写入 Header `x-shdata-iv`。
   - 服务端使用**服务端 SM2 私钥**对 **二进制密文 `Ciphertext_resp`** 计算 `SM3WithSM2` 签名，并写入 Header `x-shdata-signture`。
   - 设置 Header `Content-Type`: `application/octet-stream`。
3. **客户端验签与解密**：
   - 客户端接收到响应后，优先使用**服务端 SM2 公钥**对二进制密文响应 Body 和 `x-shdata-signture` 进行 `SM3WithSM2` 验签。
   - 验签成功后，客户端使用**客户端 SM2 私钥**以 **`C1C3C2`** 模式解密 `x-shdata-secret` 获取 `Key_resp`。
   - 客户端使用 `Key_resp` 和 `x-shdata-iv` 以 **`SM4/GCM/NoPadding`** 模式解密 Body 得到响应明文。



#### 2.3.4 示例代码

*待补充*



### 2.4 网关认证

前置平台网关采用基于 HMAC（Hash-based Message Authentication Code）的签名机制实现接口调用认证，该机制使用以下要素：

- **随机数（Nonce）**：由接口调用方生成，在一定的时间范围内必须保证唯一性，防止重放攻击。
- **时间戳（Timestamp）**：由接口调用方生成，用于校验请求的时效性。平台服务器将验证时间戳与当前时间的偏差，超出合理范围的请求将被拒绝。
- **应用 Key（AppKey）**：由平台为每个接入方分配的唯一标识。
- **应用密钥（AppSecret）**：由平台为每个接入方分配的保密密钥，仅调用方和平台知晓，用于 HMAC 的计算。

认证流程依赖调用方提供的随机数和时间戳，并结合平台颁发的 AppKey 和 AppSecret 进行签名计算，从而验证接口调用的合法性。

#### 2.4.1 网关认证 HTTP Header 规范

​	调用接口时，需要在 HTTP Header 中包含以下字段（HTTP Header 不区分大小写）：

| Header 名称      | 说明                                                         |
| :--------------- | :----------------------------------------------------------- |
| `shrs_signature` | 签名值，计算方法详见 [2.4.2 签名计算](#242-签名计算)         |
| `shrs_timestamp` | 接口调用时的时间戳（精确到毫秒），与计算签名时的时间戳一致，与北京时间偏差不超过 **30 秒** |
| `shrs_clientId`  | 平台分配的 AppKey                                            |
| `shrs_uuid`      | 随机数（Nonce），由调用方生成，保证每次请求唯一              |

#### 2.4.2 签名计算

签名计算过程分为以下三步：

1. **构建待签名信息**：
   - 将随机数（Nonce，如UUID）、AppKey 和时间戳（Timestamp）按顺序拼接，并使用英文冒号（`:`）分隔，形成待签名的字符串。
   - 待签名信息 = `Nonce` + `":"` + `AppKey` + `":"` + `Timestamp`
2. **生成签名**：
   - 使用 **AppSecret** 作为密钥。
   - 采用 **`SM4CMAC`** 算法对步骤 1 中生成的"待签名信息"进行计算，得到原始的签名结果（二进制数据）。
3. **编码签名结果**：
   - 对步骤 2 中得到的原始签名结果进行 **Base64 编码**，最终得到可用于传输的 `shrs_signature` 签名值。

#### 2.4.3 认证失败处理

- 当认证**不通过**时（如签名不匹配、时间戳与北京时间偏差超过 30 秒、Nonce 重复、认证 Header 缺失等），网关返回 `HTTP 401` 错误状态码。
- 当 **AppKey 未授权访问对应 APIID** 时（如 AppKey 与请求的接口未建立授权关系），网关返回 `HTTP 403` 错误状态码。

以上两类失败均通过响应体以 JSON 格式提供详细的错误信息：

```json
{
  "code": "401",
  "message": "传入时间戳与服务器时间戳超过允许范围",
  "path": "/b120b58edde14c2c9e10f9e553034887",
  "timestamp": "1691128890979"
}
```

#### 2.4.4 示例代码

示例代码（仅供参考）：

```java
public class GatewayClientSample {

    public void callSiApi() {

        String payload ="{\"name\":\"xieguojun\"}";
        String apiid = "";
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> httpEntity = new HttpEntity<>(payload, headers);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        generateHeaders(headers);

        RestTemplate restTemplate = new RestTemplate();

        String url = "https://openapi.rsj.sh.gov.cn/"+apiid;


        ResponseEntity<String> response = restTemplate.postForEntity(url, httpEntity, String.class);
        HttpStatus statusCode = response.getStatusCode();

        String responseBody = response.getBody();
        if (statusCode.is2xxSuccessful()) {
            //处理应答
        }

        //处理异常
        throw new RuntimeException(String.format("[%d]%s",statusCode.value(),responseBody));
    }

    private void generateHeaders(HttpHeaders headers) {
        // 分配的appKey/appSecret
        String appKey = "2000026";
        String appSecret = "<your-appSecret>";

        // 当前时间戳(精确到毫秒)，和服务器误差不能超过30秒
        long t = System.currentTimeMillis();

        //每次调用生成一个唯一流水号
        String uuid = UUID.randomUUID().toString();

        // 拼接消息
        String message = uuid + ":" + appKey + ":" + t;

        // 根据appSecret生成密钥
        byte[] key = Arrays.copyOf(appSecret.getBytes(StandardCharsets.UTF_8), 16);

        // 使用SM4CMAC算法计算签名
        HMac hmac = new HMac("SM4CMAC", key);
        byte[] digested = hmac.digest(message, StandardCharsets.UTF_8);
        // 转化为Base64
        String signature = Base64.getEncoder().encodeToString(digested);

        // 设置请求头
        headers.set("shrs_signature", signature);
        headers.set("shrs_timestamp", String.valueOf(t));
        headers.set("shrs_clientId", appKey);
        headers.set("shrs_uuid", uuid);
    }
}
```