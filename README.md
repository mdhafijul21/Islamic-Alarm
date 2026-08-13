# ইসলামিক এলার্ম (Islamic Alarm)

একটি সম্পূর্ণ অ্যান্ডয়েড অ্যাপ যা নামাজের সময় মোবাইল ব্যবহার সীমিত রাখার জন্য ফুলস্ক্রিলে লক এলার্ম চালু করে।

---

## 🌟 বৈশিষ্ট্যসমূহ (Features)

1. **ফুলস্ক্রিন লক এলার্ম (Fullscreen Lock Screen)**:
   - এলার্মের সময় হলে ফুলস্ক্রিনে ইসলামিক ব্যাকগ্রাউন্ড, চাঁদ-তারা, কুরআনের আয়াত (সূরা আন-নিসা: ১০৩) ও বাংলায় অর্থ প্রদর্শন করে।
   - লাইভ কাউন্টডাউন টাইমার (যেমন: ১৫ মিনিট)।

2. **স্ক্রিন পিনিং ও লক সুবিধা**:
   - এলার্ম চালু হলে `startLockTask()` (Screen Pinning) এবং WakeLock সক্রিয় করে যাতে ব্যবহারকারী সহজে অ্যাপ থেকে বের হতে না পারেন।
   - ব্যাক বাটন সম্পূর্ণ নিষ্ক্রিয় করা থাকে।

3. **কল রিসিভ সাপোর্ট (In-Call Handling)**:
   - এলার্ম চলার সময় ইনকামিং কল আসলে স্বয়ংক্রিয়ভাবে স্ক্রিন আনপিন হয় যাতে কল রিসিভ করা যায়। কল শেষ হলে বাকি সময় আবার লক চালু হয়।

4. **মাল্টিপল এলার্ম ম্যানেজমেন্ট**:
   - সময়, লক ডিউরেশন (১-৬০ মিনিট) এবং ঐচ্ছিক লেবেল (যেমন: ফজর, জিকির, তাহাজ্জুদ) সহ একাধিক এলার্ম তৈরি, সম্পাদন ও মোছার সুবিধা।

5. **পারমিশন গাইড ও অটোস্টার্ট সাপোর্ট**:
   - Exact Alarm, Ignore Battery Optimization, Phone State এবং Xiaomi/Oppo/Realme/Vivo/Huawei ডিভাইসের জন্য Autostart সেটিংসে যাওয়ার সুবিধা।

6. **সোফিস্টিকেটেড ডার্ক থিম (Sophisticated Dark Aesthetic)**:
   - নাইট ক্যানভাস (`#0F1115`), ডার্ক স্লেইট সারফেস ও গোল্ডেন একসেন্ট সহ প্রিমিয়াম ডিজাইন।

---

## ⚠️ গুরুত্বপূর্ণ সীমাবদ্ধতা (Limitations)

Android OS এর নিরাপত্তা ও পলিসির কারণে:
- **Screen Pinning (App Pinning)** ব্যবহার করে ফুলস্ক্রিন লক অ্যাপ তৈরি করা হয়েছে।
- ব্যবহারকারী ডিভাইসের ফিজিক্যাল ব্যাক + রিসেন্ট বাটন একসাথে চেপে ধরলে অথবা ডিভাইস রিবুট দিলে স্ক্রিন আনপিন হতে পারে।
- এটি সম্পূর্ণ ১০০% সিস্টেম-লেভেল হার্ড লক নয় (যেহেতু Device Admin / Kiosk mode ছাড়া সাধারণ অ্যাপের জন্য সম্পূর্ণ ব্লক করা অ্যান্ড্রয়েড পলিসি বিরোধী)। তবে নামাজের ওয়াক্তে মনোযোগ ধরে রাখার জন্য এটি অত্যন্ত কার্যকর।

---

## 📱 Termux দিয়ে GitHub-এ Push করে APK নামানোর নিয়ম

আপনার মোবাইলে **Termux** ব্যবহার করে সহজে প্রজেক্টটি GitHub-এ আপলোড করতে পারেন এবং **GitHub Actions** থেকে স্বয়ংক্রিয়ভাবে তৈরি হওয়া **APK** ডাউনলোড করতে পারেন।

### ধাপ ১: Termux-এ Git সেটআপ

```bash
# Termux প্যাকেজ আপডেট করুন
pkg update && pkg upgrade -y

# Git ইন্সটল করুন
pkg install git -y

# আপনার নাম ও ইমেইল সেট করুন
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### ধাপ ২: প্রজেক্ট Push করুন

```bash
# প্রজেক্ট ফোল্ডারে যান
cd IslamicAlarm

# Git ইনিশিয়ালাইজ করুন
git init
git add .
git commit -m "Initial commit - Islamic Alarm App"

# আপনার GitHub রিপোজিটরির সাথে কানেক্ট করুন
git branch -M main
git remote add origin https://github.com/USERNAME/REPOSITORY.NAME.git

# GitHub Personal Access Token (PAT) ব্যবহার করে Push করুন
git push -u origin main
```

### ধাপ ৩: GitHub Actions থেকে APK ডাউনলোড

1. আপনার GitHub রিপোজিটরিতে যান।
2. **Actions** ট্যাবে ক্লিক করুন।
3. **Build Android APK** ওয়ার্কফ্লোটি স্বয়ংক্রিয়ভাবে চলতে শুরু করবে।
4. বিল্ড শেষ হলে (সবুজ টিক চিহ্ন আসলে) সেই রানিং জবটিতে ক্লিক করুন।
5. **Artifacts** সেকশনে **Islamic-Alarm-Debug-APK** ডাউনলোড লিংক পেয়ে যাবেন। ZIP ফাইলটি এক্সট্র্যাক্ট করে `app-debug.apk` ফাইলটি আপনার মোবাইলে ইন্সটল করুন।

---

## 🛠️ টেকনোলজি স্ট্যাক (Tech Stack)

- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL `.gradle.kts`)
- **UI Framework**: Material Design 3 (ViewBinding, CoordinatorLayout, RecyclerView)
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 34 | **Compile SDK**: 36
- **Architecture**: MVVM / Modular Clean Architecture
