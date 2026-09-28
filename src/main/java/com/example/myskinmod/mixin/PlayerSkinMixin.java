package com.example.myskinmod.mixin;

import com.example.myskinmod.util.SkinHelper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerSkinMixin {

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        Identifier customSkin = SkinHelper.getPlayerSkinTexture(player.getUuid());

        if (customSkin != null) {
            cir.setReturnValue(customSkin);
        }
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void onGetModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        String modelType = SkinHelper.getPlayerModelType(player.getUuid());

        if (modelType != null && !modelType.trim().isEmpty()) {
            cir.setReturnValue(modelType);
        }
    }

    // วิธีแก้ไขเพื่อ bypass การตรวจสอบขนาดสกิน
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void bypassSkinSizeCheck(CallbackInfoReturnable<Identifier> cir) { // แก้ไขจาก Boolean เป็น Identifier เพื่อให้ตรงกับ Method
        Identifier customSkin = SkinHelper.getPlayerSkinTexture(((AbstractClientPlayerEntity)(Object)this).getUuid());
        if (customSkin != null) {
            cir.setReturnValue(customSkin);
            cir.cancel();
        }
    }
}