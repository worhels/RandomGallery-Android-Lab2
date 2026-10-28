package ua.cn.stu.randomgallery.app;

import android.os.Handler;
import android.os.Looper;

final class MainThreadHandler {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private MainThreadHandler() { }

    static void run(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else HANDLER.post(action);
    }
}
