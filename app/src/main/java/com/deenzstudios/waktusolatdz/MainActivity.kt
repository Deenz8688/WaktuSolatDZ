package com.deenzstudios.waktusolatdz

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.deenzstudios.waktusolatdz.notification.PrayerNotifier
import com.deenzstudios.waktusolatdz.ui.MosqueViewModel
import com.deenzstudios.waktusolatdz.ui.PrayerScreen
import com.deenzstudios.waktusolatdz.ui.PrayerViewModel
import com.deenzstudios.waktusolatdz.ui.theme.WaktuSolatDZTheme
import com.deenzstudios.waktusolatdz.databinding.ActivityMainBinding
import com.deenzstudios.waktusolatdz.widget.PrayerWidgetProvider

class MainActivity : AppCompatActivity() {

    private val viewModel: PrayerViewModel by viewModels()
    private val mosqueViewModel: MosqueViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        PrayerWidgetProvider.updateAll(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Cipta channel awal supaya ia muncul dalam Tetapan Android walaupun belum berbunyi.
        PrayerNotifier.ensureChannels(this)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appContent.setContent {
            WaktuSolatDZTheme {
                PrayerScreen(viewModel = viewModel, mosqueViewModel = mosqueViewModel)
            }
        }
    }
}
