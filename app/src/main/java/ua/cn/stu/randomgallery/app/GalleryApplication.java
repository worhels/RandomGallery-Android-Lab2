package ua.cn.stu.randomgallery.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

import ua.cn.stu.randomgallery.PicsumGallerySource;
import ua.cn.stu.randomgallery.RandomGalleryClient;

public final class GalleryApplication extends Application {
    private static final String APP_PREFERENCES = "random_gallery";
    private static final String GALLERY_ID_KEY = "gallery_id";

    private GalleryStorageImpl galleryStorage;
    private RandomGalleryClient galleryClient;
    private SyncState syncState;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        String galleryId = preferences.getString(GALLERY_ID_KEY, null);
        if (galleryId == null) {
            galleryId = UUID.randomUUID().toString();
            preferences.edit().putString(GALLERY_ID_KEY, galleryId).apply();
        }
        galleryStorage = new GalleryStorageImpl(this);
        galleryClient = new RandomGalleryClient(new PicsumGallerySource(galleryId), galleryStorage);
        syncState = new SyncState();
        NotificationHelper.ensureChannel(this);
    }

    public GalleryStorageImpl getGalleryStorage() {
        return galleryStorage;
    }

    public RandomGalleryClient getGalleryClient() {
        return galleryClient;
    }

    public SyncState getSyncState() {
        return syncState;
    }
}
