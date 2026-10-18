package com.example.myskinmod.mixin;

import com.example.myskinmod.util.SkinHelper;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Shadow public abstract GameProfile getProfile();

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        if (this.getProfile() != null) {
            Identifier customSkin = SkinHelper.getCustomSkin(this.getProfile().getId());
            if (customSkin != null) {
                cir.setReturnValue(customSkin);
            }
        }
    }
}