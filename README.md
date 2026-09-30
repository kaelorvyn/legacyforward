# legacyforward

让 **Fabric 服务端**读得懂 Velocity / BungeeCord 的 **legacy（BungeeCord 式）玩家信息转发** ——
也就是 Fabric 版的 `bungeecord: true`，服务端终于能拿到**玩家真实 IP**。

> **English**: A tiny server-side Fabric mod that implements BungeeCord/Velocity **legacy** player-info
> forwarding for Minecraft **1.21.1**. It reads the client IP that the proxy embeds in the handshake
> packet, so server logs / bans / anti-cheat see the real player address instead of `127.0.0.1`.
> Pure Mixin, no Fabric API dependency, ~4 KB.

## 为什么需要它

跑 Velocity 代理时，`player-info-forwarding-mode` 有三种：`none` / `legacy` / `modern`。

- **Paper / Spigot 后端**：`spigot.yml` 里 `bungeecord: true` 就完事了。
- **Fabric 后端**：一直没有现成实现 ——
  `FabricProxy-Lite` 只吃 `modern`（它的文档第 3 步写死"把 Velocity 设为 modern"）；
  支持 BungeeCord 的老 `FabricProxy` 已弃用且最高只到 1.18.1；
  `FabricProxyPlus` 只覆盖 1.21.6+ 且仅 bungee、许可证是 All-Rights-Reserved。

而这台网络里有 Paper **1.12.2**（起床战争）和 PaperSpigot **1.8.8**（空岛战争）后端，
`modern` 要求 1.13+，**全局切 `modern` 会把它们打断**，所以只能留在 `legacy`。

缺的其实不是「能力」而是「轮子」：转发数据是**明文**，就藏在握手包里。这个模组把那句话变成了代码。

## 它怎么工作

Velocity 的 `PlayerDataForwarding#createLegacyForwardingAddress` 决定了线上格式：

```
host\0ip\0uuid(无横线)\0propertiesJson          # \0 = NUL (0x00)
```

握手包（`ClientIntentionPacket`）的 `serverAddress` 是协议里**唯一**能携带这些数据的地方。
模组在 `ServerHandshakePacketListenerImpl#handleIntention` 的 HEAD 把它解析出来，
然后改写 `Connection` 的地址**字段**：

```java
int port = ((InetSocketAddress) this.connection.getRemoteAddress()).getPort(); // 端口要趁改写前取
InetSocketAddress real = LegacyForward.parse(packet.hostName(), port);
if (real != null) this.connection.address = real;
```

这正是 Spigot/Paper `bungeecord: true` 的同款做法。

> **踩过的坑（给后来人）**：第一版是给 `Connection#getRemoteAddress()` 挂 `@Inject` 返回伪造地址 ——
> **完全无效**，日志照旧记 `127.0.0.1`。原因是服务端打印玩家地址走的是
> `Connection.getLoggableAddress()`，它字节码第一条指令就是 `getfield`，**直接读地址字段**，
> 根本不经过 `getRemoteAddress()`。改字段才能一次覆盖两条路。
> 字段 `Connection.address` 不是 final，用 access widener 打开即可。

## 能力边界（说清楚，不夸大）

| 项目 | 情况 |
|---|---|
| **真实 IP** | ✅ 这就是本模组的作用 |
| **UUID** | ➖ **本来就不需要转发**。代理 `online-mode=false` 生成的离线 UUID 与服务端 `nameUUIDFromBytes("OfflinePlayer:" + name)` 天然相同，玩家数据不会错位 |
| **皮肤** | ❌ 拿不到。离线代理的 `properties` 是空的，Fabric 侧也没有 SkinsRestorer |

## 要求

- Minecraft **1.21.1**（服务端）
- Fabric Loader **≥ 0.16**，Java **21+**
- 仅服务端（`"environment": "server"`），客户端**不需要**装
- 代理侧：`player-info-forwarding-mode = "legacy"`

## 安装

把 jar 丢进服务端的 `mods/`，重启即可。没有配置文件、没有命令、没有 entrypoint。

```
mods/legacyforward-1.0.0.jar
```

## ⚠️ 安全提醒

`legacy` 转发的本质是**后端无条件信任握手里的明文数据**。
换句话说：**谁能直连后端端口，谁就能伪造任意 IP 和 UUID**。

所以后端服务端应该：

- 只监听 `127.0.0.1`（`server.properties` 里 `server-ip=127.0.0.1`），或
- 用防火墙只放行代理所在主机

原版 Fabric 服务端没装这个模组时是"忽略"这段数据，装上之后它就"相信"了 —— 这一点必须自己兜住。

## 从源码构建

```bash
./gradlew build
```

成品会出现在 `build/libs/`（同时被 `remapJar` 拷一份到项目根）。

> **注意**：Fabric Loom 1.18.2 自身是用 Java 25 编译的，**跑 Gradle 的那个 JVM 必须是 JDK 25**。
> 本仓库的 `gradle.properties` 里刻意没有写死路径，请放到你自己的 `~/.gradle/gradle.properties`：
>
> ```properties
> org.gradle.java.home=/path/to/jdk-25
> ```
>
> 模组字节码本身仍是 `release 21`，所以**运行服务端**用 JDK 21 或更高都可以。

## 验证

用一个 headless 客户端（mineflayer + `fakeHost` 伪造握手）跑完整登录，以服务端日志
`PlayerList.placeNewPlayer` 打印的 `{}[{}] logged in with entity id {}` 为准：

| 用例 | 服务端实际记录 | 结果 |
|---|---|---|
| 带 legacy 转发（声明 `203.0.113.7`） | `FwdLegacy[/203.0.113.7:55612] logged in with entity id 17` | ✅ 真实 IP 生效 |
| 对照组：普通直连 | `FwdPlain[/127.0.0.1:55634] logged in` | ✅ 不受影响 |
| IP 位置塞域名 | `FwdBadIp[/127.0.0.1:55648] logged in` | ✅ 静默回退 |
| 转发数据不完整（只有 3 段） | `FwdTrunc[/127.0.0.1:55672] logged in` | ✅ 静默回退 |

四个用例都能正常登录，不会把服务端搞崩。任何不匹配（没有 NUL / 段数不够 / 不是 IP 字面量）
都**静默回退**，行为与不装模组完全一致。

> 顺带一条经验：验证这类"我构造的输入 → 被测系统如何反应"的功能之前，
> **先给输入本身加一个正交观测点**（比如串一个会解码握手的中继），
> 证明测试确实把数据发出去了，再去改被测代码 —— 否则很容易照着"假失败"改半天。

## 兼容性

服务端 Mixin 目标只有两个，都很稳定：

- `net.minecraft.server.network.ServerHandshakePacketListenerImpl#handleIntention`
- `net.minecraft.network.Connection` 的 `address` 字段（access widener）

理论上同样的思路可以移植到其它版本，只需确认这两处的名字没变。

## License

[MIT](LICENSE)
