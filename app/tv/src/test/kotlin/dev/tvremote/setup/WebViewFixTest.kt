package dev.tvremote.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `dumpsys webviewupdate` as the API 34 emulator wrote it: in use, just switched off, off a while, nothing on the TV. */
class WebViewFixTest {
    private val others = """
    com.google.android.webview.beta is NOT installed.
    com.android.webview is NOT installed."""
    private fun dump(current: String, preferred: String, valid: String) = """Current WebView Update Service state
  Multiprocess enabled: true
  $current
  Minimum targetSdkVersion: 33
  $preferred
  WebView packages:
$valid$others"""
    private val on = "    Valid package com.google.android.webview (versionName: 113.0.5672.136, versionCode: 567263634, targetSdkVersion: 34) is  installed/enabled for all users\n"
    private val off = "    Valid package com.google.android.webview (versionName: 113.0.5672.136, versionCode: 567263634, targetSdkVersion: 34) is NOT installed/enabled for all users\n"

    @Test fun inUse() = assertNull(WebViewFix.missing(dump("Current WebView package (name, version): (com.google.android.webview, 113.0.5672.136)",
        "Preferred WebView package (name, version): (com.google.android.webview, 113.0.5672.136)", on)))

    @Test fun justSwitchedOff() = assertEquals(listOf("com.google.android.webview"), WebViewFix.missing(dump(
        "Current WebView package (name, version): (com.google.android.webview, 113.0.5672.136)", "Preferred WebView package: none", off)))

    @Test fun offAWhile() = assertEquals(listOf("com.google.android.webview"), WebViewFix.missing(dump("Current WebView package is null", "Preferred WebView package: none", off)))

    @Test fun nothingToTurnOn() = assertEquals(emptyList<String>(), WebViewFix.missing(dump("Current WebView package is null", "Preferred WebView package: none", "")))
}
