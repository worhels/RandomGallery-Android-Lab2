package ua.cn.stu.randomgallery.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class GalleryActionReceiver extends BroadcastReceiver {
    public static final String ACTION_CONFIRM_SYNC = "ua.cn.stu.randomgallery.action.CONFIRM_SYNC";
    public static final String ACTION_DISMISS_UPDATE = "ua.cn.stu.randomgallery.action.DISMISS_UPDATE";
    public static final String ACTION_CANCEL_SYNC = "ua.cn.stu.randomgallery.action.CANCEL_SYNC";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_CONFIRM_SYNC.equals(action)) {
            SyncService.scheduleUpdate(context);
        } else if (ACTION_DISMISS_UPDATE.equals(action)) {
            NotificationHelper.cancelUpdatePrompt(context);
        } else if (ACTION_CANCEL_SYNC.equals(action)) {
            SyncService.cancelScheduledUpdate(context);
        }
    }
}
