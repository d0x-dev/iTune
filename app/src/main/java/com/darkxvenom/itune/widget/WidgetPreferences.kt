package com.darkxvenom.itune.widget

import android.content.Context

/** User customization shared by the settings preview and AppWidget RemoteViews renderer. */
object WidgetPreferences {
    private const val FILE = "itune_widget_customization"
    const val SIZE_COMPACT = "compact"
    const val SIZE_STANDARD = "standard"
    const val SIZE_EXPANDED = "expanded"
    const val STYLE_ARTWORK = "artwork"
    const val STYLE_DARK = "dark"
    const val STYLE_LIGHT = "light"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun size(context: Context) = prefs(context).getString("size", SIZE_STANDARD) ?: SIZE_STANDARD
    fun style(context: Context) = prefs(context).getString("style", STYLE_ARTWORK) ?: STYLE_ARTWORK
    fun showArtwork(context: Context) = prefs(context).getBoolean("artwork", true)
    fun showControls(context: Context) = prefs(context).getBoolean("controls", true)
    fun showFavorite(context: Context) = prefs(context).getBoolean("favorite", true)
    fun blurArtworkBackground(context: Context) = prefs(context).getBoolean("blur_artwork_background", false)

    fun setSize(context: Context, value: String) = prefs(context).edit().putString("size", value).apply()
    fun setStyle(context: Context, value: String) = prefs(context).edit().putString("style", value).apply()
    fun setArtwork(context: Context, value: Boolean) = prefs(context).edit().putBoolean("artwork", value).apply()
    fun setControls(context: Context, value: Boolean) = prefs(context).edit().putBoolean("controls", value).apply()
    fun setFavorite(context: Context, value: Boolean) = prefs(context).edit().putBoolean("favorite", value).apply()
    fun setBlurArtworkBackground(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("blur_artwork_background", value).apply()
}
