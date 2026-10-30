package dev.tvremote.setup

import android.app.Activity
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import dev.tvremote.core.T
import dev.tvremote.tvui.TvUi

/** The Input button where the TV has no picker of its own (the stock launcher is off): the TV's inputs as it lists them, one to switch to. */
class InputsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val list: List<TvInputInfo> = try { (getSystemService(TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList.orEmpty() } catch (e: Exception) { emptyList() }
        if (list.isEmpty()) { finish(); return }
        val dlg = TvUi.menu(this, R.style.TvSheet, T("keyfix.inputs"), list.map { info ->
            TvUi.Item(R.drawable.ic_input, info.loadLabel(this).toString()) {
                val uri: Uri = if (info.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(info.id) else TvContract.buildChannelsUriForInput(info.id)
                try { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }
        }, R.drawable.ic_check)
        dlg.setOnDismissListener { finish() }
    }
}
