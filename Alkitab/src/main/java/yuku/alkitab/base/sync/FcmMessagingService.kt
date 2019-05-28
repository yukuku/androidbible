package yuku.alkitab.base.sync

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import yuku.alkitab.base.util.AppLog

private const val TAG = "FcmMessagingService"

class FcmMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        remoteMessage ?: return

        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        AppLog.d(TAG, "@@onReceive from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data == null) {
            AppLog.e(TAG, "@@onReceive remote message has no data")
            return
        }

        val intent = Intent(this, FcmIntentService::class.java)

        // convert data map to intent extras
        data.forEach { (k, v) ->
            if (v != null) {
                intent.putExtra(k, v)
            }
        }

        FcmIntentService.enqueueWork(this, intent)
    }

    override fun onNewToken(token: String?) {
        AppLog.d(TAG, "Refreshed token: $token")
        token ?: return

        Sync.notifyNewFcmRegistrationId(token)
    }
}