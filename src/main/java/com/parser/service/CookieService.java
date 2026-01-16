package com.parser.service;

import com.parser.config.Config;
import com.parser.config.CookieConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Упрощенный сервис для работы с cookies через Selenium
 */
public class CookieService {
    private static final Logger logger = LoggerFactory.getLogger(CookieService.class);

    // Кэш cookies для доменов
    private static final Map<String, Map<String, String>> cookieCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 30 * 60 * 1000; // 30 минут

    // Время последнего обновления cookies
    private static long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = 60 * 60 * 1000; // 1 час

    /**
     * Получение свежих cookies для домена
     */
    public static Map<String, String> getFreshCookies(String domain) {
        // Проверяем кэш
        if (cookieCache.containsKey(domain) && cacheTimestamp.containsKey(domain)) {
            long cacheAge = System.currentTimeMillis() - cacheTimestamp.get(domain);
            if (cacheAge < CACHE_TTL) {
                logger.debug("🍪 Используем cookies из кэша для {} (возраст: {} мин)",
                        domain, cacheAge / (60 * 1000));
                return new HashMap<>(cookieCache.get(domain));
            }
        }

        // Если кэш устарел или отсутствует
        logger.info("🍪 Получение свежих cookies для {}", domain);

        // Пробуем получить из конфига
        Map<String, String> cookies = getCookiesFromConfig(domain);
        if (!cookies.isEmpty()) {
            // Сохраняем в кэш
            cookieCache.put(domain, new HashMap<>(cookies));
            cacheTimestamp.put(domain, System.currentTimeMillis());
            return cookies;
        }

        // Если в конфиге нет, пробуем через Selenium (только если включены динамические cookies)
        if (Config.isDynamicCookiesEnabled()) {
            try {
                Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookies();
                if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                    // Сохраняем в конфиг и кэш
                    updateCookieConfig(domain, freshCookies);
                    cookieCache.put(domain, new HashMap<>(freshCookies));
                    cacheTimestamp.put(domain, System.currentTimeMillis());
                    return freshCookies;
                }
            } catch (Exception e) {
                logger.warn("⚠️ Не удалось получить свежие cookies через Selenium: {}", e.getMessage());
            }
        }

        return new HashMap<>();
    }

    /**
     * Принудительное обновление cookies
     */
    public static boolean refreshCookies(String domain) {
        logger.info("🔄 Принудительное обновление cookies для домена: {}", domain);

        try {
            Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookies();

            if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                updateCookieConfig(domain, freshCookies);
                lastRefreshTime = System.currentTimeMillis();
                logger.info("✅ Cookies успешно обновлены");
                return true;
            } else {
                logger.error("❌ Полученные cookies не прошли валидацию");
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка при обновлении cookies: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Обновление cookies через GUI (для отладки)
     */
    public static boolean refreshCookiesWithGUI(String domain) {
        logger.info("🔄 Обновление cookies через GUI для домена: {}", domain);

        try {
            Map<String, String> freshCookies = SeleniumCookieFetcher.getFreshCookiesWithGUI();

            if (SeleniumCookieFetcher.validateCookies(freshCookies)) {
                updateCookieConfig(domain, freshCookies);
                lastRefreshTime = System.currentTimeMillis();
                logger.info("✅ Cookies успешно обновлены через GUI");
                return true;
            } else {
                logger.error("❌ Полученные cookies не прошли валидацию");
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка при обновлении cookies через GUI: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получение строки cookies для HTTP заголовка
     */
    public static String getCookieHeader(String domain) {
        Map<String, String> cookies = getFreshCookies(domain);
        return cookiesToHeaderString(cookies);
    }

    /**
     * Получение cookies для домена
     */
    public static Map<String, String> getCookiesForDomain(String domain) {
        return getFreshCookies(domain);
    }

    /**
     * Очистка кэша cookies
     */
    public static void clearCache() {
        lastRefreshTime = 0;
        logger.info("🧹 Кэш cookies очищен");
    }

    /**
     * Получение статистики
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("lastRefreshTime", new Date(lastRefreshTime));
        stats.put("dynamicCookiesEnabled", Config.isDynamicCookiesEnabled());
        stats.put("refreshIntervalMinutes", REFRESH_INTERVAL / 60000);
        return stats;
    }

    /**
     * Обновление конфигурации cookies
     */
    private static void updateCookieConfig(String domain, Map<String, String> cookies) {
        StringBuilder cookieString = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (cookieString.length() > 0) {
                cookieString.append("; ");
            }
            cookieString.append(entry.getKey()).append("=").append(entry.getValue());
        }

        CookieConfig.setCookiesForDomain(domain, cookieString.toString());
        saveCookiesToProperties(cookies);
        logger.debug("Обновлены cookies для домена: {} ({} cookies)", domain, cookies.size());
    }

    /**
     * Сохранение cookies в файл properties
     */
    private static void saveCookiesToProperties(Map<String, String> cookies) {
        try {
            Properties props = new Properties();

            // Формируем строку cookies для Goofish API
            StringBuilder cookieString = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                cookieString.append(entry.getKey()).append("=").append(entry.getValue()).append("; ");
            }

            // Убираем последнюю точку с запятой
            if (cookieString.length() > 0) {
                cookieString.setLength(cookieString.length() - 2);
            }

            // Сохраняем для основного домена Goofish
            props.setProperty("h5api.m.goofish.com.cookies", cookieString.toString());

            // Также сохраняем для www.goofish.com
            props.setProperty("www.goofish.com.cookies", cookieString.toString());

            // Сохраняем в файл
            try (FileOutputStream fos = new FileOutputStream("cookies.properties")) {
                props.store(fos, "Cookies for Goofish\nAuto-generated by SeleniumCookieFetcher");
                logger.info("💾 Cookies сохранены в: cookies.properties");
                logger.info("🍪 Строка cookies ({} символов): {}",
                        cookieString.length(),
                        cookieString.substring(0, Math.min(100, cookieString.length())) +
                                (cookieString.length() > 100 ? "..." : ""));
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при сохранении cookies в файл: {}", e.getMessage());
        }
    }

    /**
     * Получение cookies из конфига
     */
    private static Map<String, String> getCookiesFromConfig(String domain) {
        String cookieString = CookieConfig.getCookiesForDomain(domain);
        Map<String, String> cookies = new HashMap<>();

        if (cookieString != null && !cookieString.trim().isEmpty()) {
            String[] cookiePairs = cookieString.split("; ");
            for (String pair : cookiePairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    cookies.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        return cookies;
    }

    /**
     * Преобразование Map cookies в строку для заголовка
     */
    private static String cookiesToHeaderString(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return header.toString();
    }
}