package it.unipi.di.sam.overwave.receiver

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import it.unipi.di.sam.overwave.R
import it.unipi.di.sam.overwave.database.Transmission
import java.text.SimpleDateFormat

fun toWaveId(wave: String): Int = when(wave) {
    "light" -> 0
    "vibration" -> 1
    "screen brightness" -> 2
    else -> 3
}

@BindingAdapter("waveImage")
fun ImageView.setWaveImage(item: Transmission) {
    this.setImageResource(when(item.wave) {
        0 -> R.drawable.light
        1 -> R.drawable.vibration
        2 -> R.drawable.screen_brightness
        else -> R.drawable.sound
    })
}

@BindingAdapter("isSentTextVisible")
fun TextView.setIsSentTextVisible(item: Transmission) {
    this.visibility = if (item.sentMessage == null) View.GONE else View.VISIBLE
}

@BindingAdapter("sentText")
fun TextView.setSentText(item: Transmission) {
    this.text = if (item.sentMessage != null) "expected: ${item.sentMessage}" else ""
}

@BindingAdapter("elapsedTime")
fun TextView.setElapsedTime(item: Transmission) {
    this.text = "${(item.endTimeMillis - item.startTimeMillis) / 1000}s"
}

@BindingAdapter("date")
fun TextView.setDate(item: Transmission) {
    this.text = convertLongToDateString(item.startTimeMillis)
}

@SuppressLint("SimpleDateFormat")
fun convertLongToDateString(systemTime: Long): String {
    return SimpleDateFormat("MMM-dd-yyyy' Time: 'HH:mm")
        .format(systemTime).toString()
}
