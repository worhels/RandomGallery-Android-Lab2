package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GalleryNavigationInstrumentedTest {
    @Test
    public void localPhotoCanOpenDetailsSurviveRecreationAndReturnWithSystemBack() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GalleryApplication application = (GalleryApplication) context.getApplicationContext();
        GalleryStorageImpl storage = application.getGalleryStorage();
        long previousTimestamp = storage.getTimestamp();
        String identifier = "stable_1___picsum-navigation-" + UUID.randomUUID().toString().replace("-", "");
        Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        try {
            try (OutputStream output = storage.write(identifier, "Фото для навігації")) {
                output.write(bytes.toByteArray());
            }
            storage.setTimestamp(1);
            assertEquals(1, application.getGalleryClient().getLocalGallery().size());

            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                View firstPhoto = waitForFirstPhoto(scenario);
                scenario.onActivity(activity -> firstPhoto.performClick());
                scenario.onActivity(activity -> {
                    Fragment current = activity.getSupportFragmentManager()
                            .findFragmentById(R.id.fragment_container);
                    assertTrue(current instanceof DetailsFragment);
                    assertEquals(1, activity.getSupportFragmentManager().getBackStackEntryCount());
                });

                scenario.recreate();
                scenario.onActivity(activity -> assertTrue(activity.getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container) instanceof DetailsFragment));

                scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
                scenario.onActivity(activity -> {
                    assertTrue(activity.getSupportFragmentManager()
                            .findFragmentById(R.id.fragment_container) instanceof GalleryFragment);
                    assertEquals(0, activity.getSupportFragmentManager().getBackStackEntryCount());
                });
            }
        } finally {
            storage.delete(identifier);
            storage.setTimestamp(previousTimestamp);
        }
    }

    private static View waitForFirstPhoto(ActivityScenario<MainActivity> scenario) throws InterruptedException {
        AtomicReference<View> result = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container);
                if (fragment != null && fragment.getView() != null) {
                    RecyclerView recycler = fragment.getView().findViewById(R.id.gallery_recycler);
                    if (recycler != null) {
                        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0);
                        if (holder != null) result.set(holder.itemView);
                    }
                }
            });
            if (result.get() == null) Thread.sleep(100);
        }
        assertNotNull("The saved photo should appear in the gallery grid", result.get());
        return result.get();
    }
}
