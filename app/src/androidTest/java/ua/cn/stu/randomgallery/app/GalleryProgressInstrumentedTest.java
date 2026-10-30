package ua.cn.stu.randomgallery.app;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GalleryProgressInstrumentedTest {
    @Test
    public void progressTextReflectsSnapshotInPortraitAndLandscapeAndCompletesAtOneHundred() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName(),
                    android.Manifest.permission.POST_NOTIFICATIONS);
        }
        GalleryApplication application = (GalleryApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            application.getSyncState().setScheduled(false, false);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                TextView progressText = activity.findViewById(R.id.sync_progress_text);
                TextView statusText = activity.findViewById(R.id.sync_status);
                ProgressBar progressBar = activity.findViewById(R.id.sync_progress);
                assertEquals(activity.getString(R.string.sync_waiting_for_internet), statusText.getText().toString());
                assertTrue("A scheduled job should be indeterminate while waiting", progressBar.isIndeterminate());
                assertTrue("Waiting state must not invent a percentage",
                        progressText.getVisibility() == android.view.View.GONE);
            });
            application.getSyncState().setRunning();
            application.getSyncState().setProgress(37);
            assertProgress(scenario, "37%", 37);

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE);
            assertProgress(scenario, "37%", 37);

            application.getSyncState().setSuccess();
            assertProgress(scenario, "100%", 100);

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT);
            assertProgress(scenario, "100%", 100);
        } finally {
            application.getSyncState().setCancelled();
        }
    }

    private static void assertProgress(ActivityScenario<MainActivity> scenario, String text, int progress) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            TextView progressText = activity.findViewById(R.id.sync_progress_text);
            ProgressBar progressBar = activity.findViewById(R.id.sync_progress);
            assertEquals(text, progressText.getText().toString().replace("Завантажено: ", ""));
            assertEquals(progress, progressBar.getProgress());
        });
    }

    private static void waitForOrientation(ActivityScenario<MainActivity> scenario, int orientation) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] matched = {false};
            scenario.onActivity(activity -> matched[0] = activity.getResources()
                    .getConfiguration().orientation == orientation);
            if (matched[0]) return;
            android.os.SystemClock.sleep(50);
        }
        assertTrue("The requested orientation should be applied", false);
    }
}
