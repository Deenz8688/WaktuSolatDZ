package com.deenzstudios.waktusolatdz.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.databinding.ScreenZikirBinding

class ZikirActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ScreenZikirBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Apply the bundled font on older Android versions as well.
        val arabicFont = ResourcesCompat.getFont(this, R.font.amiri_regular)
        fun applyArabicFont(view: View) {
            if (view is TextView && view.tag == "zikir_arabic") view.typeface = arabicFont
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) applyArabicFont(view.getChildAt(index))
            }
        }
        applyArabicFont(binding.root)
        binding.backButton.setOnClickListener { finish() }
    }
}
