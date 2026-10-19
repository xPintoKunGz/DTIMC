
# 👔 Devathip Interface Mod (Skin Storage & Changer)

A comprehensive Minecraft modification designed for character customization. Manage, apply high-resolution (HD) skins, and organize your personal skin cloud storage effortlessly—all within the game interface without needing third-party websites.

---

## 📋 Key Features

- **⭐ Fabric 1.20.1 Native Support:** Exclusively engineered for the Fabric Loader 1.20.1 environment *(Forge & NeoForge compatibility planned for future releases)*.
- **⭐ Client-Side Multiplayer Sync:** Seamless client-side synchronization—other players can see your custom skin instantly, as long as they have the mod installed!
- **⭐ Intuitive & Minimalist UI:** Clean, responsive GUI designed for seamless skin swapping in just a few clicks.
- **⭐ High-Resolution (HD) Skins:** Native support for modern resolution formats ranging from **64x64 up to 4096x4096**, alongside classic ratios from **64x32 to 1024x512**.
- **⭐ Flexible Import Options:** Drag & drop image files directly into the client window or pull textures from online direct links *(Supports `.png`, `.jpg`, and `.jpeg` formats up to 10 MB)*.
- **⭐ Model Type Customization:** Easily toggle between **Classic (Default)** and **Slim (Alex)** arm model variants.
- **⭐ Cloud Skin Storage & Categories:** Store your skin library locally, organize textures into custom folders/categories, and mark your top picks using the **Favorites (❤️)** feature.

---

## 🚧 Roadmap  & Upcoming Features

- ✅ **Figura Compatibility:** Seamless interoperability with Figura 3D avatar mods, allowing custom model rendering to take priority while maintaining DTIMC's storage and network sync.
- ❌ **Mod Ecosystem Integration:** Planned compatibility for *Simple Voice Chat*, *Plasmo Voice*, and *Emotechat*.

---

## 📦 Installation

Since **MySkinMod** functions as a **Client-Side** multiplayer mod, installation is straightforward and requires no server-side modifications!

### 📋 Prerequisites
1. **Minecraft Java Edition** version `1.20.1`.
2. [Fabric Loader](https://fabricmc.net/) version `0.16.10` or higher.
3. **Fabric API** installed in your `.minecraft/mods` directory.

---

### 🚀 Step-by-Step Guide

1. **Download the Mod**:
   - Get the latest release `.jar` file from the [Releases](https://github.com/xPintoKunGz/DTIMC/releases) page.

2. **Locate your Mods Folder**:
   - **Windows**: Press `Win + R`, type `%appdata%\.minecraft\mods`, and press **Enter**.
   - **macOS**: `~/Library/Application Support/minecraft/mods`
   - **Linux**: `~/.minecraft/mods`

3. **Install**:
   - Place the downloaded `DTIMC-1.20.1-x.x.x.jar` file and `fabric-api.jar` directly into your `mods` folder.

4. **Launch & Play**:
   - Start Minecraft using the **Fabric Loader 1.20.1** profile.

---

> 💡 **Multiplayer Note**: 
> This mod is **Client-Side only**. You can freely join any vanilla, LAN, or public server! 
> To see other players' custom skins (and for them to see yours), **both players simply need to have the mod installed in their client**. No server-side plugins or server mod installations are required!

---
    
## 💡 Usage Guide

- Press **`R`** in-game to open the primary **Skin Changer** UI or navigate directly to the **Skin Storage Cloud**.
- Organize your wardrobe by creating custom categories, reordering profiles inline, renaming skins, and pinning favorites.

---

## 💻 Building from Source

To set up the development environment, modify the codebase, or compile the build manually:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/xPintoKunGz/DTIMC.git
   ```

2. Navigate to the project directory and build the mod:

   ```bash
   cd DTIMC
   ./gradlew build
   ```

3. Launch the Minecraft test client:

   ```bash
   ./gradlew runClient
   ```

---

## 📄 License

Distributed under the MIT License. See the [LICENSE](https://choosealicense.com/licenses/mit/) file for more information.

---

---

## 🎤 Figura Lip-Sync (Plasmo Voice) — optional

If **Figura** and **Plasmo Voice (2.1.1 or newer)** are both installed, DTIMC listens to your microphone and
exposes the loudness to Figura avatar scripts, so your avatar's mouth can move while you talk. Without those
mods this feature does nothing.

- **Lua API** (identical to FigExtra's, so existing scripts keep working):
  `plasmovoice.getVoiceLevel(uuid)` → `0..1`, `plasmovoice.isMuted(uuid)`, `plasmovoice.getVersion()`
- **Lua event** fired for every voice packet you send:
  `events["FIGEXTRA.PLASMOVOICE_PLAYER_SPEAK"]:register(function(activationId, distance) ... end)`
- **Ready-made avatar script:** [`examples/figura-lipsync/script.lua`](examples/figura-lipsync/script.lua)
  (set the path to your mouth part, then tune the thresholds with `DEBUG = true`).
- Other players see your mouth move through a Figura ping — they only need Figura, not Plasmo Voice or DTIMC.
- Don't install the original **FigExtra** alongside DTIMC; both register the same `plasmovoice` global.

Credits: adapted from [FigExtra](https://github.com/Furrc/FigExtra) by Furrc (MIT) — see `THIRD_PARTY_NOTICES.md`.
