package ua.cn.stu.randomgallery.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import ua.cn.stu.randomgallery.GalleryStorage;

public final class GalleryStorageImpl implements GalleryStorage {
    private static final String TIMESTAMP_KEY = "gallery.timestamp";
    private static final String ENTRY_PREFIX = "photo.";
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]{1,512}");
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[a-f0-9]{64}_[a-f0-9]{32}\\.img");

    private final File directory;
    private final StorageMetadata metadata;

    public GalleryStorageImpl(@NonNull Context context) {
        this(new File(context.getFilesDir(), "gallery"), createMetadata(context));
    }

    GalleryStorageImpl(@NonNull File directory, @NonNull StorageMetadata metadata) {
        try {
            this.directory = directory.getCanonicalFile();
            this.metadata = metadata;
            if (!this.directory.isDirectory() && !this.directory.mkdirs()) {
                throw new IllegalStateException("Could not create gallery storage directory");
            }
            removeUnreferencedFiles();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize gallery storage", exception);
        }
    }

    private static StorageMetadata createMetadata(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                "gallery_storage", Context.MODE_PRIVATE);
        return new SharedPreferencesStorageMetadata(preferences);
    }

    @NonNull
    @Override
    public synchronized InputStream read(String identifier) throws IOException {
        Record record = findRecord(identifier);
        if (record == null || !record.complete || !record.file.isFile()) {
            throw new FileNotFoundException("Gallery image does not exist");
        }
        return new FileInputStream(record.file);
    }

    @NonNull
    @Override
    public synchronized OutputStream write(String identifier, String name) throws IOException {
        validateIdentifier(identifier);
        File staging = File.createTempFile(digest(identifier) + "_", ".part", directory);
        FileOutputStream output;
        try {
            output = new FileOutputStream(staging, false);
        } catch (IOException exception) {
            staging.delete();
            throw exception;
        }
        Record previous = findRecord(identifier);
        return new FilterOutputStream(output) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    super.close();
                    File completedFile = new File(directory,
                            digest(identifier) + "_" + UUID.randomUUID().toString().replace("-", "") + ".img");
                    if (!staging.renameTo(completedFile)) {
                        throw new IOException("Could not finalize downloaded image");
                    }
                    Map<String, String> values = metadata.readAll();
                    writeRecord(values, identifier, name == null ? "" : name,
                            completedFile.getName(), true);
                    if (!metadata.replaceAll(values)) {
                        completedFile.delete();
                        throw new IOException("Could not save gallery metadata");
                    }
                    if (previous != null && !previous.file.equals(completedFile)) {
                        previous.file.delete();
                    }
                } catch (IOException exception) {
                    staging.delete();
                    throw exception;
                }
            }
        };
    }

    @Override
    public synchronized boolean isExists(String identifier) {
        if (!isSafeIdentifier(identifier)) return false;
        try {
            Record record = findRecord(identifier);
            if (record == null || !record.complete || !record.file.isFile()) {
                if (record != null) removeRecord(identifier, record);
                return false;
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public synchronized String getName(String identifier) {
        if (!isSafeIdentifier(identifier)) return null;
        try {
            Record record = findRecord(identifier);
            return record == null || !record.complete || !record.file.isFile()
                    ? null : record.name;
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public synchronized void delete(String identifier) {
        if (!isSafeIdentifier(identifier)) return;
        try {
            Record record = findRecord(identifier);
            if (record != null) removeRecord(identifier, record);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete gallery metadata", exception);
        }
    }

    @Override
    public synchronized void rename(String oldIdentifier, String newIdentifier) {
        validateIdentifier(oldIdentifier);
        validateIdentifier(newIdentifier);
        if (oldIdentifier.equals(newIdentifier)) return;
        try {
            Record source = findRecord(oldIdentifier);
            if (source == null || !source.complete || !source.file.isFile()) {
                throw new IllegalStateException("Gallery image to rename does not exist");
            }
            if (findRecord(newIdentifier) != null) {
                throw new IllegalStateException("Gallery image with the new identifier already exists");
            }
            File destination = new File(directory,
                    digest(newIdentifier) + "_" + UUID.randomUUID().toString().replace("-", "") + ".img");
            if (!source.file.renameTo(destination)) {
                throw new IllegalStateException("Could not rename gallery image");
            }
            Map<String, String> values = metadata.readAll();
            removeRecordValues(values, oldIdentifier);
            writeRecord(values, newIdentifier, source.name, destination.getName(), true);
            if (!metadata.replaceAll(values)) {
                destination.renameTo(source.file);
                throw new IllegalStateException("Could not save renamed gallery metadata");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not rename gallery image", exception);
        }
    }

    @NonNull
    @Override
    public synchronized List<String> getAll() {
        List<String> identifiers = new ArrayList<>();
        Map<String, String> values = metadata.readAll();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(ENTRY_PREFIX) || !key.endsWith(".id")) continue;
            String identifier = entry.getValue();
            if (!isSafeIdentifier(identifier)) continue;
            try {
                Record record = findRecord(identifier);
                if (record != null && record.complete && record.file.isFile()) {
                    identifiers.add(identifier);
                }
            } catch (IOException ignored) {
                continue;
            }
        }
        Collections.sort(identifiers);
        return identifiers;
    }

    @Override
    public synchronized long getTimestamp() {
        try {
            String value = metadata.readAll().get(TIMESTAMP_KEY);
            return value == null ? 0 : Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Override
    public synchronized void setTimestamp(long timestamp) {
        if (timestamp < 0) throw new IllegalArgumentException("timestamp must be non-negative");
        Map<String, String> values = metadata.readAll();
        values.put(TIMESTAMP_KEY, Long.toString(timestamp));
        if (!metadata.replaceAll(values)) {
            throw new IllegalStateException("Could not save gallery timestamp");
        }
    }

    private Record findRecord(String identifier) throws IOException {
        validateIdentifier(identifier);
        String key = recordPrefix(identifier);
        Map<String, String> values = metadata.readAll();
        String storedIdentifier = values.get(key + "id");
        if (!identifier.equals(storedIdentifier)) return null;
        String fileName = values.get(key + "file");
        String name = values.get(key + "name");
        boolean complete = "1".equals(values.get(key + "complete"));
        if (fileName == null || !SAFE_FILE_NAME.matcher(fileName).matches()) return null;
        File file = new File(directory, fileName).getCanonicalFile();
        if (!directory.equals(file.getParentFile())) return null;
        return new Record(file, name == null ? "" : name, complete);
    }

    private void removeRecord(String identifier, Record record) throws IOException {
        Map<String, String> values = metadata.readAll();
        removeRecordValues(values, identifier);
        if (!metadata.replaceAll(values)) {
            throw new IOException("Could not remove gallery metadata");
        }
        record.file.delete();
    }

    private void removeUnreferencedFiles() {
        Map<String, String> values = metadata.readAll();
        List<File> referenced = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getKey().startsWith(ENTRY_PREFIX) || !entry.getKey().endsWith(".file")) continue;
            String fileName = entry.getValue();
            if (fileName != null && SAFE_FILE_NAME.matcher(fileName).matches()) {
                referenced.add(new File(directory, fileName));
            }
        }
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && !referenced.contains(file)) file.delete();
        }
    }

    private static void writeRecord(Map<String, String> values, String identifier,
                                    String name, String fileName, boolean complete) {
        String key = recordPrefix(identifier);
        values.put(key + "id", identifier);
        values.put(key + "file", fileName);
        values.put(key + "name", name);
        values.put(key + "complete", complete ? "1" : "0");
    }

    private static void removeRecordValues(Map<String, String> values, String identifier) {
        String key = recordPrefix(identifier);
        values.remove(key + "id");
        values.remove(key + "file");
        values.remove(key + "name");
        values.remove(key + "complete");
    }

    private static String recordPrefix(String identifier) {
        return ENTRY_PREFIX + digest(identifier) + ".";
    }

    private static void validateIdentifier(String identifier) {
        if (!isSafeIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid gallery identifier");
        }
    }

    private static boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", valueByte & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class Record {
        final File file;
        final String name;
        final boolean complete;

        Record(File file, String name, boolean complete) {
            this.file = file;
            this.name = name;
            this.complete = complete;
        }
    }
}
