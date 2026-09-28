package com.example.myskinmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {
    // ตัวอย่างการใช้ Accessor (ถ้าไม่ได้ใช้จริงให้ลบชื่อออกจาก JSON ง่ายกว่า)
    @Accessor("PLAYER_MODEL_PARTS")
    static TrackedData<Byte> getPlayerModelParts() {
        throw new AssertionError();
    }
}