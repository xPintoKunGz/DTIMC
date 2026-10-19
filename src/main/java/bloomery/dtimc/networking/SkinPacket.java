package bloomery.dtimc.networking;

import net.minecraft.network.PacketByteBuf;
import java.util.UUID;

public class SkinPacket {
    public final UUID uuid;
    public final int chunkIndex;
    public final int totalChunks;
    public final byte[] data;
    public final String modelType;

    public SkinPacket(UUID uuid, int chunkIndex, int totalChunks, byte[] data, String modelType) {
        this.uuid = uuid;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
        this.modelType = (modelType == null || modelType.isEmpty()) ? "default" : modelType;
    }

    public SkinPacket(UUID uuid, int chunkIndex, int totalChunks, byte[] data) {
        this(uuid, chunkIndex, totalChunks, data, "default");
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(this.uuid);
        buf.writeInt(this.chunkIndex);
        buf.writeInt(this.totalChunks);
        buf.writeByteArray(this.data);
        buf.writeString(this.modelType);
    }

    public static SkinPacket read(PacketByteBuf buf) {
        UUID readUuid = buf.readUuid();
        int readChunkIndex = buf.readInt();
        int readTotalChunks = buf.readInt();
        byte[] readData = buf.readByteArray();
        String readModelType = buf.readableBytes() > 0 ? buf.readString() : "default";
        return new SkinPacket(readUuid, readChunkIndex, readTotalChunks, readData, readModelType);
    }

    // Getters & Aliases
    public UUID getUuid() { return this.uuid; }
    public int getChunkIndex() { return this.chunkIndex; }
    public int getTotalChunks() { return this.totalChunks; }
    public byte[] getData() { return this.data; }
    public String getModelType() { return this.modelType; }

    public UUID senderUuid() { return this.uuid; }
    public int chunkIndex() { return this.chunkIndex; }

    public int totalChunks() {
        return this.totalChunks;
    }

    public byte[] data() { return this.data; }
    public String modelType() { return this.modelType; }
}