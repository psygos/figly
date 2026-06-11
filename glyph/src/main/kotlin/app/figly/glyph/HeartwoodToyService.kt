package app.figly.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import app.figly.core.Matrix
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

/**
 * The heartwood toy: stateless, storageless — a clock rendered as a tree.
 * Heartbeats push the present moment; the lively window after a flip lets
 * it move. Nothing is remembered; nothing needs to be.
 */
class HeartwoodToyService : Service() {

    private var gm: GlyphMatrixManager? = null
    private var registered = false
    private val main = Handler(Looper.getMainLooper())
    private var frames = LoomFrames()
    private var sessionStart = 0L

    private val ticker = object : Runnable {
        override fun run() {
            pushNow(lively = true)
            if (SystemClock.uptimeMillis() - sessionStart < LIVELY_MS) {
                main.postDelayed(this, FRAME_MS)
            } else {
                pushNow(lively = false)
            }
        }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                        GlyphToy.EVENT_AOD -> pushNow(lively = false)
                        else -> Unit
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        init()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(ticker)
        runCatching {
            gm?.turnOff()
            gm?.unInit()
        }
        gm = null
        registered = false
        return false
    }

    private fun init() {
        if (gm != null) return
        gm = GlyphMatrixManager.getInstance(applicationContext)
        gm?.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: android.content.ComponentName?) {
                runCatching {
                    gm?.register(Glyph.DEVICE_25111p)
                    registered = true
                    val len = runCatching { Common.getDeviceMatrixLength() }
                        .getOrDefault(Matrix.SIZE).takeIf { it >= Matrix.SIZE } ?: Matrix.SIZE
                    if (frames.low.size != len * len) frames = LoomFrames(len)
                }
                sessionStart = SystemClock.uptimeMillis()
                main.removeCallbacks(ticker)
                main.post(ticker)
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                registered = false
            }
        })
    }

    private fun pushNow(lively: Boolean) {
        val g = gm ?: return
        if (!registered) return
        Heartwood.render(
            frames,
            epochMs = System.currentTimeMillis(),
            lively = lively,
            animMs = SystemClock.uptimeMillis(),
        )
        runCatching {
            val frame = GlyphMatrixFrame.Builder()
                .addLow(frames.low)
                .addMid(frames.mid)
                .addTop(frames.top)
                .build(applicationContext)
            g.setMatrixFrame(frame.render())
        }
    }

    companion object {
        private const val FRAME_MS = 125L
        private const val LIVELY_MS = 14_000L
    }
}
