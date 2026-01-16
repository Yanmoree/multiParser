package com.parser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.Properties;

/**
 * Класс для управления куки файлами
 */
public class CookieConfig {
    private static final Logger logger = LoggerFactory.getLogger(CookieConfig.class);
    private static final Properties cookies = new Properties();
    private static final String COOKIE_FILE = "cookies.properties";
    private static volatile boolean isLoaded = false;

    static {
        synchronized (CookieConfig.class) {
            if (!isLoaded) {
                loadCookies();
                isLoaded = true;
            }
        }
    }

    /**
     * Загрузка куки из файла
     */
    private static void loadCookies() {
        // Попытка 1: Загрузка из файла в текущей директории
        File externalFile = new File(COOKIE_FILE);
        if (externalFile.exists() && externalFile.isFile()) {
            try (InputStream input = new FileInputStream(externalFile)) {
                cookies.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded cookies from external file: {}", COOKIE_FILE);
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external cookie file: {}", e.getMessage());
            }
        }

        // Попытка 2: Загрузка из ресурсов
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(COOKIE_FILE)) {
            if (input != null) {
                cookies.load(new InputStreamReader(input, "UTF-8"));
                logger.info("Loaded cookies from resources: {}", COOKIE_FILE);
            } else {
                logger.warn("Cookie file not found");
            }
        } catch (IOException e) {
            logger.error("Error loading cookies from resources: {}", e.getMessage(), e);
        }
    }

    /**
     * Получение куки для домена
     */
    public static String getCookiesForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return "";
        }

        String key = domain.toLowerCase().trim() + ".cookies";
        String cookiesStr = cookies.getProperty(key, "");
        return cookiesStr != null ? cookiesStr.trim() : "";
    }

    /**
     * Установка куки для домена
     */
    public static void setCookiesForDomain(String domain, String cookieString) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }

        String key = domain.toLowerCase().trim() + ".cookies";
        cookies.setProperty(key, cookieString != null ? cookieString.trim() : "");
        saveCookies();
        logger.info("Cookies updated for domain: {}", domain);
    }

    /**
     * Получение конкретного куки по имени для домена
     */
    public static String getCookie(String domain, String cookieName) {
        if (domain == null || domain.trim().isEmpty() || cookieName == null || cookieName.trim().isEmpty()) {
            return "";
        }

        String allCookies = getCookiesForDomain(domain);
        if (allCookies == null || allCookies.isEmpty()) {
            return "";
        }

        String[] cookiePairs = allCookies.split("; ");
        for (String pair : cookiePairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(cookieName.trim())) {
                return parts[1].trim();
            }
        }

        return "";
    }

    /**
     * Установка конкретного куки
     */
    public static void setCookie(String domain, String cookieName, String cookieValue) {
        if (domain == null || domain.trim().isEmpty() || cookieName == null || cookieName.trim().isEmpty()) {
            return;
        }

        String key = domain.toLowerCase().trim() + ".cookies";
        String currentCookies = cookies.getProperty(key, "");

        // Создаем новую строку куки
        StringBuilder newCookies = new StringBuilder();
        boolean replaced = false;

        if (currentCookies != null && !currentCookies.trim().isEmpty()) {
            String[] cookiePairs = currentCookies.split("; ");
            for (String pair : cookiePairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    if (parts[0].trim().equals(cookieName.trim())) {
                        // Заменяем существующий куки
                        newCookies.append(cookieName.trim()).append("=").append(cookieValue != null ? cookieValue.trim() : "").append("; ");
                        replaced = true;
                    } else {
                        newCookies.append(pair).append("; ");
                    }
                }
            }
        }

        // Если куки не было, добавляем новый
        if (!replaced) {
            newCookies.append(cookieName.trim()).append("=").append(cookieValue != null ? cookieValue.trim() : "").append("; ");
        }

        // Убираем последнюю точку с запятой
        String result = newCookies.toString();
        if (result.endsWith("; ")) {
            result = result.substring(0, result.length() - 2);
        }

        cookies.setProperty(key, result);
        saveCookies();
        logger.debug("Cookie {} set for domain {}", cookieName, domain);
    }

    /**
     * Удаление куки
     */
    public static void removeCookie(String domain, String cookieName) {
        if (domain == null || domain.trim().isEmpty() || cookieName == null || cookieName.trim().isEmpty()) {
            return;
        }

        String key = domain.toLowerCase().trim() + ".cookies";
        String currentCookies = cookies.getProperty(key, "");

        if (currentCookies == null || currentCookies.trim().isEmpty()) {
            return;
        }

        StringBuilder newCookies = new StringBuilder();
        String[] cookiePairs = currentCookies.split("; ");

        for (String pair : cookiePairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && !parts[0].trim().equals(cookieName.trim())) {
                if (newCookies.length() > 0) {
                    newCookies.append("; ");
                }
                newCookies.append(pair);
            }
        }

        cookies.setProperty(key, newCookies.toString());
        saveCookies();
        logger.debug("Cookie {} removed from domain {}", cookieName, domain);
    }

    /**
     * Очистка всех куки для домена
     */
    public static void clearCookiesForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }

        String key = domain.toLowerCase().trim() + ".cookies";
        cookies.remove(key);
        saveCookies();
        logger.info("All cookies cleared for domain: {}", domain);
    }

    /**
     * Сохранение куки в файл
     */
    public static void saveCookies() {
        File externalFile = new File(COOKIE_FILE);
        try (OutputStream output = new FileOutputStream(externalFile)) {
            cookies.store(output, "Cookies for HTTP requests\nAuto-generated file");
            logger.debug("Cookies saved to: {}", COOKIE_FILE);
        } catch (IOException e) {
            logger.error("Failed to save cookies: {}", e.getMessage(), e);
        }
    }

    /**
     * Получение списка всех доменов с куки
     */
    public static String[] getCookieDomains() {
        return cookies.stringPropertyNames().stream()
                .filter(key -> key != null && key.endsWith(".cookies"))
                .map(key -> key.substring(0, key.length() - 8))
                .toArray(String[]::new);
    }

    /**
     * Проверка наличия куки для домена
     */
    public static boolean hasCookiesForDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }

        String key = domain.toLowerCase().trim() + ".cookies";
        String cookieString = cookies.getProperty(key, "");
        return cookieString != null && !cookieString.trim().isEmpty();
    }

    /**
     * Получение всех куки в виде строки для конкретного домена
     */
    public static String getAllCookiesAsString(String domain) {
        return getCookiesForDomain(domain);
    }

    /**
     * Парсинг строки куки из заголовка Set-Cookie
     */
    public static void parseSetCookieHeader(String domain, String setCookieHeader) {
        if (domain == null || domain.trim().isEmpty() || setCookieHeader == null || setCookieHeader.trim().isEmpty()) {
            return;
        }

        String[] cookiesArray = setCookieHeader.split(";\\s*");
        for (String cookie : cookiesArray) {
            String[] parts = cookie.split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String value = parts[1].trim();

                // Пропускаем атрибуты
                if (name.equalsIgnoreCase("path") ||
                        name.equalsIgnoreCase("domain") ||
                        name.equalsIgnoreCase("expires") ||
                        name.equalsIgnoreCase("max-age") ||
                        name.equalsIgnoreCase("secure") ||
                        name.equalsIgnoreCase("httponly") ||
                        name.equalsIgnoreCase("samesite")) {
                    continue;
                }

                setCookie(domain, name, value);
            }
        }

        logger.debug("Parsed Set-Cookie header for domain: {}", domain);
    }

    /**
     * Релоад куки из файла
     */
    public static void reload() {
        synchronized (CookieConfig.class) {
            cookies.clear();
            loadCookies();
            logger.info("Cookies reloaded");
        }
    }
}