package yuku.alkitab.songs

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import yuku.alkitab.base.widget.MaterialDialogProgressHelper.progress

object SongBookUtilJavaHelper {
    @JvmStatic
    fun showProgressDialog(context: Context, message: String): MaterialDialog {
        return MaterialDialog(context).show {
            message(text = message)
            progress(true, 0)
        }
    }
}
