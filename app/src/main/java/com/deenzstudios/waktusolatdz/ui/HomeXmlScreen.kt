package com.deenzstudios.waktusolatdz.ui

import android.content.Context
import android.animation.Animator
import android.animation.AnimatorInflater
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.IslamicEvents
import com.deenzstudios.waktusolatdz.data.PrayerTimes
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.data.formatGregorianDate
import com.deenzstudios.waktusolatdz.data.formatHijriDate
import com.deenzstudios.waktusolatdz.data.upcomingIslamicEvents
import com.deenzstudios.waktusolatdz.data.WeatherNow
import com.deenzstudios.waktusolatdz.data.WeatherRepository
import com.deenzstudios.waktusolatdz.databinding.ItemHomeIslamicEventBinding
import com.deenzstudios.waktusolatdz.databinding.ScreenHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/** Only the bridge to the existing navigation; the Home design lives in res/layout. */
@Composable
internal fun HomeXmlScreen(
    state: PrayerUiState,
    nextName: String,
    nextTime: String,
    countdown: String,
    onRetry: () -> Unit,
) {
    AndroidView(
        factory = { HomeXmlView(it) },
        modifier = Modifier.fillMaxSize(),
        update = { it.render(state, nextName, nextTime, countdown, onRetry) },
    )
}

/** Binds real timetable data and actions. Colors, spacing, shapes and fonts are XML. */
private class HomeXmlView(context: Context) : FrameLayout(context) {
    private val binding = ScreenHomeBinding.inflate(LayoutInflater.from(context), this, true)
    private val content = binding
    private var renderedCalendar: Pair<String, String>? = null
    private var weatherTimes: PrayerTimes? = null
    private var weatherZone: String? = null
    private var weatherJob: Job? = null
    private var weatherGeneration = 0
    private var weather: WeatherNow? = null
    private var animationKey: Pair<WeatherNow.Kind, Boolean>? = null
    private val weatherAnimations = mutableListOf<Animator>()

