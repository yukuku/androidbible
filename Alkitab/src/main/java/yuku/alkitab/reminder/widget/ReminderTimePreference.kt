package yuku.alkitab.reminder.widget

import android.content.Context
import android.os.Build
import android.text.InputType
import android.text.format.DateFormat
import android.util.AttributeSet
import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import yuku.alkitab.debug.R
import java.util.Locale

class ReminderTimePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyle: Int = 0) : Preference(context, attrs, defStyle) {
    override fun onClick() {
        super.onClick()

        val currentValue = getPersistedString(null)

        val listener = object : HackedTimePickerDialog.HackedTimePickerListener {
            override fun onTimeSet(hourOfDay: Int, minute: Int) {
                persistString(formatForPersistence(hourOfDay, minute))
                notifyChanged()
            }

            override fun onTimeOff() {
                persistString(null)
                notifyChanged()
            }
        }

        // Prevent crash on Samsung devices with Android 5 or 6.
        // https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/a78fd0b7e6cab29504bfb2697d3d026c
        if (Build.MANUFACTURER.toLowerCase(Locale.US) == "samsung" && Build.VERSION.SDK_INT in 21..23) {
            fun showFallbackDialog() {
                MaterialDialog.Builder(context)
                    .inputType(InputType.TYPE_CLASS_NUMBER)
                    .input(context.getText(R.string.dr_timepicker_fallback_hint), currentValue ?: "", false) { _, input ->
                        val hhmm = input.toString().trim()
                        val valid = when {
                            hhmm.length != 4 -> false
                            (hhmm.substring(0, 2).toIntOrNull() ?: -1) !in 0..23 -> false
                            (hhmm.substring(2, 4).toIntOrNull() ?: -1) !in 0..59 -> false
                            else -> true
                        }

                        if (valid) {
                            listener.onTimeSet(hhmm.substring(0, 2).toInt(), hhmm.substring(2, 4).toInt())
                        } else {
                            showFallbackDialog()
                        }
                    }
                    .positiveText(R.string.dr_timepicker_set)
                    .negativeText(R.string.dr_timepicker_off)
                    .onNegative { _, _ ->
                        listener.onTimeOff()
                    }
                    .show()
            }
            showFallbackDialog()
        } else {
            val hour = currentValue?.substring(0, 2)?.toIntOrNull() ?: 12
            val minute = currentValue?.substring(2, 4)?.toIntOrNull() ?: 0

            HackedTimePickerDialog(
                context,
                title,
                context.getString(R.string.dr_timepicker_set),
                context.getString(R.string.dr_timepicker_off),
                listener,
                hour,
                minute,
                DateFormat.is24HourFormat(context)
            ).show()
        }
    }

    private fun formatForPersistence(hour: Int, minute: Int) = String.format(Locale.US, "%02d%02d", hour, minute)

    override fun shouldDisableDependents(): Boolean {
        return getPersistedString(null) == null
    }
}
