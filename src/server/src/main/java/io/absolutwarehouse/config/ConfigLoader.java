package io.absolutwarehouse.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {

    private static final Properties properties = new Properties();

    public static void load(String filepath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filepath)) {
            properties.load(fis);
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static String getString(String key, String defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        return clean(val);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(clean(properties.getProperty(key)));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}