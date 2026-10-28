# Writing the words (every language)

Who reads them: a person on the sofa with a remote, not a developer. They read a label in one glance or not at all.
Why this file: words written in English first and then translated came out as machine words ("Gập cổng HDMI/AV chưa
cắm" for "Fold unplugged inputs"; "Luôn về khi bật TV" that never said where to). Short is not enough: it must be plain.

## The rules

1. **Write each language as its speakers say it, never word by word from another.** Vietnamese is written first and on
   its own (it is the source); English and the others say the same thing the way a native would, not the Vietnamese
   (or the code's) words in a row. If you think in English, find what a Vietnamese person would say, then write that.
2. **Say what happens, not how it works inside.** "Chỉ hiện cổng đang cắm", not "Gập cổng"; "Tự mở TV Home khi bật
   TV", not "Luôn về khi bật TV". A setting's label answers "what will I see?".
3. **Name the thing.** No label without its object: where it goes, what it opens, which app. When the object
   changes (the chosen app), put it in the words (`%1$s`).
4. **Words people already use for it.** The TV's own menus, the phone's, the shop's: "Nguồn vào", "Cài đặt", "Hẹn
   giờ tắt", "Inputs", "Settings". No terms from the code (fold, snapshot, target, slot, pin state, overlay).
5. **A switch reads as a sentence that is true when it is on.** "Tự mở TV Home khi bật TV: Bật". Not a negative
   ("Không ẩn…"), not an "on" word inside a switch that can be off ("Bật TV là …: Tắt" reads twice).
6. **The say-it-aloud test.** Read the label aloud as if telling a parent what to press. If you would say it
   differently, write what you would say.
7. **Short still holds** (see the memory/guide limits): one line for a label, the detail in the note under it or the
   dialog, not in the label.

## Per language

| Language | Notes |
|---|---|
| Vietnamese (`vi`) | Everyday words, not Hán-Việt or translated ones: "mở", "hiện", "ẩn", "cắm", "tắt"; avoid "kích hoạt", "thực thi", "khởi chạy", "gập", "ghi đè". "TV", "app", "Wi-Fi", "HDMI" stay as people say them. Sentence case, no Title Case. |
| English (`en`) | Plain verbs, sentence case ("Show only plugged-in inputs"), no "Enable/Disable X functionality". Phrasal verbs are fine ("turn on", "set up"). |
| Russian (`ru`) | What Android TV and phones say in Russian ("Источники", "Настройки", "Таймер сна"); infinitive or noun for labels ("Показывать только подключённые входы"), «ёлочки» for quotes. Not a word-by-word copy of the English. |
| A new language | Its own TV/phone menus are the reference; a native speaker's look before release if possible. |

## Before / after (real ones)

| Before | After | Rule |
|---|---|---|
| Gập cổng HDMI/AV chưa cắm | Chỉ hiện cổng HDMI/AV đang cắm | 1, 2 |
| Luôn về khi bật TV | Tự mở TV Home khi bật TV (or the chosen app's name) | 3 |
| Bật TV là mở TV Home: Tắt | Tự mở TV Home khi bật TV: Tắt | 5 |
| Fold unplugged HDMI/AV inputs | Show only plugged-in HDMI/AV inputs | 2, 4 |
