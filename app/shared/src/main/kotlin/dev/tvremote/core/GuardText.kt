package dev.tvremote.core

import dev.tvremote.guard.GuardWords

/** The error guard's words (err.* in i18n/<lang>.json) in the app's language; TV Home, Channels and Games use strings.xml instead. */
fun guardWords() = GuardWords(T("err.title"), T("err.last_time"), T("err.copy"), T("err.copied"), T("err.share"), T("err.close")) { n ->
    T("err.repeat", "n" to n) }
