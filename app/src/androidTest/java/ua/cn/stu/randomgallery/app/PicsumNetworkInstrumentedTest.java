package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import ua.cn.stu.randomgallery.LocalPhoto;
import ua.cn.stu.randomgallery.PicsumGallerySource;
import ua.cn.stu.randomgallery.RandomGalleryClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class PicsumNetworkInstrumentedTest {
    @Test(timeout = 180000)
    public void picsumSourceDownloadsAndPersistsARealImage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Skipped: emulator has no validated internet connection", hasValidatedInternet(context));

        String suffix = UUID.randomUUID().toString().replace("-", "");
        File directory = new File(context.getFilesDir(), "picsum-instrumented-" + suffix);
        String preferencesName = "picsum-instrumented-" + suffix;
        GalleryStorageImpl storage = new GalleryStorageImpl(directory,
                new SharedPreferencesStorageMetadata(context.getSharedPreferences(preferencesName,
                        Context.MODE_PRIVATE)));
        PicsumGallerySource source = new PicsumGallerySource("random-gallery-device-check-" + suffix,
                600_000L, 1, 320, PicsumGallerySource.DEFAULT_BASE_URL);
        RandomGalleryClient client = new RandomGalleryClient(source, storage);
        AtomicInteger lastProgress = new AtomicInteger(-1);
        try {
            assertTrue("Picsum should provide a gallery for the new local store",
                    client.syncGallery(lastProgress::set));
            assertEquals(100, lastProgress.get());
            List<LocalPhoto> photos = client.getLocalGallery();
            assertEquals(1, photos.size());
            try (InputStream image = photos.get(0).openImage()) {
                Bitmap bitmap = BitmapFactory.decodeStream(image);
                assertNotNull("The downloaded file must contain a decodable image", bitmap);
                assertTrue(bitmap.getWidth() > 0 && bitmap.getHeight() > 0);
                bitmap.recycle();
            }
            assertTrue(storage.isExists(photos.get(0).getLocalId()));
        } finally {
            for (String identifier : storage.getAll()) storage.delete(identifier);
            context.deleteSharedPreferences(preferencesName);
            deleteRecursively(directory);
        }
    }

    private static boolean hasValidatedInternet(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network active = manager.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
