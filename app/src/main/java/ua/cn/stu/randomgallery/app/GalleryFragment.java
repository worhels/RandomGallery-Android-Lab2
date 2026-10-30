package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.ArrayList;

import ua.cn.stu.randomgallery.GalleryListener;
import ua.cn.stu.randomgallery.LocalPhoto;
import ua.cn.stu.randomgallery.RandomGalleryClient;

public final class GalleryFragment extends Fragment implements GalleryListener, SyncState.Observer {
    private GalleryNavigation navigation;
    private GalleryApplication application;
    private GalleryAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView statusView;
    private TextView progressTextView;
    private ProgressBar progressBar;

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
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.gallery_recycler);
        emptyView = view.findViewById(R.id.gallery_empty);
        statusView = view.findViewById(R.id.sync_status);
        progressTextView = view.findViewById(R.id.sync_progress_text);
        progressBar = view.findViewById(R.id.sync_progress);

        int orientation = getResources().getConfiguration().orientation;
        int columns = orientation == Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        adapter = new GalleryAdapter(requireContext(), columns, (source, photo) ->
                navigation.openDetails(source, photo.getLocalId()));
        recyclerView.setAdapter(adapter);
        view.findViewById(R.id.button_refresh).setOnClickListener(button -> {
            navigation.requestNotificationPermission();
            SyncService.scheduleUpdate(requireContext());
        });

        application.getGalleryClient().addListener(this);
        application.getSyncState().addObserver(this);
    }

    @Override
    public void onGotGalleryPhotos(List<LocalPhoto> photos) {
        List<LocalPhoto> copy = new ArrayList<>(photos);
        MainThreadHandler.run(() -> {
            if (adapter == null || recyclerView == null) return;
            adapter.submitList(copy);
            showEmpty(copy.isEmpty());
        });
    }

    @Override
    public void onSyncStateChanged(@NonNull SyncState.Snapshot snapshot) {
        if (statusView == null || progressBar == null || progressTextView == null) return;
        int statusText;
        switch (snapshot.status) {
            case CHECKING:
                statusText = R.string.sync_checking;
                break;
            case UPDATE_AVAILABLE:
                statusText = R.string.sync_available;
                break;
            case SCHEDULED:
                statusText = !snapshot.hasInternet ? R.string.sync_waiting_for_internet
                        : !snapshot.unmetered ? R.string.sync_waiting_for_unmetered
                        : R.string.sync_scheduled;
                break;
            case RUNNING:
                statusText = R.string.sync_running;
                break;
            case SUCCESS:
                statusText = R.string.sync_success;
                break;
            case ERROR:
                statusText = R.string.sync_error;
                break;
            case CANCELLED:
                statusText = R.string.notification_cancelled;
                break;
            default:
                statusText = R.string.sync_idle;
                break;
        }
        statusView.setText(statusText);
        statusView.setTextColor(getResources().getColor(snapshot.status == SyncState.Status.ERROR
                ? R.color.gallery_error : R.color.gallery_text_secondary, requireContext().getTheme()));
        boolean visible = snapshot.status == SyncState.Status.SCHEDULED
                || snapshot.status == SyncState.Status.RUNNING;
        progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        progressBar.setProgress(snapshot.progress);
        progressBar.setIndeterminate(snapshot.status == SyncState.Status.SCHEDULED);
        if (snapshot.status == SyncState.Status.RUNNING) {
            progressTextView.setText(getString(R.string.sync_progress_percent, snapshot.progress));
            progressTextView.setVisibility(View.VISIBLE);
        } else if (snapshot.status == SyncState.Status.SCHEDULED) {
            progressTextView.setVisibility(View.GONE);
        } else if (snapshot.status == SyncState.Status.SUCCESS) {
            progressTextView.setText(getString(R.string.sync_progress_percent, snapshot.progress));
            progressTextView.setVisibility(View.VISIBLE);
        } else {
            progressTextView.setVisibility(View.GONE);
        }
    }

    private void showEmpty(boolean empty) {
        if (emptyView == null || recyclerView == null) return;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        application.getGalleryClient().removeListener(this);
        application.getSyncState().removeObserver(this);
        if (adapter != null) adapter.close();
        adapter = null;
        recyclerView = null;
        emptyView = null;
        statusView = null;
        progressBar = null;
        progressTextView = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        navigation = null;
        application = null;
        super.onDetach();
    }
}
