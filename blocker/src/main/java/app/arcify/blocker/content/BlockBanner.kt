package app.arcify.blocker.content

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import app.arcify.blocker.Diag
import app.arcify.blocker.R
import app.arcify.core.content.IgSurface
import app.arcify.core.content.Platform

/**
 * A short, non-interactive banner at the top of the screen that says why something was blocked.
 *
 * Content blocks use this instead of a full-screen page. The blocked app stays in front (Arcify
 * only navigates away from the reel, Short or page), so there is no screen switch, no flash and
 * no feed refresh. Drawn as an accessibility overlay: no extra permission, and it never takes
 * touches.
 */
class BlockBanner(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager? get() = service.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private val hide = Runnable { remove() }

    fun show(block: ContentBlock) {
        show(message(block))
    }

    fun show(message: String) {
        val wm = windowManager ?: return
        val banner = view ?: createView().also { created ->
            try {
                wm.addView(created, layoutParams())
                view = created
            } catch (e: RuntimeException) {
                Diag.error("banner could not be shown", e)
                return
            }
        }
        banner.text = message
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, VISIBLE_MS)
    }

    fun remove() {
        handler.removeCallbacks(hide)
        val current = view ?: return
        view = null
        runCatching { windowManager?.removeView(current) }
    }

    private fun message(block: ContentBlock): String = when (block) {
        is ContentBlock.ShortForm -> {
            val name = service.getString(platformName(block.platform))
            val surface = block.surface?.takeIf { it != IgSurface.UNKNOWN }
            if (surface != null) {
                service.getString(R.string.banner_shortform_surface, name, service.getString(surfaceName(surface)))
            } else {
                service.getString(R.string.banner_shortform, name)
            }
        }

        is ContentBlock.ShortFormLimit ->
            service.getString(R.string.banner_shortform_limit, block.limit, service.getString(platformName(block.platform)))

        is ContentBlock.Channel -> service.getString(R.string.banner_channel, block.channel)
        is ContentBlock.Website ->
            if (block.studyMode) service.getString(R.string.banner_website_study) else service.getString(R.string.banner_website, block.host)

        ContentBlock.DmScroll -> service.getString(R.string.banner_dm_scroll)
    }

    private fun platformName(platform: Platform): Int = when (platform) {
        Platform.INSTAGRAM -> R.string.banner_platform_instagram
        Platform.YOUTUBE -> R.string.banner_platform_youtube
        Platform.SNAPCHAT -> R.string.banner_platform_snapchat
        Platform.FACEBOOK -> R.string.banner_platform_facebook
    }

    private fun surfaceName(surface: IgSurface): Int = when (surface) {
        IgSurface.REELS_TAB -> R.string.banner_surface_reels_tab
        IgSurface.FEED -> R.string.banner_surface_feed
        IgSurface.EXPLORE -> R.string.banner_surface_explore
        IgSurface.DM -> R.string.banner_surface_dm
        IgSurface.UNKNOWN -> R.string.banner_surface_feed
    }

    private fun createView(): TextView = TextView(service).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val pad = dp(16)
        setPadding(pad, dp(12), pad, dp(12))
        // Placeholder colours until the design pass (brand-dark background, white text).
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.argb(238, 0x1F, 0x1B, 0x3A))
        }
        gravity = Gravity.CENTER
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP
        horizontalMargin = 0.04f
        y = dp(56)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()

    private companion object {
        const val VISIBLE_MS = 2_500L
    }
}
