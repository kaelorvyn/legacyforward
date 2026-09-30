package com.kaelorvyn.legacyforward.mixin;

import com.kaelorvyn.legacyforward.LegacyForward;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

/**
 * 把握手包里的转发数据落成这条连接的真实来源地址。
 *
 * <p>做法与 Spigot/Paper 的 {@code bungeecord: true} 完全一样：改写 {@code Connection} 的地址字段。
 * 只有改字段才管用——服务端打印玩家地址用的 {@code Connection.getLoggableAddress()}
 * 是直接读那个字段的，并不经过 {@code getRemoteAddress()}。
 */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class ServerHandshakePacketListenerImplMixin {

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void legacyforward$useForwardedAddress(ClientIntentionPacket packet, CallbackInfo ci) {
        // 取端口要趁早：下面一改写，再问就拿到假地址了
        int port = ((InetSocketAddress) this.connection.getRemoteAddress()).getPort();
        InetSocketAddress real = LegacyForward.parse(packet.hostName(), port);
        if (real != null) {
            this.connection.address = real;
        }
    }
}
