package ua.cn.stu.randomgallery.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.app.NotificationManager;
import android.content.Intent;
import android.app.Notification;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class NotificationActionsInstrumentedTest {
    @Test
    public void bootPromptConfirmationChecksUpdatesBeforeShowingAvailableMessage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(context.getPackageName(),
                    android.Manifest.permission.POST_NOTIFICATIONS);
        }
        GalleryApplication application = (GalleryApplication) context.getApplicationContext();
        long previousTimestamp = application.getGalleryStorage().getTimestamp();
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ActionsHandlerService.cancelPendingCheck(context);
        SyncService.cancelScheduledUpdate(context);
        application.getGalleryStorage().setTimestamp(0);

        AtomicBoolean checkingObserved = new AtomicBoolean();
        CountDownLatch checkFinished = new CountDownLatch(1);
        SyncState.Observer observer = snapshot -> {
            if (snapshot.status == SyncState.Status.CHECKING) checkingObserved.set(true);
            if (checkingObserved.get() && (snapshot.status == SyncState.Status.UPDATE_AVAILABLE
                    || snapshot.status == SyncState.Status.IDLE || snapshot.status == SyncState.Status.ERROR)) {
                checkFinished.countDown();
            }
        };
        application.getSyncState().addObserver(observer);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
            Notification prompt = NotificationHelper.buildBootPrompt(context).build();
            assertEquals(context.getString(R.string.notification_boot_title), prompt.extras.getCharSequence(
                    android.app.Notification.EXTRA_TITLE).toString());
            assertEquals(context.getString(R.string.notification_boot_message), prompt.extras.getCharSequence(
                    android.app.Notification.EXTRA_TEXT).toString());
            assertEquals(2, prompt.actions.length);

            prompt.actions[0].actionIntent.send();
            assertTrue("Confirmation should start the background update check",
                    checkFinished.await(20, TimeUnit.SECONDS));
            assertTrue(checkingObserved.get());
            assertEquals(SyncState.Status.UPDATE_AVAILABLE,
                    application.getSyncState().getSnapshot().status);
            assertNull("Checking for updates must not start a download job",
                    scheduler.getPendingJob(SyncService.JOB_ID));
        } finally {
            application.getSyncState().removeObserver(observer);
            application.getGalleryStorage().setTimestamp(previousTimestamp);
            SyncService.cancelScheduledUpdate(context);
            NotificationHelper.cancelUpdatePrompt(context);
        }
    }

    @Test
    public void bootPromptCancellationDismissesPromptWithoutStartingCheck() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(context.getPackageName(),
                    android.Manifest.permission.POST_NOTIFICATIONS);
        }
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        ActionsHandlerService.cancelPendingCheck(context);
        NotificationHelper.showBootPrompt(context);
        assertTrue("The boot prompt should be posted when notification permission is granted",
                hasNotification(manager, NotificationHelper.UPDATE_NOTIFICATION_ID));

        NotificationHelper.buildBootPrompt(context).build().actions[1].actionIntent.send();
        waitForPromptDismissal(manager);

        assertFalse("Cancel must dismiss the prompt",
                hasNotification(manager, NotificationHelper.UPDATE_NOTIFICATION_ID));
        assertNull("Cancel must not schedule an update check",
                scheduler.getPendingJob(ActionsHandlerService.UPDATE_CHECK_JOB_ID));
        assertNull("Cancel must not schedule a photo download",
                scheduler.getPendingJob(SyncService.JOB_ID));
    }

    @Test
    public void updateActionsPostNotificationAndScheduleThenCancelOfflineJob() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(context.getPackageName(),
                    android.Manifest.permission.POST_NOTIFICATIONS);
        }

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        GalleryApplication application = (GalleryApplication) context.getApplicationContext();
        GalleryActionReceiver receiver = new GalleryActionReceiver();
        boolean wifiWasEnabled = isWifiEnabled(automation);
        try {
            runShell(automation, "cmd connectivity airplane-mode enable");
            runShell(automation, "svc wifi disable");
            waitForNetworkLoss(context);
            SyncService.cancelScheduledUpdate(context);

            NotificationHelper.showUpdateAvailable(context);

            receiver.onReceive(context, new Intent(GalleryActionReceiver.ACTION_CONFIRM_SYNC));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            JobInfo job = scheduler.getPendingJob(SyncService.JOB_ID);
            assertNotNull("Confirm should schedule a job while offline", job);
            assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, job.getNetworkType());
            SyncState.Snapshot scheduled = application.getSyncState().getSnapshot();
            assertEquals(SyncState.Status.SCHEDULED, scheduled.status);
            assertFalse("The scheduled status should report missing internet", scheduled.hasInternet);

            receiver.onReceive(context, new Intent(GalleryActionReceiver.ACTION_DISMISS_UPDATE));
            receiver.onReceive(context, new Intent(GalleryActionReceiver.ACTION_CANCEL_SYNC));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNull(scheduler.getPendingJob(SyncService.JOB_ID));
            assertEquals(SyncState.Status.CANCELLED, application.getSyncState().getSnapshot().status);
        } finally {
            SyncService.cancelScheduledUpdate(context);
            runShell(automation, "cmd connectivity airplane-mode disable");
            if (wifiWasEnabled) {
                runShell(automation, "svc wifi enable");
                waitForNetworkRestore(context);
            }
        }
    }

    private static boolean isWifiEnabled(android.app.UiAutomation automation) throws Exception {
        String output = runShell(automation, "settings get global wifi_on").trim();
        return "1".equals(output);
    }

    private static boolean hasNotification(NotificationManager manager, int id) {
        if (manager == null) return false;
        for (android.service.notification.StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getId() == id) return true;
        }
        return false;
    }

    private static void waitForPromptDismissal(NotificationManager manager) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (hasNotification(manager, NotificationHelper.UPDATE_NOTIFICATION_ID)
                && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(100);
        }
    }

    private static void waitForNetworkLoss(Context context) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline && NetworkStatus.read(context).hasInternet) {
            Thread.sleep(250);
        }
        assertTrue("The emulator should be offline for the deferred-job check",
                !NetworkStatus.read(context).hasInternet);
    }

    private static void waitForNetworkRestore(Context context) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && !NetworkStatus.read(context).hasInternet) {
            Thread.sleep(250);
        }
        assertTrue("The emulator's original network should be restored after the offline check",
                NetworkStatus.read(context).hasInternet);
    }

    private static String runShell(android.app.UiAutomation automation, String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     new FileInputStream(descriptor.getFileDescriptor()), StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
            return output.toString();
        }
    }

}
