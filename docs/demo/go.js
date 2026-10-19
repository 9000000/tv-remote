/* Guide topic -> where the live demo opens for it (demo/?go=<topic id>): the page's tab, then what to show there: a
   selector (the first one found is scrolled to and outlined; its card when it is a title), "name()" (a function of the
   page, such as a dialog) or "click:<selector>" (a tap, such as the Settings button); more than one may follow, in order. Read by guide.js (its "Try it" buttons) and demo/index.html. Matched against the page the demo
   runs (sw.js REF): a selector that page lacks just opens the tab. */
window.DEMO_GO = {
  remote: ["remote"],
  typing: ["remote", ".rc"],
  voice: ["remote", ".rc"],
  listen: ["remote", ".rc"],
  clipboard: ["remote", "clipDialog()"],
  picture: ["remote", "#screencard"],
  quality: ["remote", "#screencard"],
  screenshot: ["remote", "#screencard"],
  notify: ["remote", "notifyDialog()"],
  draw: ["remote", "drawStart()"],
  photo: ["remote", "drawStart()"],
  share: ["remote", "shareDialog()"],
  apps: ["apps"],
  files: ["files"],
  health: ["health"],
  "tv-status": ["health"],
  sleep: ["auto", '[data-i18n="auto.sleep_timer"]'],
  bedtime: ["auto", '[data-i18n="auto.bedtime"]'],
  limits: ["auto", '[data-i18n="auto.daily_app_limits"]'],
  lock: ["auto", '[data-i18n="auto.locks_title"]'],
  welcome: ["auto", "#welcomecard"],
  scenes: ["auto", '[data-panel="auto"] [data-i18n="common.scenes"]'],
  optimize: ["tv", '[data-panel="tv"] [data-i18n="common.optimize_tv"]'],
  launchers: ["tv", "#launchcard"],
  home: ["tv", "#launchcard"],
  awake: ["health", "click:#hmode [data-m=sleep]", "#hsleep .check"],
  "screen-dim": ["remote", "timerMenu()"],
  tvguard: ["tv", "#tvsetcard"],
  hook: ["remote", "click:#aboutbtn", "click:#hookpick [data-v=on]", "#hookgroup"],
  log: ["remote", "click:#aboutbtn", "#sendlog"]
};
