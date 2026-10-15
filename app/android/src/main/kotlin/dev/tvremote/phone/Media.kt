package dev.tvremote.phone

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import dev.tvremote.core.Adb
import dev.tvremote.core.AdbLink
import dev.tvremote.core.Manage

// Things sent to the TV: links, shared links and files from other apps, photos / videos / songs from this phone, the Play Store.

/** Makes the TV open a link: a Play Store page, a YouTube video, a web address. Works with and without ADB. */
internal fun MainActivity.openLink(link: String) {
    val b = basic
    val tv = tv
    if (b != null) b.launch(link)
    else if (tv != null && tv.ready()) Thread { try { tv.shell("am start -a android.intent.action.VIEW -d " + Adb.q(link)) } catch (ignored: Exception) { } }.start()
}

// ---------------------------------------------------------------- "Share → TV Remote" from other apps, and playing a file on the TV
/** On the remote screen the picker's message line is out of sight: say it in a toast there. */
internal fun MainActivity.say(text: String, error: Boolean) {
    if (host == null) message(text, error) else Toast.makeText(this, text, if (error) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/** Another app shared a link or a file with us: keep it until a TV is connected, then send it there. */
internal fun MainActivity.takeShare(i: Intent?): Boolean {
    if (i == null || Intent.ACTION_SEND != i.action) return false
    shared = i
    setIntent(Intent(this, MainActivity::class.java))             // not again after a rotation
    sendShared(0)
    return true
}

internal fun MainActivity.sendShared(tries: Int) {
    val i = shared ?: return
    if (!live()) {
        if (host == null) message(t("Chọn TV để phát nội dung vừa chia sẻ.", "Choose a TV to play what you shared."), false)
        if (tries < 90) ui.postDelayed({ if (!isFinishing) sendShared(tries + 1) }, 1000) else shared = null
        return
    }
    shared = null
    @Suppress("DEPRECATION")
    val file = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    if (file != null) { castFile(file); return }
    val link = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+").find(i.getStringExtra(Intent.EXTRA_TEXT) ?: "")?.value
    if (link == null) say(t("Nội dung chia sẻ không có đường dẫn nào để mở trên TV.", "What was shared has no link to open on the TV."), true)
    else { openLink(link); Toast.makeText(this, t("Đã gửi lên TV", "Sent to the TV"), Toast.LENGTH_SHORT).show() }
}

/** A photo, video or song from this phone: copied to the TV's Download folder, then opened by the TV's own viewer or player. */
internal fun MainActivity.castFile(uri: Uri) {
    // Not tv?.ready(): the file picker stops the picture session (onStop) and it is still reopening when we get here.
    // The copy makes its own ADB connection; only the keys-only mode means there is no ADB at all.
    val ip = host
    if (ip == null || basic != null) {
        say(t("Phát tệp lên TV cần ADB. TV này mới chỉ nối bằng nút remote: vào “Bật ADB cho TV này” trước.", "Playing a file on the TV needs ADB. This TV is connected with the remote buttons only: use “Turn on ADB for this TV” first."), true)
        return
    }
    var name = "shared"
    var size = -1L
    try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).let { if (it >= 0) c.getString(it)?.let { n -> name = n } }
                c.getColumnIndex(android.provider.OpenableColumns.SIZE).let { if (it >= 0 && !c.isNull(it)) size = c.getLong(it) }
            }
        }
    } catch (ignored: Exception) {
    }
    if (!name.contains('.')) android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri))?.let { name += ".$it" }
    say(t("Đang gửi lên TV: ", "Sending to the TV: ") + name + "...", false)
    Thread {
        val link = AdbLink(filesDir, ip)
        val err = try {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw java.io.IOException("cannot read the file")
                Manage(link, null, cacheDir).cast(input, size, name)
            }
            null
        } catch (e: Exception) { dev.tvremote.core.Errors.say(e, VI) } finally { link.close() }
        ui.post {
            if (isFinishing) return@post
            if (err == null) say(t("Đang phát trên TV: ", "Playing on the TV: ") + name, false)
            else say(t("Không phát được: ", "Could not play it: ") + err, true)
        }
    }.start()
}

/**
 * The system photo picker: a sheet over this app (Android 13+, or older phones with a recent Google Play system update),
 * so the remote stays where it is and needs no permission to read photos. Without it, the file picker.
 */
