package ua.cn.stu.randomgallery.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.d("GalleryBoot", "BOOT_COMPLETED received; notificationsAllowed="
                + NotificationHelper.canPost(context));
        NotificationHelper.ensureChannel(context);
        NotificationHelper.showBootPrompt(context);
    }
}
