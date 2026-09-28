package com.example.myskinmod.networking;

import net.minecraft.network.PacketByteBuf;
import java.util.UUID;

public record SkinPacket(UUID senderUuid, int chunkIndex, int totalChunks, byte[] data) {

    public void write(PacketByteBuf buf) {
        buf.writeUuid(senderUuid);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
        buf.writeInt(data.length);
        buf.writeByteArray(data);
    }

    public static SkinPacket read(PacketByteBuf buf) {
        UUID uuid = buf.readUuid();
        int index = buf.readInt();
        int total = buf.readInt();
        int length = buf.readInt();
        byte[] data = new byte[length];
        buf.readBytes(data); // อ่านข้อมูลแบบดิบ
        return new SkinPacket(uuid, index, total, data);
    }
}