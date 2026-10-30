package ua.cn.stu.randomgallery.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

final class NotificationHelper {
    static final String CHANNEL_ID = "gallery_updates";
    static final int UPDATE_NOTIFICATION_ID = 4101;
    static final int SYNC_NOTIFICATION_ID = 4102;
    private static final int CONFIRM_REQUEST_CODE = 4111;
    private static final int CANCEL_PROMPT_REQUEST_CODE = 4112;
    private static final int CANCEL_SYNC_REQUEST_CODE = 4113;
    private static final int CHECK_UPDATES_REQUEST_CODE = 4114;

    private NotificationHelper() { }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    static boolean canPost(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static void showUpdateAvailable(Context context) {
        showChoice(context, R.string.notification_available_title,
                R.string.notification_available_message, R.string.notification_confirm,
                GalleryActionReceiver.ACTION_CONFIRM_SYNC, CONFIRM_REQUEST_CODE);
    }

    static void showBootPrompt(Context context) {
        postChoice(context, buildBootPrompt(context), UPDATE_NOTIFICATION_ID);
    }

    static NotificationCompat.Builder buildBootPrompt(Context context) {
        ensureChannel(context);
        PendingIntent confirm = action(context, GalleryActionReceiver.ACTION_CHECK_UPDATES,
                CHECK_UPDATES_REQUEST_CODE);
        PendingIntent cancel = action(context, GalleryActionReceiver.ACTION_DISMISS_UPDATE,
                CANCEL_PROMPT_REQUEST_CODE);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_boot_title))
                .setContentText(context.getString(R.string.notification_boot_message))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, context.getString(R.string.notification_check), confirm)
                .addAction(0, context.getString(R.string.notification_cancel), cancel);
    }

    private static void showChoice(Context context, int title, int message, int confirmLabel,
                                   String confirmAction, int confirmRequestCode) {
        PendingIntent confirm = action(context, confirmAction, confirmRequestCode);
        PendingIntent cancel = action(context, GalleryActionReceiver.ACTION_DISMISS_UPDATE,
                CANCEL_PROMPT_REQUEST_CODE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(message))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, context.getString(confirmLabel), confirm)
                .addAction(0, context.getString(R.string.notification_cancel), cancel);
        postChoice(context, notification, UPDATE_NOTIFICATION_ID);
    }

    private static void postChoice(Context context, NotificationCompat.Builder notification, int notificationId) {
        if (!canPost(context)) return;
        ensureChannel(context);
        NotificationManagerCompat.from(context).notify(notificationId, notification.build());
    }

    static void showSyncProgress(Context context, int progress) {
        if (!canPost(context)) return;
        ensureChannel(context);
        PendingIntent cancel = action(context, GalleryActionReceiver.ACTION_CANCEL_SYNC,
                CANCEL_SYNC_REQUEST_CODE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_sync_title))
                .setContentText(context.getString(R.string.notification_sync_message, progress))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, false)
                .addAction(0, context.getString(R.string.notification_cancel), cancel);
        NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification.build());
    }

    static void cancelUpdatePrompt(Context context) {
        NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID);
    }

    static void cancelSyncNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(SYNC_NOTIFICATION_ID);
    }

    private static PendingIntent action(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, GalleryActionReceiver.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
