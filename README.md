
# Devathip Interface Mod (Skin Storage & Changer)

A comprehensive Minecraft modification designed for character customization. Manage, apply high-resolution (HD) skins, and organize your personal skin cloud storage effortlessly—all within the game interface without needing third-party websites.


## 📋 Key Features

- **⭐ Fabric 1.20.1 Native Support:** Exclusively engineered for the Fabric Loader 1.20.1 environment *(Forge & NeoForge compatibility planned for future releases)*.
- **⭐ Intuitive & Minimalist UI:** Clean, responsive GUI designed for seamless skin swapping in just a few clicks.
- **⭐ High-Resolution (HD) Skins:** Native support for modern resolution formats ranging from **64x64 up to 4096x4096**, alongside classic ratios from **64x32 to 1024x512**.
- **⭐ Flexible Import Options:** Drag & drop image files directly into the client window or pull textures from online direct links *(Supports `.png`, `.jpg`, and `.jpeg` formats up to 10 MB)*.
- **⭐ Model Type Customization:** Easily toggle between **Classic (Default)** and **Slim (Alex)** arm model variants.
- **⭐ Cloud Skin Storage & Categories:** Store your skin library locally, organize textures into custom folders/categories, and mark your top picks using the **Favorites (❤️)** feature.


## 🚧 Roadmap  & Upcoming Features

- ❌ **Mod Ecosystem Integration:** Planned compatibility for popular mods, including *Figma*, *Simple Voice Chat*, *Plasmo Voice*, and *Emotechat*.
- ❌ **Client-Side Multiplayer Sync:** A dedicated client-side network renderer that allows other players using the mod to see your custom HD skins in multiplayer environments *(In Active Development)*.


## 📦 Installation

1. Download and install [Fabric Loader](https://fabricmc.net/) for **Minecraft 1.20.1**.
2. Download the latest `.jar` build from the [Releases](../../releases) tab.
3. Place the downloaded `.jar` file into your Minecraft instance's `mods` directory (`.minecraft/mods`).
4. Launch the game and enjoy!
    
## 💡 Usage Guide

- Press **`K`** in-game to open the primary **Skin Changer** UI or navigate directly to the **Skin Storage Cloud**.
- Organize your wardrobe by creating custom categories, reordering profiles inline, renaming skins, and pinning favorites.


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



## 📄 License

Distributed under the MIT License. See the [LICENSE](https://choosealicense.com/licenses/mit/) file for more information.
