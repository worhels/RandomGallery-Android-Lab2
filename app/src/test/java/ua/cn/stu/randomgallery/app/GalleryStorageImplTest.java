package ua.cn.stu.randomgallery.app;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GalleryStorageImplTest {
    private File directory;
    private InMemoryMetadata metadata;
    private GalleryStorageImpl storage;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("random-gallery-storage-").toFile();
        metadata = new InMemoryMetadata();
        storage = new GalleryStorageImpl(directory, metadata);
    }

    @After
    public void tearDown() {
        deleteRecursively(directory);
    }

    @Test
    public void writesReadsListsAndRestoresImageAndNameFromNewStorageInstance() throws Exception {
        String identifier = "stable_123___picsum-42-320x240";
        byte[] image = "image-content".getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = storage.write(identifier, "Автор #42")) {
            output.write(image);
        }

        GalleryStorageImpl reopened = new GalleryStorageImpl(directory, metadata);
        assertTrue(reopened.isExists(identifier));
        assertEquals("Автор #42", reopened.getName(identifier));
        assertEquals(List.of(identifier), reopened.getAll());
        try (ByteArrayOutputStream actual = new ByteArrayOutputStream();
             java.io.InputStream input = reopened.read(identifier)) {
            input.transferTo(actual);
            assertArrayEquals(image, actual.toByteArray());
        }
    }

    @Test
    public void renameMovesImageAndMetadataAndDeleteRemovesBoth() throws Exception {
        String original = "___456___picsum-8-160x120";
        String renamed = "stable_456___picsum-8-160x120";
        try (OutputStream output = storage.write(original, "Фото")) {
            output.write(new byte[]{1, 2, 3, 4});
        }

        storage.rename(original, renamed);
        assertFalse(storage.isExists(original));
        assertTrue(storage.isExists(renamed));
        assertEquals("Фото", storage.getName(renamed));
        storage.delete(renamed);
        assertFalse(storage.isExists(renamed));
        assertTrue(storage.getAll().isEmpty());
    }

    @Test
    public void timestampIsPersistentAndRejectsNegativeValues() {
        storage.setTimestamp(1_735_689_600_000L);
        assertEquals(1_735_689_600_000L, new GalleryStorageImpl(directory, metadata).getTimestamp());
        assertThrows(IllegalArgumentException.class, () -> storage.setTimestamp(-1));
    }

    @Test
    public void unsafeIdentifiersAreRejectedAndCannotEscapeStorageDirectory() {
        assertFalse(storage.isExists("../../private"));
        assertEquals(null, storage.getName("../../private"));
        assertThrows(IllegalArgumentException.class, () -> storage.write("../outside", "unsafe"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("C:\\outside"));
        assertNotNull(directory.getParentFile());
        assertFalse(new File(directory.getParentFile(), "outside").exists());
    }

    @Test
    public void allReturnsOnlyCommittedRecordsInStableOrder() throws Exception {
        try (OutputStream output = storage.write("stable_5___photo-b", "B")) {
            output.write(2);
        }
        try (OutputStream output = storage.write("stable_4___photo-a", "A")) {
            output.write(1);
        }
        assertEquals(List.of("stable_4___photo-a", "stable_5___photo-b"), storage.getAll());
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static final class InMemoryMetadata implements StorageMetadata {
        private Map<String, String> values = new HashMap<>();

        @Override
        public synchronized Map<String, String> readAll() {
            return new HashMap<>(values);
        }

        @Override
        public synchronized boolean replaceAll(Map<String, String> newValues) {
            values = new HashMap<>(newValues);
            return true;
        }
    }
}
