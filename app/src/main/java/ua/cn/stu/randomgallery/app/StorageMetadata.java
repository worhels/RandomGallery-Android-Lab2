package ua.cn.stu.randomgallery.app;

import java.util.Map;

interface StorageMetadata {
    Map<String, String> readAll();

    boolean replaceAll(Map<String, String> values);
}
