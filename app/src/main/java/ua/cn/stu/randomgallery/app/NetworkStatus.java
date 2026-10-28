package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class NetworkStatus {
    final boolean hasInternet;
    final boolean unmetered;

    private NetworkStatus(boolean hasInternet, boolean unmetered) {
        this.hasInternet = hasInternet;
        this.unmetered = unmetered;
    }

    static NetworkStatus read(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return new NetworkStatus(false, false);
        Network active = manager.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
        boolean internet = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean unmetered = internet
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        return new NetworkStatus(internet, unmetered);
    }
}
