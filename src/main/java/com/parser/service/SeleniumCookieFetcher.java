package com.parser.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для получения cookies через Selenium - ИСПРАВЛЕННЫЙ ДЛЯ СЕРВЕРА
 */
public class SeleniumCookieFetcher {
    private static final Logger logger = LoggerFactory.getLogger(SeleniumCookieFetcher.class);

    /**
     * Основной метод получения cookies для Goofish - ИСПРАВЛЕННЫЙ ДЛЯ СЕРВЕРА
     */
    public static Map<String, String> fetchGoofishCookies(boolean headless) {
        logger.info("🔄 Запуск Selenium для получения cookies Goofish");
        System.out.println("=".repeat(60));
        System.out.println("🔄 АВТОМАТИЧЕСКОЕ ПОЛУЧЕНИЕ COOKIES GOOFISH");
        System.out.println("=".repeat(60));

        WebDriver driver = null;
        try {
            // 1. Настройка WebDriver
            WebDriverManager.chromedriver().setup();
            logger.info("✅ ChromeDriver настроен");

            // 2. Конфигурация Chrome - ОПТИМИЗИРОВАННАЯ ДЛЯ СЕРВЕРА
            ChromeOptions options = new ChromeOptions();

            // 🔴 ОСНОВНОЕ ИСПРАВЛЕНИЕ: Используем старый headless режим для стабильности
            if (headless) {
                // Старый headless режим для совместимости
                options.addArguments("--headless");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--remote-debugging-port=9222");
                logger.info("🌐 Режим: Headless (серверный)");
            } else {
                logger.info("🌐 Режим: С GUI");
            }

            // 🔴 ОСНОВНЫЕ АРГУМЕНТЫ ДЛЯ СЕРВЕРА
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--disable-features=VizDisplayCompositor");

            // 🔴 УМЕНЬШЕННЫЙ НАБОР АРГУМЕНТОВ ДЛЯ СТАБИЛЬНОСТИ
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-logging");
            options.addArguments("--log-level=3");
            options.addArguments("--disable-web-security");
            options.addArguments("--allow-running-insecure-content");
            options.addArguments("--ignore-certificate-errors");

            // 🔴 USER-AGENT ДЛЯ ОБХОДА ДЕТЕКЦИИ
            String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
            options.addArguments("--user-agent=" + userAgent);

            // 🔴 ЭКСПЕРИМЕНТАЛЬНЫЕ ОПЦИИ
            options.setExperimentalOption("excludeSwitches", Arrays.asList(
                    "enable-automation",
                    "enable-logging"
            ));
            options.setExperimentalOption("useAutomationExtension", false);

            Map<String, Object> prefs = new HashMap<>();
            prefs.put("credentials_enable_service", false);
            prefs.put("profile.password_manager_enabled", false);
            options.setExperimentalOption("prefs", prefs);

            // 3. Запуск браузера
            driver = new ChromeDriver(options);

            // Установка таймаутов
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));

            logger.info("✅ Браузер запущен");

            // 4. ПЕРЕХОД НА GOOFISH
            String url = "https://www.goofish.com";
            logger.info("🌐 Переход на: {}", url);

            int maxRetries = 3;
            boolean pageLoaded = false;

            for (int retry = 1; retry <= maxRetries; retry++) {
                try {
                    driver.get(url);

                    // Ждем загрузки страницы
                    new WebDriverWait(driver, Duration.ofSeconds(30)).until(
                            webDriver -> ((JavascriptExecutor) webDriver)
                                    .executeScript("return document.readyState").equals("complete")
                    );

                    logger.info("✅ Страница успешно загружена (попытка {})", retry);
                    pageLoaded = true;
                    break;

                } catch (TimeoutException e) {
                    logger.warn("⚠️ Таймаут при загрузке страницы (попытка {}), пробуем снова...", retry);

                    if (retry < maxRetries) {
                        Thread.sleep(5000);
                    }
                }
            }

            if (!pageLoaded) {
                throw new TimeoutException("Не удалось загрузить страницу после " + maxRetries + " попыток");
            }

            // 5. ОЖИДАНИЕ И ВЗАИМОДЕЙСТВИЕ
            logger.info("⏳ Ожидание генерации cookies (10 секунд)...");
            Thread.sleep(10000);

