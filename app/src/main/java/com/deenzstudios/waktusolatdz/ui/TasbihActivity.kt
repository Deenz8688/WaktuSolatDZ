package com.deenzstudios.waktusolatdz.ui

import android.os.Bundle
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.databinding.ScreenTasbihBinding

class TasbihActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ScreenTasbihBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val preferences = getSharedPreferences("tasbih", MODE_PRIVATE)
        var count = preferences.getLong("count", 0L).coerceAtLeast(0L)
        var target = preferences.getInt("target", 33).let { if (it == 33 || it == 99) it else 0 }

        fun updateGradient() {
            val textWidth = binding.tasbihCount.paint.measureText(binding.tasbihCount.text.toString()).coerceAtLeast(1f)
            val textStart = ((binding.tasbihCount.width - textWidth) / 2f).coerceAtLeast(0f)
            binding.tasbihCount.paint.shader = LinearGradient(
                textStart, 0f, textStart + textWidth, 0f,
                intArrayOf(
                    ContextCompat.getColor(this, R.color.home_hero_start),
                    ContextCompat.getColor(this, R.color.home_hero_center),
                    ContextCompat.getColor(this, R.color.home_hero_end),
                ),
                null, Shader.TileMode.CLAMP,
            )
            binding.tasbihCount.invalidate()
        }

        fun updateCount() {
            binding.tasbihCount.text = count.toString()
            binding.tasbihCount.contentDescription = getString(R.string.tasbih_count_description, count)
            updateGradient()
        }

        binding.tasbihCount.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateGradient() }
        binding.backButton.setOnClickListener { finish() }
        binding.targetGroup.check(when (target) {
            99 -> R.id.target_99
            0 -> R.id.target_free
            else -> R.id.target_33
        })
        binding.targetGroup.setOnCheckedChangeListener { _, checkedId ->
            target = when (checkedId) {
                R.id.target_99 -> 99
                R.id.target_free -> 0
                else -> 33
            }
            preferences.edit().putInt("target", target).apply()
            updateCount()
        }
        binding.tasbihCount.setOnClickListener {
            if (count < Long.MAX_VALUE) count++
            preferences.edit().putLong("count", count).apply()
            updateCount()
        }
        binding.resetButton.setOnClickListener {
            count = 0L
            preferences.edit().putLong("count", count).apply()
            updateCount()
        }
        updateCount()
    }
}
