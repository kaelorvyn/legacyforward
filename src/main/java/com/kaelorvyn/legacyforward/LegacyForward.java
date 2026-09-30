package com.kaelorvyn.legacyforward;

import java.net.InetSocketAddress;

/**
 * 解析 Velocity / BungeeCord 的 legacy（BungeeCord 式）转发数据。
 *
 * <p>线上格式，读自 Velocity 的
 * {@code com.velocitypowered.proxy.connection.PlayerDataForwarding#createLegacyForwardingAddress}：
 * <pre>host \0 ip \0 uuid(无横线) \0 propertiesJson</pre>
 *
 * <p>只取 IP：代理是 {@code online-mode=false}，它生成的离线 UUID 与本服务端
 * {@code UUIDUtil.createOfflinePlayerUUID} 算出来的是同一个
 * （两边都是 {@code nameUUIDFromBytes("OfflinePlayer:" + name)}），所以 UUID 天然一致，
 * 玩家数据不会错位。皮肤属性这里也是空的（离线模式没有 Mojang 签名），转发过来没有意义。
 *
 * <p>注意：这段数据是握手包里的明文，装了这个模组就等于无条件信任它——
 * 所以本服务端只应监听 127.0.0.1 或由代理侧防火墙保护，别把端口裸奔在公网上。
 */
public final class LegacyForward {

    private static final String SEP = "\u0000";

    private LegacyForward() {
    }

    /**
     * @param hostname     握手包里的 server address
     * @param fallbackPort 沿用客户端真实来源端口（转发数据里只有 IP，没有端口）
     * @return 转发来的真实地址；不是经代理转发来的则返回 {@code null}
     */
    public static InetSocketAddress parse(String hostname, int fallbackPort) {
        if (hostname == null || hostname.indexOf(SEP) < 0) {
            return null;
        }
        String[] parts = hostname.split(SEP, -1);
        if (parts.length < 4) {
            return null;
        }
        String ip = parts[1];
        // 只收 IP 字面量。BungeeCord/Velocity 转发的本来就是数字 IP；
        // 拦掉其它形状是为了不让客户端塞进来的怪字符串触发 DNS 解析。
        if (ip.isEmpty() || !ip.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        return new InetSocketAddress(ip, fallbackPort);
    }
}
