package yuku.alkitab.base.sync

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import yuku.alkitab.base.util.AppLog

private const val TAG = "FcmMessagingService"

class FcmMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        AppLog.d(TAG, "@@onReceive from: ${remoteMessage.from}")

        val intent = Intent(this, FcmIntentService::class.java)

        // convert data map to intent extras
        remoteMessage.data.forEach { (k, v) ->
            if (v != null) {
                intent.putExtra(k, v)
            }
        }

        FcmIntentService.enqueueWork(this, intent)
    }

    override fun onNewToken(token: String) {
        AppLog.d(TAG, "Refreshed token: $token")

        Sync.notifyNewFcmRegistrationId(token)
    }
}