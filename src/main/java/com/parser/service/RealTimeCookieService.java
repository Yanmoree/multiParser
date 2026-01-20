package com.parser.service;

import com.parser.config.Config;
import com.parser.config.CookieConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления cookies в реальном времени
 */
public class RealTimeCookieService {
    private static final Logger logger = LoggerFactory.getLogger(RealTimeCookieService.class);

    private static final Map<String, String> currentCookies = new ConcurrentHashMap<>();
    private static long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 60 * 60 * 1000; // 1 час

    // Основные домены
    private static final String[] GOOFISH_DOMAINS = {
            "www.goofish.com",
            "h5api.m.goofish.com",
            "m.goofish.com"
    };

    /**
     * Инициализация cookies
     */
    public static synchronized void initialize() {
        if (currentCookies.isEmpty() || isExpired()) {
            refreshCookies();
        }
    }

    /**
     * Проверка истечения срока cookies
     */
    private static boolean isExpired() {
        long now = System.currentTimeMillis();
        return (now - lastUpdateTime) > UPDATE_INTERVAL;
    }

    /**
     * Обновление cookies
     */
    public static synchronized boolean refreshCookies() {
        logger.info("🔄 Обновление cookies через Selenium...");

        try {
            Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookies();

            if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                currentCookies.clear();
                currentCookies.putAll(freshCookies);
                lastUpdateTime = System.currentTimeMillis();

                // Сохраняем в конфиг
                updateAllDomains();

                // Сохраняем в файл для отладки
                saveToJsonFile();

                logger.info("✅ Cookies успешно обновлены");
                return true;
            } else {
                logger.error("❌ Валидация cookies не пройдена");
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка обновления cookies: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Обновление конфигурации для всех доменов
     */
    private static void updateAllDomains() {
        String cookieString = mapToString(currentCookies);

        for (String domain : GOOFISH_DOMAINS) {
            CookieConfig.setCookiesForDomain(domain, cookieString);
            logger.debug("Обновлены cookies для домена: {}", domain);
        }

        // Также сохраняем в cookies.properties
        saveToPropertiesFile();
    }

    /**
     * Получение строки cookies для заголовка
     */
    public static String getCookieHeader(String domain) {
        if (currentCookies.isEmpty() || isExpired()) {
            refreshCookies();
        }
        return mapToString(currentCookies);
    }

    /**
     * Получение конкретного cookie
     */
    public static String getCookie(String name) {
        if (currentCookies.isEmpty() || isExpired()) {
            refreshCookies();
        }
        return currentCookies.getOrDefault(name, "");
    }

    /**
     * Преобразование Map в строку cookies
     */
    private static String mapToString(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return sb.toString();
    }

    /**
     * Сохранение cookies в properties файл
     */
    private static void saveToPropertiesFile() {
        try {
            Properties props = new Properties();

            String cookieString = mapToString(currentCookies);

            // Сохраняем для всех доменов
            for (String domain : GOOFISH_DOMAINS) {
                props.setProperty(domain + ".cookies", cookieString);
            }

            try (FileOutputStream fos = new FileOutputStream("cookies.properties")) {
                props.store(fos, "Auto-generated cookies for Goofish\n" + new Date());
                logger.info("💾 Cookies сохранены в cookies.properties");
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка сохранения cookies: {}", e.getMessage());
        }
    }

    /**
     * Сохранение cookies в JSON файл
     */
    private static void saveToJsonFile() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("last_updated", new Date().toString());
            data.put("source", "real_fetch");
            data.put("cookies", currentCookies);
            data.put("timestamp", System.currentTimeMillis());

            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data);

            try (java.io.FileWriter fw = new java.io.FileWriter("real_cookies.json")) {
                fw.write(json);
                logger.info("💾 Cookies сохранены в real_cookies.json");
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка сохранения JSON cookies: {}", e.getMessage());
        }
    }

    /**
     * Получение информации о cookies
     */
    public static Map<String, Object> getCookieInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("last_update", new Date(lastUpdateTime));
        info.put("cookie_count", currentCookies.size());
        info.put("key_cookies", getKeyCookiesInfo());
        info.put("domains", Arrays.asList(GOOFISH_DOMAINS));

        return info;
    }

    /**
     * Получение информации о ключевых cookies
     */
    private static Map<String, String> getKeyCookiesInfo() {
        Map<String, String> keyCookies = new HashMap<>();

        String[] keys = {"_m_h5_tk", "_tb_token_", "cna", "cookie2", "t"};
        for (String key : keys) {
            if (currentCookies.containsKey(key)) {
                String value = currentCookies.get(key);
                keyCookies.put(key, value.length() > 50 ? value.substring(0, 47) + "..." : value);
            }
        }

        return keyCookies;
    }
}