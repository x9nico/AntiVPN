package me.egg82.antivpn.messaging.packets;

import io.netty.buffer.ByteBuf;
import java.io.Serializable;
import org.checkerframework.checker.nullness.qual.NonNull;

public interface Packet extends Serializable {
    byte VERSION = 0x01;

    byte getPacketId();

    void read(@NonNull ByteBuf buffer);
    void write(@NonNull ByteBuf buffer);
}