internal fun MainActivity.pickMedia() {
    val sheet = Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)    // no type: photos and videos both
    if (Build.VERSION.SDK_INT >= 33 || sheet.resolveActivity(packageManager) != null) {
        try { startActivityForResult(sheet, PICK_CAST); return } catch (ignored: Exception) { }
    }
    // Android 12 and older: Google Play services carries the same picker under this name (as androidx PickVisualMedia uses)
    val gms = Intent("androidx.activity.result.contract.action.PICK_IMAGES")
    if (gms.resolveActivity(packageManager) != null) {
        try { startActivityForResult(gms, PICK_CAST); return } catch (ignored: Exception) { }
    }
    pickFile(arrayOf("image/*", "video/*"))                       // the full-screen file picker: the copy works all the same
}

internal fun MainActivity.pickFile(types: Array<String>) {
    try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, types).addCategory(Intent.CATEGORY_OPENABLE), PICK_CAST) }
    catch (e: Exception) { say(t("Điện thoại không có app chọn tệp.", "This phone has no file picker."), true) }
}

internal fun MainActivity.askText(title: String, hint: String, ok: String, then: (String) -> Unit) {
    val box = input(hint)
    val wrap = FrameLayout(this)
    wrap.setPadding(dp(20), dp(8), dp(20), 0)
    wrap.addView(box)
    AlertDialog.Builder(this).setTitle(title).setView(wrap)
        .setPositiveButton(ok) { _, _ -> box.text.toString().trim().let { if (it.isNotEmpty()) then(it) } }
        .setNegativeButton(t("Hủy", "Cancel"), null).show()
}

/** Things the TV can be told to open straight from the phone, saving the hunt with the TV's own keyboard. */
internal fun MainActivity.quickOpen() {
    if (!live()) return
    val store = { id: String -> openLink("https://play.google.com/store/apps/details?id=$id") }
    val items = ArrayList<Triple<String, String, () -> Unit>>()
    items.add(Triple("@search|" + t("Tìm app trên CH Play", "Search the Play Store"), "") {
        askText(t("Tìm app trên CH Play", "Search the Play Store"), t("vd: vlc", "e.g. vlc"), t("Tìm", "Search")) { openLink("market://search?c=apps&q=" + java.net.URLEncoder.encode(it, "UTF-8")) }   // the TV's Play Store takes searches only this way
    })
    items.add(Triple("@package|" + t("Mở trang CH Play theo app id", "Open a Play Store page by app id"), t("vd: org.videolan.vlc", "e.g. org.videolan.vlc")) {
        askText(t("App id (tên gói)", "App id (package name)"), "org.videolan.vlc", t("Mở", "Open")) { store(it) }
    })
    items.add(Triple("@link|" + t("Mở đường dẫn trên TV", "Open a link on the TV"), t("YouTube, web, tệp...", "YouTube, web, files...")) {
        askText(t("Đường dẫn", "Link"), "https://youtu.be/...", t("Mở", "Open")) { openLink(if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it)) it else "https://$it") }
    })
    items.add(Triple("@image|" + t("Phát ảnh / video từ điện thoại", "Play a photo / video from the phone"), t("Cần ADB", "Needs ADB")) {
        pickMedia()
    })
    items.add(Triple("@music|" + t("Phát nhạc / tệp khác", "Play a song / other file"), "") {
        pickFile(arrayOf("audio/*", "video/*", "image/*"))
    })
    items.add(Triple("@cast|" + t("Chiếu màn hình điện thoại lên TV", "Mirror the phone’s screen on the TV"), t("Cùng Wi-Fi", "Same Wi-Fi")) {
        try { startActivity(Intent("android.settings.CAST_SETTINGS")) }
        catch (e: Exception) { say(t("Điện thoại này không có phần Truyền (Cast) trong Cài đặt. Thử nút Truyền / Smart View ở thanh thông báo.", "This phone has no Cast screen in Settings. Try the Cast / Smart View tile in the notification shade."), true) }
    })
    for ((name, id) in listOf("@play|YouTube" to "com.google.android.youtube.tv", "@play|Netflix" to "com.netflix.ninja", "@play|VLC" to "org.videolan.vlc", "@play|Kodi" to "org.xbmc.kodi",
        "@download|Downloader" to "com.esaba.downloader", "@home|Projectivy Launcher" to "com.spocky.projengmenu", "@globe|Tailscale" to "com.tailscale.ipn"))
        items.add(Triple(name, t("Trang CH Play: cài hoặc mở", "Play Store page: install or open")) { store(id) })
    menu(t("Mở trên TV", "Open on the TV"), -1, items)
}
