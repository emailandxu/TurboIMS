package io.github.vvb2060.ims.privileged

import android.app.Activity
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.system.Os
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

class ImsResetter : Instrumentation() {
    companion object {
        private const val TAG = "ImsResetter"
        const val BUNDLE_SELECT_SIM_ID = "select_sim_id"
        const val BUNDLE_RESULT = "result"
        const val BUNDLE_RESULT_MSG = "result_msg"
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        if (arguments == null) {
            finish(Activity.RESULT_CANCELED, Bundle())
            return
        }

        val result = Bundle()
        var index = 0
        val maxRetries = 50
        while (!Shizuku.pingBinder()) {
            index++
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                break
            }
            if (index >= maxRetries) {
                break
            }
        }
        val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
        try {
            am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        } catch (t: Throwable) {
            Log.w(TAG, "startDelegateShellPermissionIdentity failed", t)
        }
        try {
            val subId = arguments.getInt(BUNDLE_SELECT_SIM_ID, -1)
            val sm = context.getSystemService(SubscriptionManager::class.java)
            val subIds: IntArray = if (subId == -1) {
                sm.javaClass.getMethod("getActiveSubscriptionIdList").invoke(sm) as IntArray
            } else {
                intArrayOf(subId)
            }

            val telephony = ITelephony.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .getTelephonyServiceRegisterer()
                        .get()!!
                )
            )
            val sub = ISub.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .getSubscriptionServiceRegisterer()
                        .get()!!
                )
            )

            for (id in subIds) {
                val slotIndex = sub.getSlotIndex(id)
                Log.i(TAG, "resetIms for subId $id slot $slotIndex")
                telephony.resetIms(slotIndex)
            }

            result.putBoolean(BUNDLE_RESULT, true)
        } catch (t: Throwable) {
            Log.e(TAG, "reset ims failed", t)
            result.putBoolean(BUNDLE_RESULT, false)
            result.putString(BUNDLE_RESULT_MSG, t.message ?: t.javaClass.simpleName)
        } finally {
            try {
                am.stopDelegateShellPermissionIdentity()
            } catch (t: Throwable) {
                Log.w(TAG, "stopDelegateShellPermissionIdentity failed", t)
            }
        }

        finish(Activity.RESULT_OK, result)
    }
}
