Islamic Alarm - Dua section Light UI patch

Apply from the root of your existing Islamic-Alarm repository:
  unzip -o ~/storage/downloads/Islamic_Alarm_Light_Dua_Design.zip -d ~/Islamic-Alarm
  cd ~/Islamic-Alarm
  git status
  git add -A
  git commit -m "feat: redesign dua library with light theme and category cards"
  git push origin main

This patch is scoped to the Dua and Hadith section. It registers a dedicated light theme
for DuasActivity and DuaDetailActivity; the app's alarm, prayer-time and other screens
keep their existing theme and code.

The four category illustrations are included in drawable-nodpi.
