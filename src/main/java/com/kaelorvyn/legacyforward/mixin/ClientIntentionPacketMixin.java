package com.kaelorvyn.legacyforward.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 原版握手包的 serverAddress 只允许 <b>255 字符</b>：
 * <pre>private ClientIntentionPacket(FriendlyByteBuf buf) {
 *     this(buf.readVarInt(), buf.readUtf(255), buf.readUnsignedShort(), ...);
 * }</pre>
 *
 * <p>而 legacy 转发数据长这样：{@code host\0ip\0uuid\0propertiesJson} —— 其中 properties 里
 * 带着皮肤（签名后的 base64，通常一两千字符）。一旦超过 255，解码握手时就直接抛异常断连，
 * 连日志都不会留，表现出来就是<b>"代理连不上后端"</b>，而 Velocity 只会给一句
 * "The connection to the remote server was unexpectedly closed"。
 *
 * <p>Spigot / Paper 也做了同样的放宽，否则 BungeeCord 转发根本带不了皮肤。这里对齐即可。
 * 帧长本身由 netty 限制兜着，放到 32767 足够用也不至于失控。
 */
@Mixin(ClientIntentionPacket.class)
public abstract class ClientIntentionPacketMixin {

    @ModifyArg(
            method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;readUtf(I)Ljava/lang/String;"),
            index = 0)
    private static int legacyforward$widenHostnameLimit(int original) {
        return 32767;
    }
}
