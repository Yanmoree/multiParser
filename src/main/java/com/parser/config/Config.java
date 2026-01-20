package com.parser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.Properties;

/**
 * Класс для управления конфигурацией приложения
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";
    private static volatile boolean isLoaded = false;

    static {
        synchronized (Config.class) {
            if (!isLoaded) {
                loadProperties();
                isLoaded = true;
            }
        }
    }

    private static void loadProperties() {
        File externalConfig = new File(CONFIG_FILE);
        if (externalConfig.exists()) {
            try (InputStream input = new FileInputStream(externalConfig)) {
                properties.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded configuration from: {}", CONFIG_FILE);
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external config: {}", e.getMessage());
            }
        }

        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded configuration from resources");
            } else {
                setDefaults();
                logger.warn("Config file not found, using defaults");
            }
        } catch (IOException e) {
            setDefaults();
            logger.error("Error loading config: {}", e.getMessage());
        }
    }

    private static void setDefaults() {
        properties.setProperty("telegram.bot.token", "");
        properties.setProperty("telegram.bot.username", "");
        properties.setProperty("telegram.admin.id", "0");
        properties.setProperty("parser.default.check_interval", "300");
        properties.setProperty("parser.default.max_age_minutes", "1440");
        properties.setProperty("parser.default.max_pages", "3");
        properties.setProperty("parser.default.rows_per_page", "100");
        properties.setProperty("thread.pool.core.size", "3");
        properties.setProperty("thread.pool.max.size", "10");
        properties.setProperty("thread.pool.keepalive.seconds", "60");
        properties.setProperty("storage.data.dir", "./data");
        properties.setProperty("storage.backup.enabled", "true");
        properties.setProperty("http.connect.timeout", "10000");
        properties.setProperty("http.read.timeout", "15000");
        properties.setProperty("http.user.agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        properties.setProperty("cookie.auto.update", "true");
        properties.setProperty("cookie.update.interval.minutes", "120");
        properties.setProperty("cookie.dynamic.enabled", "true");
        properties.setProperty("cookie.cache.ttl.minutes", "30");
        properties.setProperty("logging.level", "INFO");
        properties.setProperty("api.goofish.delay.between.requests", "2000");
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid long for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    public static String getString(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        return value != null ? value.trim() : defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        return value != null && (value.trim().toLowerCase().matches("true|yes|1"));
    }

    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid double for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public static void saveConfig() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            properties.store(output, "Product Parser Configuration");
            logger.info("Configuration saved");
        } catch (IOException e) {
            logger.error("Failed to save configuration: {}", e.getMessage());
        }
    }

    public static void reload() {
        synchronized (Config.class) {
            properties.clear();
            loadProperties();
            logger.info("Configuration reloaded");
        }
    }

    // Convenience methods
    public static String getTelegramBotToken() {
        return getString("telegram.bot.token", "");
    }

    public static String getTelegramBotUsername() {
        return getString("telegram.bot.username", "");
    }

    public static long getTelegramAdminId() {
        return getLong("telegram.admin.id", 0);
    }

    public static int getThreadPoolCoreSize() {
        return getInt("thread.pool.core.size", 3);
    }

    public static int getThreadPoolMaxSize() {
        return getInt("thread.pool.max.size", 10);
    }

    public static int getHttpConnectTimeout() {
        return getInt("http.connect.timeout", 10000);
    }

    public static int getHttpReadTimeout() {
        return getInt("http.read.timeout", 15000);
    }

    public static String getHttpUserAgent() {
        return getString("http.user.agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    }

    public static boolean isDynamicCookiesEnabled() {
        return getBoolean("cookie.dynamic.enabled", true);
    }

    public static boolean getCookieAutoUpdate() {
        return getBoolean("cookie.auto.update", true);
    }

    public static int getCookieUpdateInterval() {
        return getInt("cookie.update.interval.minutes", 120);
    }

    public static String getStorageDataDir() {
        return getString("storage.data.dir", "./data");
    }

    public static int getDefaultCheckInterval() {
        return getInt("parser.default.check_interval", 300);
    }

    public static int getDefaultMaxAgeMinutes() {
        return getInt("parser.default.max_age_minutes", 1440);
    }
}