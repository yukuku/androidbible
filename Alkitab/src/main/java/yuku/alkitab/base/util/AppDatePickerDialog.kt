package yuku.alkitab.base.util

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import yuku.alkitab.debug.R

class AppDatePickerDialog(
    context: Context,
    listener: DatePickerDialog.OnDateSetListener,
    year: Int,
    month: Int,
    dayOfMonth: Int,
) : DatePickerDialog(context, listener, year, month, dayOfMonth) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getButton(BUTTON_POSITIVE).setText(R.string.ok)
    }
}
