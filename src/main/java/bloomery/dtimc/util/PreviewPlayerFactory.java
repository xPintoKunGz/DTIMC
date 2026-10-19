package bloomery.dtimc.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.function.Supplier;

/**
 * Dummy player used by every 3D preview. It ignores the REAL player's state completely:
 * always standing, empty hands, no armor/elytra/cape, never spectator/invisible/swimming/crawling/sleeping/on fire,
 * and every skin layer (hat, jacket, sleeves, pants) visible.
 */
public final class PreviewPlayerFactory {
    private PreviewPlayerFactory() { }

    public static ClientPlayerEntity create(MinecraftClient client, Supplier<Identifier> skin, Supplier<String> model) {
        if (client.world == null || client.player == null) return null;

        ClientPlayerEntity p = new ClientPlayerEntity(
                client, client.world, client.player.networkHandler,
                client.player.getStatHandler(), client.player.getRecipeBook(),
                false, false
        ) {
            // --- items ---
            @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
            @Override public Iterable<ItemStack> getArmorItems() { return Collections.emptyList(); }
            @Override public ItemStack getMainHandStack() { return ItemStack.EMPTY; }
            @Override public ItemStack getOffHandStack() { return ItemStack.EMPTY; }
            @Override public ItemStack getStackInHand(Hand hand) { return ItemStack.EMPTY; }
            @Override public boolean isUsingItem() { return false; }
            @Override public boolean isUsingRiptide() { return false; }

            // --- pose / actions ---
            @Override public EntityPose getPose() { return EntityPose.STANDING; }
            @Override public boolean isSneaking() { return false; }
            @Override public boolean isInSneakingPose() { return false; }
            @Override public boolean isSprinting() { return false; }
            @Override public boolean isRiding() { return false; }
            @Override public boolean isFallFlying() { return false; }
            @Override public boolean isSwimming() { return false; }
            @Override public boolean isInSwimmingPose() { return false; }
            @Override public boolean isCrawling() { return false; }
            @Override public boolean isSleeping() { return false; }
            @Override public float getLeaningPitch(float tickDelta) { return 0.0f; }
            @Override public float getHandSwingProgress(float tickDelta) { return 0.0f; }

            // --- game mode / status effects / fire ---
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
            @Override public boolean isInvisible() { return false; }
            @Override public boolean isInvisibleTo(PlayerEntity viewer) { return false; }
            @Override public boolean isOnFire() { return false; }
            @Override public boolean doesRenderOnFire() { return false; }

            // --- skin ---
            @Override public Identifier getSkinTexture() { return skin.get(); }
            @Override public String getModel() { return model.get(); }
            @Override public boolean isPartVisible(PlayerModelPart part) { return part != PlayerModelPart.CAPE; }
            @Override public Identifier getCapeTexture() { return null; }
            @Override public Identifier getElytraTexture() { return null; }

            // --- name tag ---
            @Override public boolean shouldRenderName() { return false; }
            @Override public boolean hasCustomName() { return false; }
            @Override public boolean isCustomNameVisible() { return false; }
            @Override public Text getName() { return Text.empty(); }
            @Override public Text getDisplayName() { return Text.empty(); }
        };

        p.setPose(EntityPose.STANDING);
        p.handSwinging = false;
        p.handSwingProgress = 0.0f;
        p.limbAnimator.setSpeed(0.0f);
        return p;
    }
}
