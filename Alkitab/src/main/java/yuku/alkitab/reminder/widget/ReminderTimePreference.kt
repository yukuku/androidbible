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

    private fun formatForPersistence(hour: Int, minute: Int) = String.format(Locale.US, "%02d%02d", hour, minute)

    override fun shouldDisableDependents(): Boolean {
        return getPersistedString(null) == null
    }
}
