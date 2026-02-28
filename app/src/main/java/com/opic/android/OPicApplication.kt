package com.opic.android

import android.app.Application
import android.util.Log
import com.opic.android.audio.TtsManager
import com.opic.android.domain.StudyDecay
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Python Main.py 시작 시퀀스 대응:
 *   setup_database()    → Room.createFromAsset() (AppModule에서 처리)
 *   apply_study_decay() → StudyDecay.apply()
 *   pygame.mixer.init() → TtsManager.init()
 */
@HiltAndroidApp
class OPicApplication : Application() {

    @Inject lateinit var studyDecay: StudyDecay
    @Inject lateinit var ttsManager: TtsManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ttsManager.init()
        appScope.launch {
            try {
                val decayed = studyDecay.apply()
                Log.d("OPicApp", "StudyDecay 완료: ${decayed}개 항목 감소")
            } catch (e: Exception) {
                Log.e("OPicApp", "StudyDecay 실패", e)
            }
        }
    }
}
