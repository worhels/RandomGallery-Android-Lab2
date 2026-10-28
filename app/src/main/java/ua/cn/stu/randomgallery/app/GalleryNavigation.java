package ua.cn.stu.randomgallery.app;

import android.view.View;

interface GalleryNavigation {
    void openDetails(View source, String localPhotoId);

    void back();

    void requestNotificationPermission();
}
