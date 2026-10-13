package com.example.myskinmod.util;

import net.minecraft.client.texture.NativeImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Safe skin loading. Everything is checked BEFORE the image is decoded (file size, real image header, resolution),
 * and the pixels are always decoded as RGBA. (A JPG decodes as 3-channel RGB, and reading/writing pixels of an RGB
 * NativeImage throws, which is what used to break Browse with some files.)
 */
public final class ImageLoader {
    public static final int MAX_DIM = 4096;
    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private ImageLoader() { }

    /** Carries translation keys so the caller can show a proper toast. */
    public static final class LoadError extends IOException {
        public final String titleKey, msgKey;
        public final Object[] args;

        public LoadError(String titleKey, String msgKey, Object... args) {
            super(msgKey);
            this.titleKey = titleKey;
            this.msgKey = msgKey;
            this.args = args;
        }

        public void toast() {
            ModToast.error(ModLang.t(titleKey), ModLang.t(msgKey, args));
        }
    }

    /** Shows the right toast for any failure while loading. */
    public static void toast(Throwable t) {
        if (t instanceof LoadError le) le.toast();
        else if (t instanceof OutOfMemoryError) ModToast.error(ModLang.t("changer.err.memory.title"), ModLang.t("changer.err.memory.msg"));
        else ModToast.error(ModLang.t("changer.err.load.title"), ModLang.t("changer.err.load.msg"));
    }

    public static NativeImage readFile(Path p) throws IOException {
        if (!Files.isRegularFile(p)) throw new LoadError("changer.err.notfound.title", "changer.err.notfound.msg");
        if (Files.size(p) > MAX_FILE_BYTES) throw new LoadError("changer.err.large.title", "changer.err.large.msg");
        return decode(Files.readAllBytes(p));
    }

    public static NativeImage readUrl(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Minecraft Skin Mod");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new LoadError("changer.err.download.title", "changer.err.http", code);
            if (conn.getContentLengthLong() > MAX_FILE_BYTES) throw new LoadError("changer.err.large.title", "changer.err.large.msg");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[16384];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_FILE_BYTES) throw new LoadError("changer.err.large.title", "changer.err.large.msg");
                    out.write(buf, 0, n);
                }
            }
            return decode(out.toByteArray());
        } catch (LoadError e) {
            throw e;
        } catch (IOException e) {
            throw new LoadError("changer.err.network.title", "changer.err.network.msg");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static NativeImage decode(byte[] data) throws IOException {
        ByteBuffer buf = MemoryUtil.memAlloc(data.length);
        try {
            buf.put(data).flip();

            int iw, ih;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
                if (!STBImage.stbi_info_from_memory(buf, w, h, c)) {
                    throw new LoadError("changer.err.invalid.title", "changer.err.invalid.msg");
                }
                iw = w.get(0);
                ih = h.get(0);
            }

            boolean modern = iw == ih && iw >= 64 && iw <= MAX_DIM;
            boolean classic = iw == ih * 2 && iw >= 64 && iw <= 1024;
            if (!modern && !classic) throw new LoadError("changer.err.res.title", "changer.err.res.msg", iw, ih);

            try {
                return NativeImage.read(NativeImage.Format.RGBA, buf);
            } catch (OutOfMemoryError oom) {
                throw new LoadError("changer.err.memory.title", "changer.err.memory.msg");
            }
        } finally {
            MemoryUtil.memFree(buf);
        }
    }
}
