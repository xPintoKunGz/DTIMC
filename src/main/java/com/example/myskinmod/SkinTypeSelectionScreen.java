package com.example.myskinmod;

import com.example.myskinmod.gui.SkinStorageScreen;
import com.example.myskinmod.storage.SkinStorageManager;
import com.example.myskinmod.storage.SkinStorageManager.SkinEntry;
import com.example.myskinmod.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Choose Classic / Slim, then apply the skin or save it to the Skin Storage Cloud. */
public class SkinTypeSelectionScreen extends Screen {

    private enum SaveMode { NEW, OVERWRITE }

    private String currentModelType = "default";

    private final Screen parent;
    private final Identifier skinId;
    private final String skinUrl;

    private NativeImage skinImage;
    private Identifier previewSkinId;
    private ClientPlayerEntity previewPlayer;
    private boolean loadStarted = false;

    private float modelYaw = 0f;
    private float modelPitch = 0f;
    private boolean rotating = false;

    private StyledButton classicButton, slimButton;
    private ModalOverlay overlay;

    // layout
    private int leftX, leftW, rightX, rightW, topY, panelH;

    public SkinTypeSelectionScreen(Screen parent, NativeImage skinImage, Identifier skinId, String skinUrl) {
        super(Text.literal("Select Skin Type"));
        this.parent = parent;
        this.skinImage = skinImage;
        this.skinId = skinId;
        this.skinUrl = skinUrl;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            try {
                net.minecraft.client.network.PlayerListEntry entry = client.getNetworkHandler() != null
                        ? client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) : null;
                String detected = entry != null ? entry.getModel() : client.player.getModel();
                this.currentModelType = (detected != null && detected.equalsIgnoreCase("slim")) ? "slim" : "default";
            } catch (Exception e) {
                this.currentModelType = "default";
            }
        }
    }

    private static String t(String key, Object... a) {
        return ModLang.t(key, a);
    }

    // ------------------------------------------------------------------ layout
    private void computeLayout() {
        int total = Math.min(width - 20, 440);
        panelH = Math.max(214, Math.min(226, height - 60));
        leftW = Math.max(150, (int) ((total - 8) * 0.52));
        rightW = Math.max(150, total - 8 - leftW);
        int startX = (width - (leftW + 8 + rightW)) / 2;
        leftX = startX;
        rightX = leftX + leftW + 8;
        topY = Math.max(24, (height - panelH) / 2 + 6);
    }

    @Override
    protected void init() {
        computeLayout();
        if (!loadStarted) {
            loadStarted = true;
            loadAndPreviewSkin(skinUrl);
        }

        this.addDrawableChild(new LinkButton(8, 8, LinkButton.Icon.BACK, t("common.back"), this::close));

        int bx = rightX + 10;
        int bw = rightW - 20;
        int saveY = topY + panelH - 10 - 22;
        int confirmY = saveY - 26;
        int modelY = confirmY - 32;
        int half = (bw - 4) / 2;

        classicButton = new StyledButton(bx, modelY, half, 20, Text.literal(t("type.classic")), b -> {
            currentModelType = "default";
            updateModelButtons();
        }, StyledButton.Style.NORMAL);
        slimButton = new StyledButton(bx + half + 4, modelY, bw - half - 4, 20, Text.literal(t("type.slim")), b -> {
            currentModelType = "slim";
            updateModelButtons();
        }, StyledButton.Style.NORMAL);
        this.addDrawableChild(classicButton);
        this.addDrawableChild(slimButton);

        this.addDrawableChild(new StyledButton(bx, confirmY, bw, 22, Text.literal(t("type.confirm")),
                b -> applySkin(currentModelType), StyledButton.Style.PRIMARY));
        this.addDrawableChild(new StyledButton(bx, saveY, bw, 22, Text.literal(t("type.save")),
                b -> onSaveToStorage(), StyledButton.Style.NORMAL));

        updateModelButtons();
    }

    private void updateModelButtons() {
        if (classicButton == null || slimButton == null) return;
        classicButton.setSelectedState(currentModelType.equals("default"));
        slimButton.setSelectedState(currentModelType.equals("slim"));
    }

    // ------------------------------------------------------------------ transparency fix (unchanged)
    private void fixSkinTransparency(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int scale = width / 64;

        int[][] baseRegions = {
                {0, 0, 32, 16}, {0, 16, 16, 32}, {16, 16, 40, 32},
                {40, 16, 56, 32}, {16, 48, 32, 64}, {32, 48, 48, 64}
        };

        for (int[] region : baseRegions) {
            int xStart = region[0] * scale, yStart = region[1] * scale;
            int xEnd = region[2] * scale, yEnd = region[3] * scale;
            if (yStart >= height) continue;

            for (int x = xStart; x < xEnd && x < width; x++) {
                for (int y = yStart; y < yEnd && y < height; y++) {
                    int color = image.getColor(x, y);
                    int alpha = (color >> 24) & 0xFF;
                    if (alpha < 255) {
                        if (alpha == 0) image.setColor(x, y, 0xFF000000);
                        else image.setColor(x, y, color | 0xFF000000);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ preview loading
    private void loadAndPreviewSkin(String url) {
        MinecraftClient client = MinecraftClient.getInstance();

        new Thread(() -> {
            try {
                NativeImage loadedImage;

                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "Minecraft-Skin-Mod");
                    try (InputStream in = conn.getInputStream()) {
                        loadedImage = NativeImage.read(in);
                    }
                } else {
                    if (url == null || url.isEmpty()) {
                        ModToast.error(t("type.err.load.title"), t("type.err.empty"));
                        return;
                    }
                    try (InputStream in = Files.newInputStream(Paths.get(url))) {
                        loadedImage = NativeImage.read(in);
                    }
                }

                int w = loadedImage.getWidth();
                int h = loadedImage.getHeight();
                if (w < 64 || h < 64 || w > 8192 || h > 8192) {
                    loadedImage.close();
                    ModToast.error(t("type.err.size.title"), t("type.err.size.msg"));
                    return;
                }

                fixSkinTransparency(loadedImage);

                client.execute(() -> {
                    try {
                        if (this.skinImage != null && this.skinImage != loadedImage) this.skinImage.close();
                        this.skinImage = loadedImage;

                        NativeImageBackedTexture texture = new NativeImageBackedTexture(loadedImage);
                        previewSkinId = client.getTextureManager().registerDynamicTexture("skin_preview", texture);

                        previewPlayer = PreviewPlayerFactory.create(client, () -> previewSkinId, () -> currentModelType);
                        updateModelButtons();
                        ModToast.show(ModToast.Type.INFO, t("type.toast.loaded.title"), t("type.toast.loaded.msg"),
                                previewSkinId, loadedImage.getWidth(), loadedImage.getHeight());
                    } catch (Exception e) {
                        ModToast.error(t("type.err.preview.title"), t("type.err.preview.msg"));
                    }
                });

            } catch (Exception e) {
                ModToast.error(t("type.err.download.title"), t("type.err.download.msg"));
            }
        }).start();
    }

    // ------------------------------------------------------------------ apply
    private void applySkin(String modelType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || skinImage == null) return;
        try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(skinImage);
            Identifier newSkinId = client.getTextureManager().registerDynamicTexture(
                    "custom_skin_" + client.player.getUuid(), texture);
            SkinHelper.applySkin(client.player, newSkinId, modelType, skinImage.getWidth(), skinImage.getHeight());

            Path tempPath = null;
            try {
                tempPath = Files.createTempFile("skin_send", ".png");
                skinImage.writeTo(tempPath);
                SkinNetworkHandler.sendSkinChunks(Files.readAllBytes(tempPath));
                ModToast.show(ModToast.Type.SUCCESS, t("type.toast.applied.title"), t("type.toast.applied.msg"),
                        previewSkinId, skinImage.getWidth(), skinImage.getHeight());
            } catch (Exception e) {
                System.out.println("[MySkinMod] Network Error: " + e.getMessage());
                ModToast.error(t("type.err.network.title"), t("type.err.network.msg"));
            } finally {
                if (tempPath != null) Files.deleteIfExists(tempPath);
            }
            client.setScreen(null);
        } catch (Exception e) {
            ModToast.error(t("type.err.apply.title"), t("type.err.apply.msg"));
        }
    }

    // ------------------------------------------------------------------ save to storage (+ duplicate check)
    private void onSaveToStorage() {
        if (skinImage == null) {
            ModToast.warning(t("type.err.notready.title"), t("type.err.notready.msg"));
            return;
        }
        List<SkinEntry> dups = SkinStorageManager.findDuplicates(skinImage);
        if (dups.isEmpty()) {
            doSave(SaveMode.NEW);
            return;
        }

        SkinEntry first = dups.get(0);
        List<String> lines = new ArrayList<>();
        lines.add(t("type.dup.line1", first.name));
        lines.add(t("type.dup.line2", first.createdDate == null ? "?" : first.createdDate));
        if (dups.size() > 1) lines.add(t("type.dup.more", dups.size() - 1));

        overlay = new ModalOverlay(t("type.dup.title"), lines, 0xFFFFC857)
                .button(t("type.dup.overwrite"), 0xFFB7791F, 0xFFD99A2B, false, () -> doSave(SaveMode.OVERWRITE))
                .button(t("type.dup.copy"), 0xFF18785A, 0xFF269B75, true, () -> doSave(SaveMode.NEW))
                .button(t("type.dup.cancel"), 0xFF454A56, 0xFF646B7A, false, () -> { })
                .onEscape(() -> { });
    }

    private void doSave(SaveMode mode) {
        SkinEntry saved;
        List<SkinEntry> dups = skinImage == null ? List.of() : SkinStorageManager.findDuplicates(skinImage);
        if (mode == SaveMode.OVERWRITE && !dups.isEmpty()) {
            saved = SkinStorageManager.replaceSkin(dups.get(0).id, currentModelType, skinImage);
        } else {
            saved = SkinStorageManager.addSkin(null, currentModelType, skinImage);
        }

        if (saved == null) {
            ModToast.error(t("type.err.save.title"), t("type.err.save.msg"));
            return;
        }
        ModToast.show(ModToast.Type.SUCCESS,
                mode == SaveMode.OVERWRITE ? t("type.toast.overwritten.title") : t("type.toast.saved.title"),
                t("type.toast.saved.msg", saved.name), previewSkinId,
                skinImage.getWidth(), skinImage.getHeight());

        MinecraftClient client = MinecraftClient.getInstance();
        Screen target = (parent instanceof SkinChangerScreen sc) ? sc.getReturnScreen() : null;
        if (target == null) target = new SkinStorageScreen(null);
        client.setScreen(target);
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        computeLayout();
        this.renderBackground(c);
        boolean modal = overlay != null && !overlay.isClosed();
        int mx = modal ? -9999 : mouseX, my = modal ? -9999 : mouseY;
        TextRenderer tr = this.textRenderer;

        c.drawCenteredTextWithShadow(tr, t("type.title"), width / 2, Math.max(6, topY - 16), 0xFFFFFFFF);

        // ---------- left: 3D preview ----------
        UiKit.panel(c, leftX, topY, leftW, panelH);
        c.drawText(tr, t("type.preview"), leftX + 10, topY + 9, UiKit.GOLD, false);
        UiKit.inset(c, leftX + 6, topY + 28, leftW - 12, panelH - 36, 0x22FFFFFF);

        if (!modal) render3DPreview(c, leftX + leftW / 2, topY + panelH - 30);
        if (previewPlayer == null) {
            c.drawCenteredTextWithShadow(tr, t("type.loading"), leftX + leftW / 2, topY + panelH / 2, 0xFFAAAAAA);
        } else {
            c.drawCenteredTextWithShadow(tr, t("type.drag_rotate"), leftX + leftW / 2, topY + panelH - 20, 0xFF555566);
        }

        // ---------- right: details + actions ----------
        UiKit.panel(c, rightX, topY, rightW, panelH);
        c.drawText(tr, t("type.details"), rightX + 10, topY + 9, UiKit.GOLD, false);

        int bx = rightX + 10;
        int thumb = 36;
        int ty = topY + 30;
        UiKit.inset(c, bx, ty, thumb, thumb, 0x55FFFFFF);
        UiKit.inset(c, bx + thumb + 8, ty, thumb, thumb, 0x55FFFFFF);
        if (previewSkinId != null && skinImage != null) {
            int iw = skinImage.getWidth(), ih = skinImage.getHeight();
            float k = iw / 64.0f;
            int region = Math.max(1, Math.round(8 * k));
            c.drawTexture(previewSkinId, bx + 2, ty + 2, thumb - 4, thumb - 4, 8.0f * k, 8.0f * k, region, region, iw, ih);
            c.drawTexture(previewSkinId, bx + 2, ty + 2, thumb - 4, thumb - 4, 40.0f * k, 8.0f * k, region, region, iw, ih);
            c.drawTexture(previewSkinId, bx + thumb + 10, ty + 2, thumb - 4, thumb - 4, 0.0f, 0.0f, iw, ih, iw, ih);
        }

        int infoX = bx + thumb * 2 + 16;
        int infoW = rightX + rightW - 10 - infoX;
        if (infoW > 30 && skinImage != null) {
            c.drawText(tr, UiKit.fit(tr, skinImage.getWidth() + "x" + skinImage.getHeight(), infoW), infoX, ty + 6, 0xFFE0E0E8, false);
            c.drawText(tr, UiKit.fit(tr, currentModelType.toUpperCase(), infoW), infoX, ty + 20, UiKit.ACCENT, false);
        }

        // source line (path / URL is masked in Streamer Mode)
        int sy = topY + 76;
        boolean isUrl = skinUrl != null && skinUrl.startsWith("http");
        String srcLabel;
        if (skinUrl == null || skinUrl.isEmpty()) srcLabel = t("type.source.none");
        else if (isUrl) srcLabel = t("type.source.url");
        else srcLabel = Files.exists(Paths.get(skinUrl)) ? t("type.source.file") : t("type.source.invalid");
        c.drawText(tr, srcLabel, bx, sy, 0xFF8A8FA3, false);
        if (skinUrl != null && !skinUrl.isEmpty()) {
            int w = rightW - 20;
            UiKit.inset(c, bx, sy + 11, w, 14, 0x33FFFFFF);
            Privacy.draw(c, tr, skinUrl, bx + 4, sy + 14, 0xFF55FF55, w - 8);
        }

        // model selector caption
        c.drawText(tr, t("type.model_type"), bx, classicButton.getY() - 11, 0xFF8A8FA3, false);

        super.render(c, mx, my, delta);

        Privacy.badge(c, tr, height);
        if (modal) overlay.render(c, tr, width, height, mouseX, mouseY);
        else if (overlay != null) overlay = null;
    }

    private void render3DPreview(DrawContext c, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (previewPlayer == null || previewSkinId == null) return;

        Quaternionf rotation = new Quaternionf()
                .rotateX((float) Math.toRadians(180f + modelPitch))
                .rotateY((float) Math.toRadians(modelYaw));

        previewPlayer.age = client.world != null ? (int) (client.world.getTime() % 10000L) : previewPlayer.age + 1;
        previewPlayer.headYaw = (float) Math.sin(System.currentTimeMillis() / 1000.0) * 5f;
        previewPlayer.bodyYaw = 0f;
        previewPlayer.setYaw(0f);
        previewPlayer.setPitch(0f);
        previewPlayer.limbAnimator.setSpeed(0.0f);

        int size = Math.max(30, (int) ((panelH - 64) / 1.9));
        InventoryScreen.drawEntity(c, x, y, size, rotation, null, previewPlayer);
    }

    // ------------------------------------------------------------------ input
    private boolean overlayActive() {
        if (overlay != null && overlay.isClosed()) overlay = null;
        return overlay != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overlayActive()) {
            overlay.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (button == 0 && UiKit.in(mouseX, mouseY, leftX, topY, leftW, panelH)) rotating = true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (overlayActive()) return true;
        if (button == 0 && rotating) {
            modelYaw += (float) deltaX * 1.5f;
            modelPitch = Math.max(-45f, Math.min(45f, modelPitch + (float) deltaY * 1.5f));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) rotating = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlayActive()) return overlay.keyPressed(keyCode);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Back arrow and Esc: release the temporary skin and return to the import page. */
    @Override
    public void close() {
        if (skinImage != null) {
            skinImage.close();
            skinImage = null;
        }
        if (skinId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(skinId);
        MinecraftClient.getInstance().setScreen(parent);
    }
}