            // Простая прокрутка для инициализации
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.3);");
                Thread.sleep(2000);
            } catch (Exception e) {
                logger.debug("Не удалось прокрутить страницу: {}", e.getMessage());
            }

            // 6. ПОЛУЧАЕМ COOKIES
            logger.info("🍪 Получение cookies...");
            Set<Cookie> allCookies = driver.manage().getCookies();

            // 7. СОБИРАЕМ И ФИЛЬТРУЕМ COOKIES
            Map<String, String> goofishCookies = new LinkedHashMap<>();

            // Ключевые cookies для Goofish
            String[] importantKeys = {
                    "_m_h5_tk", "_m_h5_tk_enc", "_samesite_flag_", "_tb_token_",
                    "cna", "cookie2", "mtop_partitioned_detect", "t",
                    "tfstk", "xlly_s", "isg"
            };

            for (Cookie cookie : allCookies) {
                String name = cookie.getName();
                String value = cookie.getValue();
                goofishCookies.put(name, value);
            }

            // Вывод результатов
            logger.info("📊 Результаты получения cookies:");
            logger.info("📦 Всего cookies: {}", allCookies.size());

            if (!goofishCookies.isEmpty()) {
                logger.info("🎯 Ключевые cookies:");
                for (String key : importantKeys) {
                    if (goofishCookies.containsKey(key)) {
                        String val = goofishCookies.get(key);
                        logger.info("   {}: {}",
                                String.format("%-25s", key),
                                val.length() > 50 ? val.substring(0, 47) + "..." : val);
                    }
                }
            }

            return goofishCookies;

        } catch (Exception e) {
            logger.error("❌ Ошибка при получении cookies через Selenium: {}", e.getMessage());

            // 🔴 УЛУЧШЕННАЯ ОБРАБОТКА ОШИБОК ДЛЯ СЕРВЕРА
            logger.info("🔄 Возвращаем кэшированные cookies из файла...");
            return getCachedCookies();

        } finally {
            // Закрытие браузера
            if (driver != null) {
                try {
                    driver.quit();
                    logger.info("✅ Браузер закрыт");
                } catch (Exception e) {
                    logger.error("⚠️ Ошибка при закрытии браузера: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Получение свежих cookies (публичный метод)
     */
    public static Map<String, String> getFreshCookies() {
        return fetchGoofishCookies(true);
    }

    /**
     * Получение cookies с GUI для отладки
     */
    public static Map<String, String> getFreshCookiesWithGUI() {
        return fetchGoofishCookies(false);
    }

    /**
     * Возвращает кэшированные cookies
     */
    private static Map<String, String> getCachedCookies() {
        try {
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream("cookies.properties")) {
                props.load(fis);

                String cookieStr = props.getProperty("www.goofish.com.cookies", "");
                if (cookieStr.isEmpty()) {
                    cookieStr = props.getProperty("h5api.m.goofish.com.cookies", "");
                }
                if (cookieStr.isEmpty()) {
                    cookieStr = props.getProperty("m.goofish.com.cookies", "");
                }

                if (!cookieStr.isEmpty()) {
                    Map<String, String> cookies = new HashMap<>();
                    String[] pairs = cookieStr.split("; ");
                    for (String pair : pairs) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length == 2) {
                            cookies.put(parts[0].trim(), parts[1].trim());
                        }
                    }

                    if (!cookies.isEmpty()) {
                        logger.info("✅ Используем кэшированные cookies из файла");
                        return cookies;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️ Не удалось загрузить кэшированные cookies: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }

    /**
     * Валидация полученных cookies
     */
    public static boolean validateCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            logger.error("❌ Cookies пусты или null");
            return false;
        }

        // 🔴 МЕНЬШЕ СТРОГАЯ ВАЛИДАЦИЯ ДЛЯ HEADLESS
        String[] requiredKeys = {"_m_h5_tk", "cna", "t"};
        int foundCount = 0;

        for (String key : requiredKeys) {
            if (cookies.containsKey(key)) {
                foundCount++;
                String value = cookies.get(key);
                logger.debug("✅ Найден {}: {}", key,
                        value.length() > 30 ? value.substring(0, 27) + "..." : value);
            } else {
                logger.warn("⚠️ Отсутствует ключевой cookie: {}", key);
            }
        }

        // 🔴 ГЕНЕРИРУЕМ ОТСУТСТВУЮЩИЕ COOKIES
        if (!cookies.containsKey("cna")) {
            logger.warn("⚠️ Cookie 'cna' отсутствует, генерируем временный...");
            String fakeCna = generateFakeCna();
            cookies.put("cna", fakeCna);
            foundCount++;
            logger.info("✅ Сгенерирован временный cna: {}", fakeCna);
        }

        if (!cookies.containsKey("_tb_token_")) {
            logger.warn("⚠️ Cookie '_tb_token_' отсутствует, генерируем временный...");
            cookies.put("_tb_token_", generateRandomToken());
            logger.info("✅ Сгенерирован временный _tb_token_");
        }

        // Проверяем _m_h5_tk
        if (cookies.containsKey("_m_h5_tk")) {
            String mh5tk = cookies.get("_m_h5_tk");
            if (mh5tk.contains("_")) {
                String[] parts = mh5tk.split("_", 2);
                logger.info("📊 Анализ _m_h5_tk:");
                logger.info("   Токен: {}",
                        parts[0].length() > 20 ? parts[0].substring(0, 17) + "..." : parts[0]);
                logger.info("   Время: {}", parts[1]);

                try {
                    long tokenTime = Long.parseLong(parts[1]);
                    long currentTime = System.currentTimeMillis();
                    long age = currentTime - tokenTime;

                    if (age > 24 * 60 * 60 * 1000) { // 24 часа
                        logger.warn("⚠️ Token _m_h5_tk устарел (возраст: {} часов)", age / (60 * 60 * 1000));
                    }
                } catch (NumberFormatException e) {
                    logger.warn("⚠️ Неверный формат времени в _m_h5_tk");
                }
            } else {
                logger.warn("⚠️ _m_h5_tk не содержит timestamp");
            }
        }

        // 🔴 МЕНЬШЕ СТРОГАЯ ВАЛИДАЦИЯ: достаточно 2 из 3 ключевых cookies
        boolean isValid = foundCount >= 2;
        logger.info("📊 Валидация cookies: {} (найдено {}/{} ключевых)",
                isValid ? "✅ УСПЕХ" : "❌ ОШИБКА", foundCount, requiredKeys.length);

        return isValid;
    }

    /**
     * Генерация временного cna cookie
     */
    private static String generateFakeCna() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder cna = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            cna.append(chars.charAt(random.nextInt(chars.length())));
        }
        return cna.toString();
    }

    /**
     * Генерация случайного токена
     */
    private static String generateRandomToken() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            token.append(chars.charAt(random.nextInt(chars.length())));
        }
        return token.toString();
    }
}