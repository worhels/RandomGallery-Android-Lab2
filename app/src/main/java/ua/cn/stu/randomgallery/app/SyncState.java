package ua.cn.stu.randomgallery.app;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SyncState {
    public enum Status {
        IDLE, CHECKING, UPDATE_AVAILABLE, SCHEDULED, RUNNING, SUCCESS, ERROR, CANCELLED
    }

    public interface Observer {
        void onSyncStateChanged(@NonNull Snapshot snapshot);
    }

    public static final class Snapshot {
        public final Status status;
        public final int progress;
        public final boolean updatesAvailable;
        public final boolean hasInternet;
        public final boolean unmetered;

        Snapshot(Status status, int progress, boolean updatesAvailable,
                 boolean hasInternet, boolean unmetered) {
            this.status = status;
            this.progress = progress;
            this.updatesAvailable = updatesAvailable;
            this.hasInternet = hasInternet;
            this.unmetered = unmetered;
        }
    }

    private final List<WeakReference<Observer>> observers = new ArrayList<>();
    private volatile Snapshot current = new Snapshot(Status.IDLE, 0, false, false, false);

    public Snapshot getSnapshot() {
        return current;
    }

    public void addObserver(@NonNull Observer observer) {
        MainThreadHandler.run(() -> {
            removeObserverNow(observer);
            observers.add(new WeakReference<>(observer));
            observer.onSyncStateChanged(current);
        });
    }

    public void removeObserver(@NonNull Observer observer) {
        MainThreadHandler.run(() -> removeObserverNow(observer));
    }

    public void setChecking() {
        update(Status.CHECKING, 0, current.updatesAvailable, current.hasInternet, current.unmetered);
    }

    public void setUpdateAvailable(boolean available) {
        update(available ? Status.UPDATE_AVAILABLE : Status.IDLE, 0, available,
                current.hasInternet, current.unmetered);
    }

    public void setScheduled(boolean hasInternet, boolean unmetered) {
        update(Status.SCHEDULED, 0, current.updatesAvailable, hasInternet, unmetered);
    }

    public void setRunning() {
        update(Status.RUNNING, 0, true, current.hasInternet, current.unmetered);
    }

    public void setProgress(int progress) {
        update(Status.RUNNING, Math.max(0, Math.min(100, progress)), true,
                current.hasInternet, current.unmetered);
    }

    public void setSuccess() {
        update(Status.SUCCESS, 100, false, current.hasInternet, current.unmetered);
    }

    public void setError() {
        update(Status.ERROR, 0, current.updatesAvailable, current.hasInternet, current.unmetered);
    }

    public void setCancelled() {
        update(Status.CANCELLED, 0, current.updatesAvailable, current.hasInternet, current.unmetered);
    }

    public void updateNetworkStatus(boolean hasInternet, boolean unmetered) {
        Snapshot snapshot = current;
        update(snapshot.status, snapshot.progress, snapshot.updatesAvailable, hasInternet, unmetered);
    }

    private void update(Status status, int progress, boolean updatesAvailable,
                        boolean hasInternet, boolean unmetered) {
        Snapshot next = new Snapshot(status, progress, updatesAvailable, hasInternet, unmetered);
        MainThreadHandler.run(() -> {
            current = next;
            Iterator<WeakReference<Observer>> iterator = observers.iterator();
            while (iterator.hasNext()) {
                Observer observer = iterator.next().get();
                if (observer == null) iterator.remove();
                else observer.onSyncStateChanged(next);
            }
        });
    }

    private void removeObserverNow(Observer observer) {
        Iterator<WeakReference<Observer>> iterator = observers.iterator();
        while (iterator.hasNext()) {
            Observer currentObserver = iterator.next().get();
            if (currentObserver == null || currentObserver == observer) iterator.remove();
        }
    }
}
