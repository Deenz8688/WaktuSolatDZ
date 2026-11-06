package com.deenzstudios.waktusolatdz

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.deenzstudios.waktusolatdz.notification.PrayerNotifier
import com.deenzstudios.waktusolatdz.ui.MosqueViewModel
import com.deenzstudios.waktusolatdz.ui.PrayerScreen
import com.deenzstudios.waktusolatdz.ui.PrayerViewModel
import com.deenzstudios.waktusolatdz.ui.theme.WaktuSolatDZTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PrayerViewModel by viewModels()
    private val mosqueViewModel: MosqueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Cipta channel awal supaya ia muncul dalam Tetapan Android walaupun belum berbunyi.
        PrayerNotifier.ensureChannels(this)
        setContent {
            WaktuSolatDZTheme {
                PrayerScreen(viewModel = viewModel, mosqueViewModel = mosqueViewModel)
            }
        }
    }
}
