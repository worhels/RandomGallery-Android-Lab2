package ua.cn.stu.randomgallery.app;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

final class SharedPreferencesStorageMetadata implements StorageMetadata {
    private final SharedPreferences preferences;

    SharedPreferencesStorageMetadata(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public synchronized Map<String, String> readAll() {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                values.put(entry.getKey(), (String) entry.getValue());
            }
        }
        return values;
    }

    @Override
    public synchronized boolean replaceAll(Map<String, String> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        return editor.commit();
    }
}
