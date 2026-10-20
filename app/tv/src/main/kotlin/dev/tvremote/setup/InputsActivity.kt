package dev.tvremote.setup

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import dev.tvremote.core.T

/** The Input button where the TV has no picker of its own (the stock launcher is off): the TV's inputs as it lists them, one to switch to. */
class InputsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val list: List<TvInputInfo> = try { (getSystemService(TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList.orEmpty() } catch (e: Exception) { emptyList() }
        if (list.isEmpty()) { finish(); return }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("keyfix.inputs"))
            .setItems(list.map { it.loadLabel(this).toString() }.toTypedArray()) { _, i ->
                val info = list[i]
                val uri: Uri = if (info.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(info.id) else TvContract.buildChannelsUriForInput(info.id)
                try { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }.setOnDismissListener { finish() }.show()
    }
}
