package ua.cn.stu.randomgallery.app;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GalleryStorageInstrumentedTest {
    @Test
    public void internalFilesAndSharedMetadataSurviveStorageRecreation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GalleryStorageImpl storage = new GalleryStorageImpl(context);
        String identifier = "stable_987654___instrumented-" + UUID.randomUUID().toString().replace("-", "");
        byte[] content = "persistent-gallery-image".getBytes(StandardCharsets.UTF_8);
        try {
            try (OutputStream output = storage.write(identifier, "Збережене фото")) {
                output.write(content);
            }
            storage.setTimestamp(1_760_000_000_000L);

            GalleryStorageImpl recreated = new GalleryStorageImpl(context);
            assertTrue(recreated.isExists(identifier));
            assertEquals("Збережене фото", recreated.getName(identifier));
            assertEquals(1_760_000_000_000L, recreated.getTimestamp());
            try (InputStream input = recreated.read(identifier);
                 ByteArrayOutputStream actual = new ByteArrayOutputStream()) {
                input.transferTo(actual);
                assertArrayEquals(content, actual.toByteArray());
            }
        } finally {
            storage.delete(identifier);
        }
    }
}
