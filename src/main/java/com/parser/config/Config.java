package com.parser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.Properties;

import static java.lang.Long.getLong;

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

    /**
     * Загрузка свойств из файла конфигурации
     */
    private static void loadProperties() {
        // Сначала пробуем загрузить из внешнего файла
        File externalConfig = new File(CONFIG_FILE);
        if (externalConfig.exists() && externalConfig.isFile()) {
            try (InputStream input = new FileInputStream(externalConfig)) {
                properties.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded configuration from external file: {}", CONFIG_FILE);
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external config file: {}", e.getMessage());
            }
        }

        // Затем из ресурсов
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded configuration from resources: {}", CONFIG_FILE);
            } else {
                setDefaults();
                logger.warn("Config file not found, using defaults");
            }
        } catch (IOException e) {
            setDefaults();
            logger.error("Error loading config from resources: {}", e.getMessage(), e);
        }
    }

    /**
     * Установка значений по умолчанию
     */
    private static void setDefaults() {
        // Telegram
        properties.setProperty("telegram.bot.token", "");
        properties.setProperty("telegram.bot.username", "");
        properties.setProperty("telegram.admin.id", "0");

        // Parser
        properties.setProperty("parser.default.check_interval", "300");
        properties.setProperty("parser.default.max_age_minutes", "1440");
        properties.setProperty("parser.default.max_pages", "3");
        properties.setProperty("parser.default.rows_per_page", "100");
        properties.setProperty("parser.default.notify_new_only", "true");

        // Thread Pool
        properties.setProperty("thread.pool.core.size", "5");
        properties.setProperty("thread.pool.max.size", "20");
        properties.setProperty("thread.pool.queue.capacity", "100");
        properties.setProperty("thread.pool.keepalive.seconds", "60");

        // Storage
        properties.setProperty("storage.data.dir", "./data");
        properties.setProperty("storage.backup.enabled", "true");
        properties.setProperty("storage.backup.interval.hours", "24");

        // HTTP
        properties.setProperty("http.connect.timeout", "10000");
        properties.setProperty("http.read.timeout", "15000");
        properties.setProperty("http.user.agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        properties.setProperty("http.max.retries", "3");
        properties.setProperty("http.retry.delay", "1000");

        // Cookie configuration
        properties.setProperty("cookie.file", "cookies.properties");
        properties.setProperty("cookie.auto.update", "true");
        properties.setProperty("cookie.update.interval.minutes", "60");
        properties.setProperty("cookie.backup.enabled", "true");
        properties.setProperty("cookie.dynamic.enabled", "true");
        properties.setProperty("cookie.cache.ttl.minutes", "30");

        // Logging
        properties.setProperty("logging.level", "INFO");
        properties.setProperty("logging.file", "parser.log");
        properties.setProperty("logging.max.size.mb", "10");
        properties.setProperty("logging.max.backups", "5");

        // API
        properties.setProperty("api.goofish.base_url", "https://h5api.m.goofish.com");
        properties.setProperty("api.goofish.search.endpoint",
                "/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/");
        properties.setProperty("api.goofish.delay.between.requests", "2000");
        properties.setProperty("api.goofish.max.products.per_page", "500");
    }

    /**
     * Получение целочисленного значения
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Получение строкового значения
     */
    public static String getString(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        return value != null ? value.trim() : defaultValue;
    }

    /**
     * Получение булевого значения
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        if (value == null) return defaultValue;

        value = value.trim().toLowerCase();
        return value.equals("true") || value.equals("yes") || value.equals("1");
    }

    /**
     * Получение значения с плавающей точкой
     */
    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid double value for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Сохранение конфигурации в файл
     */
    public static void saveConfig() {
        File externalConfig = new File(CONFIG_FILE);
        try (OutputStream output = new FileOutputStream(externalConfig)) {
            properties.store(output, "Product Parser Configuration\nAuto-generated configuration");
            logger.info("Configuration saved to: {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save configuration: {}", e.getMessage(), e);
        }
    }

    /**
     * Установка значения свойства
     */
    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Получение всех свойств
     */
    public static Properties getAllProperties() {
        return new Properties(properties);
    }

    /**
     * Релоад конфигурации
     */
    public static void reload() {
        synchronized (Config.class) {
            properties.clear();
            loadProperties();
            logger.info("Configuration reloaded");
        }
    }

    // Методы для получения конкретных параметров
    public static String getTelegramBotToken() {
        return getString("telegram.bot.token", "");
    }

    public static String getTelegramBotUsername() {
        return getString("telegram.bot.username", "");
    }

    public static long getTelegramAdminId() {
        return getLong("telegram.admin.id", 0);
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Получение ID администратора как long
     */
    public static long getTelegramAdminIdLong() {
        return getLong("telegram.admin.id", 0);
    }

    public static int getThreadPoolCoreSize() {
        return getInt("thread.pool.core.size", 5);
    }

    public static int getThreadPoolMaxSize() {
        return getInt("thread.pool.max.size", 20);
    }

    public static int getThreadPoolKeepAlive() {
        return getInt("thread.pool.keepalive.seconds", 60);
    }

    public static int getThreadPoolQueueCapacity() {
        return getInt("thread.pool.queue.capacity", 100);
    }

    public static int getHttpConnectTimeout() {
        return getInt("http.connect.timeout", 10000);
    }

    public static int getHttpReadTimeout() {
        return getInt("http.read.timeout", 15000);
    }

    public static String getHttpUserAgent() {
        return getString("http.user.agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    }

    public static int getHttpMaxRetries() {
        return getInt("http.max.retries", 3);
    }

    public static int getHttpRetryDelay() {
        return getInt("http.retry.delay", 1000);
    }

    public static String getGoofishBaseUrl() {
        return getString("api.goofish.base_url", "https://h5api.m.goofish.com");
    }

    public static String getGoofishSearchEndpoint() {
        return getString("api.goofish.search.endpoint", "/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/");
    }

    public static int getGoofishDelayBetweenRequests() {
        return getInt("api.goofish.delay.between.requests", 2000);
    }

    public static int getGoofishMaxProductsPerPage() {
        return getInt("api.goofish.max.products.per.page", 500);
    }

    public static boolean getCookieAutoUpdate() {
        return getBoolean("cookie.auto.update", true);
    }

    public static boolean isDynamicCookiesEnabled() {
        return getBoolean("cookie.dynamic.enabled", true);
    }

    public static int getCookieCacheTTL() {
        return getInt("cookie.cache.ttl.minutes", 30);
    }

    public static int getCookieUpdateInterval() {
        return getInt("cookie.update.interval.minutes", 60);
    }

    public static boolean isCookieBackupEnabled() {
        return getBoolean("cookie.backup.enabled", true);
    }

    public static String getCookieFile() {
        return getString("cookie.file", "cookies.properties");
    }

    public static String getStorageDataDir() {
        return getString("storage.data.dir", "./data");
    }

    public static boolean isStorageBackupEnabled() {
        return getBoolean("storage.backup.enabled", true);
    }

    public static int getStorageBackupInterval() {
        return getInt("storage.backup.interval.hours", 24);
    }

    public static String getLoggingLevel() {
        return getString("logging.level", "INFO");
    }

    public static String getLoggingFile() {
        return getString("logging.file", "parser.log");
    }

    public static int getLoggingMaxSizeMB() {
        return getInt("logging.max.size.mb", 10);
    }

    public static int getLoggingMaxBackups() {
        return getInt("logging.max.backups", 5);
    }

    public static int getDefaultCheckInterval() {
        return getInt("parser.default.check_interval", 300);
    }

    public static int getDefaultMaxAgeMinutes() {
        return getInt("parser.default.max_age_minutes", 1440);
    }

    public static int getDefaultMaxPages() {
        return getInt("parser.default.max_pages", 3);
    }

    public static int getDefaultRowsPerPage() {
        return getInt("parser.default.rows_per_page", 100);
    }

    public static boolean getDefaultNotifyNewOnly() {
        return getBoolean("parser.default.notify_new_only", true);
    }
}