    init {
        binding.eventScroll.setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(
                event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL,
            )
            false
        }
    }

    fun render(
        state: PrayerUiState,
        nextName: String,
        nextTime: String,
        countdown: String,
        onRetry: () -> Unit,
    ) {
        binding.retryButton.setOnClickListener { onRetry() }
        val success = state is PrayerUiState.Success
        binding.statusCard.isVisible = !success
        binding.homeContent.isVisible = success
        binding.loadingProgress.isVisible = state is PrayerUiState.Loading
        binding.errorMessage.isVisible = state is PrayerUiState.Error
        binding.retryButton.isVisible = state is PrayerUiState.Error
        binding.statusTitle.setText(
            if (state is PrayerUiState.Error) R.string.home_error_heading else R.string.home_loading,
        )
        binding.errorMessage.text = (state as? PrayerUiState.Error)?.message.orEmpty()
        if (state !is PrayerUiState.Success) {
            stopWeatherAnimations()
            return
        }

        val times = state.times
        val zone = PrayerZones.of(times.zone)
        content.zoneDistrict.text = zone?.district ?: times.zone
        content.zoneState.text = zone?.state ?: context.getString(R.string.home_zone_fallback)
        content.zoneCode.text = times.zone
        content.nextName.text = nextName
        content.nextTime.text = nextTime
        content.countdown.text = countdown
        content.gregorianDate.text = context.getString(
            R.string.home_date, times.day, formatGregorianDate(times.date),
        )
        content.hijriDate.text = formatHijriDate(times.hijriDate)

        bindWeather(times)
        bindCalendar(times)
    }

    private fun bindWeather(times: PrayerTimes) {
        weatherTimes = times
        if (weatherZone != times.zone) {
            weatherZone = times.zone
            weather = null
            weatherJob?.cancel()
            if (isAttachedToWindow) startWeatherUpdates()
        }
        renderWeather()
    }

    /** Requests are scoped to Home and pause when the Activity stops. */
    private fun startWeatherUpdates() {
        val generation = ++weatherGeneration
        weatherJob?.cancel()
        val zone = PrayerZones.of(weatherZone.orEmpty()) ?: return
        val owner = findViewTreeLifecycleOwner() ?: return
        weatherJob = owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    renderWeather()
                    while (isActive) {
                        val current = WeatherRepository.current(zone.latitude, zone.longitude)
                        ensureActive()
                        weather = current
                        renderWeather()
                        delay(30 * 60_000L)
                    }
                } finally {
                    if (generation == weatherGeneration) stopWeatherAnimations()
                }
            }
        }
    }

    private fun renderWeather() {
        val current = weather
        binding.weatherGroup.isVisible = current != null
        if (current == null) {
            stopWeatherAnimations()
            weatherAnimations.clear()
            animationKey = null
            return
        }
        val times = weatherTimes ?: return
        val now = Calendar.getInstance().minutesOfDay()
        val night = now < minutesOfDay(times.syuruk) || now >= minutesOfDay(times.maghrib)
        val clear = current.kind == WeatherNow.Kind.Sunny || current.kind == WeatherNow.Kind.Partly
        binding.weatherSun.isVisible = clear && !night
        binding.weatherMoon.isVisible = clear && night
        binding.weatherCloud.isVisible = current.kind != WeatherNow.Kind.Sunny
        binding.weatherRain.isVisible = current.kind == WeatherNow.Kind.Rain
        val description = when (current.kind) {
            WeatherNow.Kind.Sunny -> if (night) R.string.home_weather_clear_night else R.string.home_weather_sunny
            WeatherNow.Kind.Partly -> R.string.home_weather_partly
            WeatherNow.Kind.Cloudy -> R.string.home_weather_cloudy
            WeatherNow.Kind.Rain -> R.string.home_weather_rain
        }
        binding.weatherGroup.contentDescription = context.getString(description)

        val key = current.kind to night
        if (animationKey != key) {
            stopWeatherAnimations()
            weatherAnimations.clear()
            fun addAnimation(resource: Int, target: View) {
                weatherAnimations += AnimatorInflater.loadAnimator(context, resource).apply { setTarget(target) }
            }
            if (binding.weatherSun.isVisible) addAnimation(R.animator.weather_sun_rotate, binding.weatherSun)
            if (binding.weatherMoon.isVisible) addAnimation(R.animator.weather_moon_glow, binding.weatherMoon)
            if (binding.weatherCloud.isVisible) addAnimation(R.animator.weather_cloud_drift, binding.weatherCloud)
            if (binding.weatherRain.isVisible) addAnimation(R.animator.weather_rain_fall, binding.weatherRain)
            animationKey = key
        }
        startWeatherAnimations()
    }

    private fun startWeatherAnimations() {
        val started = findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        if (!isAttachedToWindow || !hasWindowFocus() || !binding.weatherGroup.isShown || !started) return
        weatherAnimations.filterNot { it.isStarted }.forEach { it.start() }
    }

    private fun stopWeatherAnimations() {
        weatherAnimations.forEach { it.cancel() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (weatherZone != null) startWeatherUpdates()
    }

    override fun onDetachedFromWindow() {
        weatherGeneration++
        weatherJob?.cancel()
        weatherJob = null
        stopWeatherAnimations()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) startWeatherAnimations() else stopWeatherAnimations()
    }

    private fun bindCalendar(times: PrayerTimes) {
        val key = times.hijriDate to times.date
        if (renderedCalendar == key) return
        val events = upcomingIslamicEvents(times.hijriDate, times.date, count = IslamicEvents.size)
        content.calendarSection.isVisible = events.isNotEmpty()
        content.eventItems.removeAllViews()
        val inflater = LayoutInflater.from(context)
        events.forEach { event ->
            val item = ItemHomeIslamicEventBinding.inflate(inflater, content.eventItems, false)
            item.daysAway.text = if (event.daysAway == 0) context.getString(R.string.home_today)
                else context.getString(R.string.home_days_away, event.daysAway)
            item.name.text = event.name
            item.dates.text = event.hijriLabel
            content.eventItems.addView(item.root)
        }
        content.eventScroll.doOnLayout { scroll ->
            val count = minOf(resources.getInteger(R.integer.home_calendar_visible_count), content.eventItems.childCount)
            val visibleHeight = (0 until count).sumOf { content.eventItems.getChildAt(it).measuredHeight } +
                content.eventItems.paddingTop + content.eventItems.paddingBottom + scroll.paddingTop + scroll.paddingBottom
            if (visibleHeight > 0 && scroll.layoutParams.height != visibleHeight) {
                scroll.layoutParams = scroll.layoutParams.apply { height = visibleHeight }
            }
        }
        renderedCalendar = key
    }
}
