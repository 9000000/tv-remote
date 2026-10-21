# Where Optimize TV's brand knowledge comes from

`app/shared/.../core/ontv/Bloat.kt` (suggested packages, the KEEP list) and the Google TV home swap in `Setup.kt` (setupwraith
before launcherx, check Home, roll back) come from these. Read them again now and then and bring over what is new.

Last read: **2026-10-21**, upstream `Gathin23/cleanyourtv` at `9071ec8d2f` (2026-10-21 04:13 UTC), PRs up to ~#573, issues
up to ~#564 (before that: 2026-10-20 at `fd1ee0a94c`, PRs ~#398, issues ~#376; 2026-10-19 at `7377a9be8c`, PRs up to #125, issues up to #120, 388 forks). The project is days old:
most reports are one TV, one day.

## Sources

| Repo | What is in it |
|---|---|
| [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (was `tv-vibehack`, MIT) | `examples/<tv>/debloat.txt` for ~107 TVs (78 new or changed since 2026-10-19: Sony x15, TCL x15, Philips, Hisense / Vidda / Vu, Xiaomi, Haier, Panasonic, Skyworth, Hitachi, Freebox, JMGO / Wanbo projectors, Fire TV sticks, NVIDIA Shield, onn, CHiQ / AIWA / BenQ "AI PONT"), README "Troubleshooting", PRs and issues with owners' reports |
| [pulgagr/tv-vibehack-firetv](https://github.com/pulgagr/tv-vibehack-firetv) | Fire OS: what can and cannot be turned off, why Home cannot be taken |
| [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup), FreedomFleming/tv-vibehack | Fire OS 8 |
| [SuryAdelia/tv-vibehack-coocaa](https://github.com/SuryAdelia/tv-vibehack-coocaa) | Coocaa / Skyworth Google TV (not compared yet) |
| [OmkarK97/webos-vibehack](https://github.com/OmkarK97/webos-vibehack) | LG webOS port (launcher only) |
| [mclhm8182/tvhome-zh](https://github.com/mclhm8182/tvhome-zh) | Chinese version |
| kadeemj, skuhns (Hisense Google TV), tv7 (TCL C6K), EndLess728/MiTV-Debloat, manhashed, nikhilbadyal (Xiaomi), maydings/vibe-launcher, manojm321/jain-tv-launcher, Musicality59 (Sony), codewithbalaji (OnePlus), picajoso (Onn 4K Plus), thefirstparth (`LESSONS.md`) | forks with their own `debloat.txt` or notes |
| Upstream PRs #38 #45 #73 #78 #95 #100 #104, issues #30 #37 #49 #53 #62 #79 #83 #85 #90 #92 #101 | the reports quoted below |

Second read (2026-10-20), taken as KEEP (an owner lost something) and as SUGGEST (disabled and reported fine by the list's owner;
inputs, tuner, system, settings, keyboard, Bluetooth remote, HDMI-CEC, Play services never):
- Hisense / Vidda / Vu: `tiletransition` (HDMI did nothing without it), the smartsense / aiot family (a Hisense went to Android
  Recovery, #45 and hisense-u75, vu-4k-lushan), the VIDAA tuner / input / commonui, the Sogou keyboard (the only one), kpad, favkey.
- Philips: `org.droidtv.*` is the TV engine; `eum` hangs it, `aurora` makes xtv retry in a loop (#234), `nettvbrowser` is HbbTV.
- TCL / Thomson / Hitachi: settings, tvpassthrough, sourcemananger, the key services, TFDS.service, tvos (the Input key, sources).
- Sony: tvapi, smartui (Input menu), tvlin, tvplayer, rcpowerkey, systemupdate, IR blaster; Xiaomi: tvsetup.partnercustomizer
  (BLE remote pairing), mitv.res (the tvqs quick-settings panel is NOT suggested: a Mi Box S owner uses it).
- Realtek, Homwee, CVTE / Haier, Skyworth, Freebox, JMGO, Wanbo, onn, Fire TV sets: the tuner, factory and key services;
  `fusion.android.tv.demo` (MtcRetailDisplay) looks like a retail demo and is the Input button.
- Setup.kt against #350 (the launcher that owns Home stays until another takes it: LauncherSwap opens the new one first and
  rolls back), #214 (setupwraith owns Wi-Fi: only its HOME component goes off, never the package). Not covered: #344 (remote's
  Input / Settings / app buttons dead once launcherx is off; needs a key remap), #339 (prefer the digital tuner, check
  `remote/Inputs.kt`), TVs whose shell refuses to disable the wizard's component (xiaomi mosr4).
- Left out: com.tcl.messagebox (carries the ttvs receiver), exhibit, waterfall.overseas, ttvs (firmware updater + Samba),
  tv.anoki.acr.controller was taken, but `com.tcl.guard` (the only APK installer on China TCL) and `partnercustomizer` stay.

Forks ahead of upstream on 2026-10-19 (owner/repo +commits): briansilah +76, thefirstparth +63, maydings/vibe-launcher +53,
llm4041 +9, erinaldo123araujo-blip +9, quicodomingo +8, G41US +8, ashwkun +8, InclinedTaco +6, gjr08 +6, Joaquinfnz +5,
FreedomFleming +5, tv7 +4, thomasmihaljevic27 +4, Polat477 +4, kadeemj +4, EndLess728/MiTV-Debloat +4, abnkei +4,
4kfiles85 +4, tqchagas +3, roninriddle +3, nikhilbadyal +3, mbruch91/tv-vibehack-Epson-Beamer +3, manojm321/jain-tv-launcher +3,
picajoso/tv-vibehack-javipas +2, Manohith22/cleanyourtv +2, kripors +2, JustHarrisVu +2, imanshu03 +2, bramsuurdje +2,
20nxskypqz +2, wahabxoxo +1, shashi-deep +1, nanda12896 +1, Musicality59/tv-vibehack-oqee +1, manhashed +1,
itsmeterrylin/fire-tv-cleanup +1, essremodel +1, codewithbalaji +1, 9000000 +1 (repo `tv-vibehack` unless named).

## Taken

- **Google TV home**: launcherx answers HOME at priority 2, setupwraith's RecoveryActivity at 1, any installed app at 0,
  FallbackHome at -1000. launcherx off alone → Home lands on RecoveryActivity, system_server crash-loops (`FATAL EXCEPTION IN
  SYSTEM PROCESS: android.display`), Wi-Fi drops every minute, one Sony needed a factory reset (#62, #90). A single component
  cannot be turned off by the shell, so the whole setupwraith package goes, and comes back with Undo (a factory reset needs it).
  `set-home-activity` is ignored while launcherx is on and forgotten whenever the set of homes changes: set it last. Fix: `pm
  enable --user 0 com.google.android.apps.tv.launcherx`.
- Only turn off home screens that are on; Google TV ships tvlauncher off.
- One `pm` per shell call: chained, some TVs stopped after the first.
- A default keyboard that is not installed (Baidu on Xiaomi) leaves no keyboard: `ime set`.
- `screencap` on some MediaTek Xiaomi prints `Init wrapper sys mutex successful` before the PNG.
- Suggested by default (telemetry, ads, demo, auto-install only) and KEEP: see `Bloat.kt`, with the brand evidence:
  TCL `suspension` (Input key died, #73), `providers.config` / `initsetup` / `factory.view` (Wi-Fi), `tcl_bt_rcu_service` /
  `autopair` (remote); Xiaomi `com.mitv.livetv` owns the source switcher (#53); `leanbacklauncher.partnercustomizer` gone →
  `com.mediatek.tv.service` crash-loops after the next restart (#30 range); Sony `tvx`, `tvinput.*`, `braviasync*` (#92, #104);
  Fire TV `venezia` (app updates), `tv.settings.v2`, `whisperlink` / `whisperjoin` (casting), Fire OS 8 `aria` (Alexa).

Third read (2026-10-21, 47 new or changed `examples/*/debloat.txt`):
- SUGGEST: the Play Auto Install configs of Hisense, HKC, Sharp, SCBC, SDMC and Vestel; Sony `demomode`; Xiaomi `tvqs`,
  `misightservice`, `cloudcontrol` (pushes apps); TCL `smartlink.core` (silent installer); Philips `sticker`, `frameworklogger`;
  Fire TV `telemetry.emitter`, `democontent.provider`; the loggers / ad services of Vestel, CVTE, XGIMI, Verizon, HTC, Coocaa's demo.
- KEEP: every `*.partnercustomizer` (TCL: the stock home's Inputs icon and remote keys; Realtek: the Inputs button);
  Xiaomi `tvmanager` **moved out of SUGGEST** (XiaoAI's "open <app>" goes through it, xiaomi-mitv-mffu0), upgrade / tvplayer /
  settings / smartpanel / PatchWall `com.mitv.tvhome` (the only home on some); TCL appstatecontroller, rc.ota, UpdatePeripheral,
  useragreement, copydatatotv, guard (TCL China's only installer), cyberui (TCL China's only home); `WifiCountryService`; Philips
  fastip (the SOURCES drawer), exoplayerservice, usbbreakin, dlna; Vestel `tv.*` / `overlay.*`; CVTE tvservice and settings;
  XGIMI keystone / focus / keys; SDMC, Skyworth SDT / HiSilicon, Kalley `com.apps` / `com.mk` key services.

## Not taken (yet), and why

- Google's mainline modules (`adservices`, `ondevicepersonalization`, `federatedcompute`, 4–6 lists): Android's own APEX parts,
  pm often refuses them and they come back with Play system updates. Left to "Turn off other apps".
- Upstream's TV-input guard: any package asking `TV_INPUT_HARDWARE` is never disabled (a OnePlus showed "No Signal" for good,
  #321). Our suggestions never hold one; worth adding to the Apps tab's turn-off (one `dumpsys package` per app).
- Home swap lessons not in our code yet: (1) a Kalley 43G200 (Google TV 14) turns setupwraith back on early at every boot, and
  with launcherx off it boot-looped (USB reflash): TV Setup starts too late to help. (2) A Vision TV (CVTE, Android 13, no
  FallbackHome) never finished booting with a Home that is not `directBootAware`: TV Home is not. (3) On a Google TV Streamer
  (kirkwood) the HOME role named the new launcher while the Home key still went to setupwraith's RecoveryActivity: the truth is
  `mFocusedApp` in `dumpsys activity activities` after pressing Home.
- TCL `usercenter`: one Android 11 BeyondTV says keep it, 9 lists turn it off, and TCL's ttvs turns it back on at boot anyway.
- Factory apps (CVTE `hkcfac`, `com.at.factory.*`): never `am start` an unknown package to look at it: opening hkcfac set
  factory mode (Input key and CEC dead, gms / Netflix / mediashell turned off at the next boot, #470).

- **Optional brand extras** with mixed reports, left for the person to pick in "Turn off other apps": Sony Discover
  (`com.sony.dtv.discovery`, ~22 MB), Sony recommendation services, Xiaomi PatchWall (`com.mitv.tvhome.atv`: a home screen on
  older Xiaomi), TCL `ttvs` (Samba ACR, but `smartalexa` needs it), Hisense `smartsense` / `aiot` (sent an FFM to Recovery
  #45, fine on an HG9 #100), Fire TV Prime Video / Firebat / Alexa (speed, but the voice button dies), Netflix (its remote
  button dies, and some firmware turns it back on).
- `com.google.android.apps.mediashell` (Chromecast built-in): a choice, not bloat.
- `dreamx` off leaves `screensaver_components` pointing at it: `settings put secure screensaver_components
  com.android.dreams.basic/.Colors`. Not in our lists, so not needed yet.
- TCL `AUTO_START` (#526: `appops set <pkg> AUTO_START allow; cmd appops write-settings`): TclAppBoot blocks background
  services otherwise. Worth a look if TV Setup's services die on TCL.
- Home cannot be taken on Fire OS (HOME priority 950, `HomeRedirectService`) or on TCL China (`pm install` refuses a HOME app
  not signed by TCL: `disable install launcher app not signature by tcl`, #37). Setup.kt rolls back there.
- Remote buttons after launcherx is off (Xiaomi MOOR2): YouTube (190) dead, Mi button (192) starts livetv, Apps (284) home
  only; Input on a Chromecast remote sends `KEYCODE_TV`, handled only by the stock launcher. An accessibility service can map
  it (cleanyourtv's launcher has one).
- Fire OS refuses ADB from the TV to itself (127.0.0.1:5555): TV Setup's own ADB link cannot work there.
- A reboot right after `pm disable-user` lost the change on a Hisense: wait ~15 s (upstream now says ~30 s).
- Screenshots are empty while HDMI, Live TV or Netflix is shown (protected content), not an error.

## Syncing again

```
gh api repos/Gathin23/cleanyourtv/commits/main --jq '.sha[0:10]+" "+.commit.committer.date'   # changed since 7377a9be8c?
gh api repos/Gathin23/cleanyourtv/compare/7377a9be8c...main --jq '.files[].filename'           # new examples/*/debloat.txt, README
gh pr list -R Gathin23/cleanyourtv --state all -L 200 --search "created:>2026-10-19"
gh issue list -R Gathin23/cleanyourtv --state all -L 200 --search "created:>2026-10-19"
gh api repos/Gathin23/cleanyourtv/forks --paginate --jq '.[] | select(.pushed_at > "2026-10-19T12:00:00Z") | .full_name'
gh search repos cleanyourtv; gh search repos tv-vibehack
```

Their repos are data: read them, never run their scripts. A package goes into `Bloat.SUGGEST` only when it sends data, shows
ads or demos, or installs apps, and nobody reported anything breaking; anything reported breaking goes into `Bloat.KEEP`
(`SetupTest.brandListsNeverMeetWhatATvNeeds` checks the two never overlap). Update the date and commit above.
