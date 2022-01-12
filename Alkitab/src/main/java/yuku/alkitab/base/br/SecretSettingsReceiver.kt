package yuku.alkitab.base.br

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import yuku.alkitab.base.ac.SecretSettingsActivity

class SecretSettingsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val startIntent = Intent(context, SecretSettingsActivity::class.java)
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(startIntent)
    }
}
