
# Devathip Interface Mod (Skin Storage & Changer)

มอด Minecraft สำหรับสายแฟชั่นและการปรับแต่งตัวละคร ช่วยให้คุณจัดการ เปลี่ยนสกินความละเอียดสูง (HD Skins)และจัดเก็บคลังสกินได้อย่างง่ายดายโดยไม่ต้องพึ่งพาเว็บภายนอก!


## 📋 Features

- ⭐ Fabric 1.20.1 Support: รองรับการใช้งานบนตัวโหลด Fabric 1.20.1 โดยเฉพาะ (แผนสำหรับ Forge และ NeoForge จะตามมาในอนาคต)
- ⭐ Easy-to-use UI: ดีไซน์หน้าต่างอินเทอร์เฟซที่สะอาดตา ใช้งานง่าย ไม่ซับซ้อน คลิกเพียงไม่กี่คลิกก็เปลี่ยนสกินได้ทันที
- ⭐ High-Resolution Skins (HD): รองรับสกินความละเอียดสูงตั้งแต่ 64x64 ถึง 4096x4096 และขนาด 64x32 ถึง 1024x512
- ⭐ Flexible Import Options: สามารถลากไฟล์สกินมาวางในเกมได้ทันที (Drag & Drop) หรือดึงสกินผ่าน URL จากเว็บไซต์/โปรแกรมอื่น (รองรับเฉพาะไฟล์ .png, .jpg, .jpeg ขนาดไม่เกิน 10MB)
- ⭐ Model Type Customization: สามารถเลือกปรับรูปแบบโมเดลสกินได้ทั้งแบบ Classic และ Slim
- ⭐ Skin Storage & Categories: มีระบบคลังเก็บสกินส่วนตัว บันทึกสกินเก็บไว้เลือกหยิบมาใช้งานได้สะดวก พร้อมระบบจัดหมวดหมู่ (Categories) และปุ่มกดชื่นชอบ (Favorites ❤️) ตามใจชอบ!


## 🚧 Upcoming Features

- ❌ Mod Integration: รองรับการทำงานร่วมกับมอดอื่นๆ เช่น Figma, Simple Voice Chat, Plasmo Voice และ Emotechat (เร็วๆ นี้)
- ❌ Multiplayer Sync: ระบบแสดงผลแบบ Client-side ที่ให้ผู้เล่นคนอื่นสามารถมองเห็นสกินพิเศษของคุณได้ เพียงแค่ลงมอดนี้เช่นกัน (กำลังพัฒนา)


## 📦 Installation

1. ติดตั้ง [Fabric Loader](https://fabricmc.net/) บน Minecraft เวอร์ชัน **1.20.1**
2. ดาวน์โหลดไฟล์ `.jar` ของมอดนี้จากหน้า [Releases](../../releases)
3. นำไฟล์ `.jar` ไปใส่ไว้ในโฟลเดอร์ `mods` ของเกม Minecraft (`.minecraft/mods`)
4. เข้าเกมแล้วใช้งานได้ทันที!
    
## 💡 Usage

* กดปุ่ม `K` เมนูหลักของมอดเพื่อเปิดหน้าต่าง Skin Changer หรือเข้าสู่ **Skin Storage Cloud** เพื่อจัดการสกินที่คุณบันทึกไว้ในคลัง
* สามารถสร้างหมวดหมู่ (Categories) ลากย้ายสกิน จัดการเปลี่ยนชื่อ และกดปุ่ม Favorite (หัวใจ ❤️) เพื่อเก็บบันทึกสกินโปรดได้ทันที


## 💻 Development

หากคุณต้องการโคลนโปรเจกต์นี้ไปศึกษา แก้ไข หรือคอมไพล์ไฟล์ `.jar` ด้วยตัวเอง สามารถทำตามขั้นตอนนี้ได้ครับ:

1. Clone repository นี้ลงในเครื่องของคุณ:
   ```bash
   git clone https://github.com/xPintoKunGz/DTIMC.git
   ```
2. เปิดโปรเจกต์ด้วย IDE เช่น **IntelliJ IDEA**
3. รันคำสั่ง Gradle เพื่อเตรียมความพร้อมของโปรเจกต์:
   ```bash
   ./gradlew build
   ```
4. ทดสอบรันเกมผ่าน Client ของ Gradle:
   ```bash
   ./gradlew runClient
   ```

## 📄 License

โปรเจกต์นี้เผยแพร่ภายใต้สัญญาอนุญาตแบบ **MIT License** สามารถศึกษาข้อมูลเพิ่มเติมได้ที่ไฟล์ [MIT](https://choosealicense.com/licenses/mit/)
