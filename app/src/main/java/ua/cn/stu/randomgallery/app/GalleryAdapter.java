package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ua.cn.stu.randomgallery.LocalPhoto;

final class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.PhotoViewHolder> {
    interface OnPhotoClickListener {
        void onPhotoChosen(View source, LocalPhoto photo);
    }

    private final Context context;
    private final int columns;
    private final OnPhotoClickListener clickListener;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private final List<LocalPhoto> photos = new ArrayList<>();

    GalleryAdapter(Context context, int columns, OnPhotoClickListener clickListener) {
        this.context = context.getApplicationContext();
        this.columns = columns;
        this.clickListener = clickListener;
    }

    void submitList(List<LocalPhoto> newPhotos) {
        photos.clear();
        photos.addAll(newPhotos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        LocalPhoto photo = photos.get(position);
        String localId = photo.getLocalId();
        holder.boundId = localId;
        holder.title.setText(photo.getName());
        holder.image.setImageDrawable(null);
        holder.image.setBackgroundResource(R.color.gallery_surface_raised);
        holder.itemView.setOnClickListener(view -> clickListener.onPhotoChosen(view, photo));

        int width = context.getResources().getDisplayMetrics().widthPixels / columns;
        int targetHeight = Math.round(180 * context.getResources().getDisplayMetrics().density);
        imageExecutor.execute(() -> {
            Bitmap bitmap = decodeThumbnail(photo, width, targetHeight);
            if (bitmap == null) return;
            MainThreadHandler.run(() -> {
                if (localId.equals(holder.boundId)) {
                    holder.image.setImageBitmap(bitmap);
                    holder.image.setBackground(null);
                } else {
                    bitmap.recycle();
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    void close() {
        imageExecutor.shutdownNow();
    }

    private static Bitmap decodeThumbnail(LocalPhoto photo, int requestedWidth, int requestedHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = photo.openImage()) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (IOException exception) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds, requestedWidth, requestedHeight);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream input = photo.openImage()) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException exception) {
            return null;
        }
    }

    private static int calculateSampleSize(BitmapFactory.Options bounds, int width, int height) {
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= width
                && bounds.outHeight / (sampleSize * 2) >= height) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    static final class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        String boundId;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.photo_thumbnail);
            title = itemView.findViewById(R.id.photo_name);
        }
    }
}
