# Nexus Deep Recovery AI (Android)

Kotlin mein bani native Android app. Videos dhoondti, dikhati aur mehfooz copy karti hai. Mukammal offline, internet permission nahi hai, aur original files ko kabhi nahi chhedti.

## App ke 4 hisse

| Hissa | Kya karta hai |
|---|---|
| 🔍 **Poore phone ka Deep Scan** | Internal storage aur SD card ki har file aur har folder padhta hai: chhupe folder, `.trashed` files, WhatsApp/Telegram/Instagram ki media aur cache, `.thumbnails`, aur woh videos jin ka naam ya extension badal gaya ho (file signature: MP4/3GP/MOV/MKV/AVI). "All files access" maangta hai. |
| 🗑 **Recently Deleted / Lost Videos** | Android ki media list se har video, Android 11+ par system trash samait. Trash wali video ko **Restore** button se wapas gallery mein laya ja sakta hai. |
| 💾 **SD Card Video Recovery** | System file picker se SD card ya folder chunein. Sirf padhne wala scan hai, aur saath mein computer par PhotoRec se deep scan ki gaida bhi hai. |
| 🛡 **Factory Reset Assessment** | Phone ki encryption check kar ke sach batata hai ke recovery mumkin hai ya nahi, aur Google Photos trash, Takeout, Drive backups, WhatsApp, Facebook/Instagram, YouTube, OneDrive aur purane phone ki checklist deta hai, "Open" button ke saath. |

Har natija: thumbnail, naam, size, duration, tareekh, jagah. Search, filter (Trash / Hidden / Thumbnails / 30 din / 50MB+), sort (tareekh/size/naam), Preview, **Movies/NexusRecovery** mein copy (cancel ho sakti hai, adhuri copy khud delete ho jaati hai), aur Urdu/English button.

## Version 1.1: naye expert features

- **Gallery Recycle Bin:** Samsung, Xiaomi, Oppo, Vivo, Realme, OnePlus aur Huawei ki gallery ke chhupe Recycle Bin folder pehchanta hai ("RECYCLE BIN" badge). Android 10 aur purane phones par `Android/data` bhi scan hota hai, jahan Samsung ka `.Trash` hota hai.
- **Source app:** har video ke saath likha hota hai ke woh kahan se aayi, jaise WhatsApp, Telegram, Instagram, TikTok, Snapchat, IMO, Camera ya Screen recording.
- **Video health:** MP4/3GP/MOV ka dhancha check kar ke "✓ Playable" ya "⚠ Damaged" dikhata hai.
- **Video Repair:** tooti ya ajeeb format wali video ko nayi MP4 mein dobara banata hai (MediaExtractor + MediaMuxer). Original nahi badalti. Agar video ka index (moov) bilkul gayab ho to saaf bata deta hai.
- **Purani thumbnails nikalna:** gallery ki `.thumbdata` cache files se har JPEG tasveer kaat kar nikalta hai. Is se bahut pehle delete hui videos ki tasveer mil sakti hai, yani pata chal jata hai ke video mein kya tha.

## APK free mein kaise banayein (GitHub Actions)

1. github.com par **New repository** banayein, maslan `nexus-deep-recovery`.
2. Is ZIP ko extract karein aur saari files repo mein upload karein. `.github/workflows/build-apk.yml` file zaroor jaani chahiye.
   - Phone se: GitHub app/website mein "Add file → Upload files", folders samait.
   - Computer se: `git init && git add . && git commit -m init && git push`.
3. Repo mein **Actions** tab kholein, phir **Build debug APK** chunein, phir **Run workflow** dabayein. Push karne par yeh khud bhi chal jata hai.
4. 5 se 8 minute baad run hara (✅) ho jayega. Us run ko kholein, neeche **Artifacts** mein `NexusDeepRecoveryAI-debug-apk` download karein, aur ZIP ke andar `app-debug.apk` hogi.
5. Agar run laal (❌) ho to us ka log mujhe bhej dein, main theek kar dunga.

Android Studio (free) mein bhi project khol kar **Build → Build APK(s)** se APK ban sakti hai.

## Doosre phone par install

1. `app-debug.apk` phone mein bhejein aur kholein.
2. "Install unknown apps" ki ijazat dein (File Manager ya Chrome ke liye).
3. Play Protect warning aaye to "Install anyway" dabayein. Yeh debug APK hai jo aap ne khud banayi hai.

## Test checklist (doosra phone)

- [ ] App khulti hai, 4 cards nazar aate hain, Urdu/English button kaam karta hai
- [ ] Deep Scan: "All files access" screen khulti hai, ON kar ke wapas aane par scan khud shuru hota hai
- [ ] Deep Scan ke dauran ginti badhti hai, aur **Cancel** se rukta hai
- [ ] Ek test video ka naam `test.dat` kar dein: deep scan usay "UNLABELED VIDEO" dikhata hai
- [ ] Gallery se ek video delete karein (trash mein jaye): Recently Deleted mein **TRASH** aata hai aur **Restore** se wapas aata hai (Android 11+)
- [ ] SD card / folder chunne par scan hota hai
- [ ] Preview video player mein khulta hai
- [ ] Copy ke baad file Movies/NexusRecovery mein hai aur original wahin hai jahan pehle tha
- [ ] Copy ke dauran Cancel karne par adhuri file nahi bachti
- [ ] Permission deny karne par saaf message aata hai, crash nahi hota
- [ ] Factory Reset screen par device ka naam aur encryption ka nateeja aata hai, aur Open buttons kaam karte hain

## Kya kaam karta hai, aur kya Android ki wajah se mumkin nahi

**Kaam karta hai:** phone ya SD card par jo bhi video file abhi physically mojood hai (chhupi, trash mein, cache mein, ya naam badli hui) woh mil jaati hai. Android 11+ ki trash se video restore ho jaati hai, aur safe copy bhi kaam karti hai.

**Experimental:** signature se pehchaani gayi cache files kabhi kabhi adhuri video ke tukde hoti hain jo play nahi hote. Kuch gallery apps (jaise Samsung Gallery) apni alag trash rakhti hain jo doosri apps nahi padh saktin.

**Mumkin nahi (koi bhi app nahi kar sakti):**
- Factory reset ke baad ka data: encryption ki chaabi mit jaati hai, purana data bas shor ban jata hai.
- Jo video mustaqil delete ho chuki ho aur us ki jagah naya data likha ja chuka ho.
- Phone ki memory ke raw sectors padhna: Android yeh ijazat nahi deta (root ke bina), aur nayi memory TRIM se khali jagah fauran saaf kar deti hai.
- SD card ka raw scan phone se nahi hota, us ke liye app mein PhotoRec wali gaida hai.

App kabhi jhoote nataij ya "95% recovered" jaise farzi percentage nahi dikhati.

**Note:** `MANAGE_EXTERNAL_STORAGE` permission ki wajah se Google Play par is app ko alag se approval lena padega. Khud install karne wali APK par koi masla nahi.
