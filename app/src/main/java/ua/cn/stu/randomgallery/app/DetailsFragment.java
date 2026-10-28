package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ua.cn.stu.randomgallery.GalleryListener;
import ua.cn.stu.randomgallery.LocalPhoto;

public final class DetailsFragment extends Fragment implements GalleryListener {
    private static final String ARG_LOCAL_PHOTO_ID = "local_photo_id";

    private GalleryNavigation navigation;
    private GalleryApplication application;
    private ImageView photoImage;
    private TextView photoTitle;
    private ExecutorService imageExecutor;

    public static DetailsFragment newInstance(String localPhotoId) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_LOCAL_PHOTO_ID, localPhotoId);
        DetailsFragment fragment = new DetailsFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof GalleryNavigation)) {
            throw new IllegalStateException("Host Activity must implement GalleryNavigation");
        }
        navigation = (GalleryNavigation) context;
        application = (GalleryApplication) context.getApplicationContext();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        photoImage = view.findViewById(R.id.photo_image);
        photoTitle = view.findViewById(R.id.photo_title);
        view.findViewById(R.id.button_back).setOnClickListener(button -> navigation.back());
        imageExecutor = Executors.newSingleThreadExecutor();
        application.getGalleryClient().addListener(this);
    }

    @Override
    public void onGotGalleryPhotos(List<LocalPhoto> photos) {
        String requestedId = getArguments() == null ? null : getArguments().getString(ARG_LOCAL_PHOTO_ID);
        LocalPhoto selected = null;
        for (LocalPhoto photo : photos) {
            if (photo.getLocalId().equals(requestedId)) {
                selected = photo;
                break;
            }
        }
        if (selected == null) {
            MainThreadHandler.run(() -> {
                if (isAdded()) navigation.back();
            });
            return;
        }
        LocalPhoto photo = selected;
        MainThreadHandler.run(() -> {
            if (photoTitle != null) photoTitle.setText(photo.getName());
        });
        ExecutorService executor = imageExecutor;
        if (executor == null) return;
        executor.execute(() -> {
            try (InputStream input = photo.openImage()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                MainThreadHandler.run(() -> {
                    if (photoImage != null && bitmap != null) photoImage.setImageBitmap(bitmap);
                });
            } catch (IOException ignored) {
                MainThreadHandler.run(() -> {
                    if (photoTitle != null) photoTitle.setText(R.string.sync_error);
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        application.getGalleryClient().removeListener(this);
        if (imageExecutor != null) imageExecutor.shutdownNow();
        imageExecutor = null;
        photoImage = null;
        photoTitle = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        navigation = null;
        application = null;
        super.onDetach();
    }
}
