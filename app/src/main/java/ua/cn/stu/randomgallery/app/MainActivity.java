package ua.cn.stu.randomgallery.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

public final class MainActivity extends AppCompatActivity implements GalleryNavigation {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1401;
    private boolean notificationPermissionRequestInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new GalleryFragment())
                    .commit();
            root.post(this::requestNotificationPermission);
        }
        ActionsHandlerService.startCheck(this);
    }

    @Override
    public void openDetails(View source, String localPhotoId) {
        DetailsFragment details = DetailsFragment.newInstance(localPhotoId);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, details)
                .addToBackStack("photo-details")
                .commit();
    }

    @Override
    public void back() {
        if (!getSupportFragmentManager().popBackStackImmediate()) finish();
    }

    @Override
    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || notificationPermissionRequestInFlight) return;
        notificationPermissionRequestInFlight = true;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            notificationPermissionRequestInFlight = false;
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(findViewById(R.id.main_root), R.string.notification_permission_needed,
                        Snackbar.LENGTH_LONG).show();
            }
        }
    }
}
