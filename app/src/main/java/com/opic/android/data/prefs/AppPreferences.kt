package com.opic.android.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 글로벌 앱 설정. opic_app_settings SharedPreferences 사용.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opic_app_settings", Context.MODE_PRIVATE)

    var textSize: Int
        get() = prefs.getInt("text_size", 18)
        set(v) = prefs.edit().putInt("text_size", v).apply()

    var levelImageDir: String
        get() = prefs.getString("level_image_dir", "") ?: ""
        set(v) = prefs.edit().putString("level_image_dir", v).apply()

    var soundDir: String
        get() = prefs.getString("sound_dir", "") ?: ""
        set(v) = prefs.edit().putString("sound_dir", v).apply()

    var targetGrade: String
        get() = prefs.getString("target_grade", "IM2") ?: "IM2"
        set(v) = prefs.edit().putString("target_grade", v).apply()

    var selectedVoice: String
        get() = prefs.getString("selected_voice", "") ?: ""
        set(v) = prefs.edit().putString("selected_voice", v).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "light") ?: "light"
        set(v) = prefs.edit().putString("theme_mode", v).apply()
}
