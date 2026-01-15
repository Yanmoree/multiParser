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

    static {
        loadProperties();
    }

    /**
     * Загрузка свойств из файла конфигурации
     */
    private static void loadProperties() {
        // Попытка 1: Загрузка из файла в текущей директории
        File externalConfig = new File(CONFIG_FILE);
        if (externalConfig.exists()) {
            try (InputStream input = new FileInputStream(externalConfig)) {
                properties.load(input);
                logger.info("Loaded configuration from external file: {}", CONFIG_FILE);
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external config file: {}", e.getMessage());
            }
        }

        // Попытка 2: Загрузка из ресурсов
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded configuration from resources: {}", CONFIG_FILE);
            } else {
                setDefaults();
                logger.warn("Config file not found, using defaults");
            }
        } catch (IOException e) {
            setDefaults();
            logger.error("Error loading config from resources: {}", e.getMessage());
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
        properties.setProperty("api.goofish.max.products.per.page", "500");
    }

    /**
     * Получение целочисленного значения
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Получение строкового значения
     */
    public static String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Получение булевого значения
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Получение значения с плавающей точкой
     */
    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
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
            properties.store(output, "Product Parser Configuration");
            logger.info("Configuration saved to: {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save configuration: {}", e.getMessage());
        }
    }

    /**
     * Установка значения свойства
     */
    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    // Методы для получения конкретных параметров

    public static String getTelegramBotToken() {
        return getString("telegram.bot.token", "");
    }

    public static int getThreadPoolMaxSize() {
        return getInt("thread.pool.max.size", 20);
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

    public static String getGoofishBaseUrl() {
        return getString("api.goofish.base_url", "https://h5api.m.goofish.com");
    }
}