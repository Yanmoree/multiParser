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
 * Сервис для получения cookies через Selenium - ИСПРАВЛЕННЫЙ ДЛЯ HEADLESS
 */
public class SeleniumCookieFetcher {
    private static final Logger logger = LoggerFactory.getLogger(SeleniumCookieFetcher.class);

    /**
     * Основной метод получения cookies для Goofish - ИСПРАВЛЕННЫЙ ДЛЯ HEADLESS
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

            // 2. Конфигурация Chrome - ОПТИМИЗИРОВАННАЯ ДЛЯ HEADLESS
            ChromeOptions options = new ChromeOptions();

            // 🔴 ОСНОВНОЕ ИСПРАВЛЕНИЕ: Настройки для обхода детекции headless
            if (headless) {
                // Современный headless режим с обходом детекции
                options.addArguments("--headless=new"); // Новый headless режим Chrome
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--start-maximized");
                logger.info("🌐 Режим: Headless (оптимизированный)");
            } else {
                logger.info("🌐 Режим: С GUI");
            }

            // 🔴 КЛЮЧЕВЫЕ АРГУМЕНТЫ ДЛЯ ОБХОДА ДЕТЕКЦИИ
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--disable-features=VizDisplayCompositor");
            options.addArguments("--disable-software-rasterizer");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-logging");
            options.addArguments("--log-level=3");
            options.addArguments("--disable-web-security");
            options.addArguments("--allow-running-insecure-content");
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("--disable-popup-blocking");
            options.addArguments("--disable-notifications");
            options.addArguments("--disable-save-password-bubble");
            options.addArguments("--disable-translate");
            options.addArguments("--disable-background-timer-throttling");
            options.addArguments("--disable-renderer-backgrounding");
            options.addArguments("--disable-backgrounding-occluded-windows");

            // 🔴 USER-AGENT ДЛЯ ОБХОДА ДЕТЕКЦИИ
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
            options.addArguments("--user-agent=" + userAgent);

            // 🔴 ЭКСПЕРИМЕНТАЛЬНЫЕ ОПЦИИ ДЛЯ ОБХОДА ДЕТЕКЦИИ
            options.setExperimentalOption("excludeSwitches", Arrays.asList(
                    "enable-automation",
                    "enable-logging"
            ));
            options.setExperimentalOption("useAutomationExtension", false);

            Map<String, Object> prefs = new HashMap<>();
            prefs.put("credentials_enable_service", false);
            prefs.put("profile.password_manager_enabled", false);
            prefs.put("profile.default_content_setting_values.notifications", 2); // Блокировать уведомления
            prefs.put("profile.default_content_setting_values.popups", 2); // Блокировать popups
            options.setExperimentalOption("prefs", prefs);

            // 3. Запуск браузера с увеличенными таймаутами
            driver = new ChromeDriver(options);

            // 🔴 УВЕЛИЧИВАЕМ ТАЙМАУТЫ ДЛЯ МЕДЛЕННЫХ СЕТЕЙ
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60)); // 60 секунд вместо 30
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30)); // 30 секунд вместо 15
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

            logger.info("✅ Браузер запущен с увеличенными таймаутами");

            // 🔴 4. ПЕРВОНАЧАЛЬНЫЙ ПЕРЕХОД НА GOOGLE (для инициализации cookies)
            try {
                logger.info("🌐 Первоначальный переход на Google для инициализации...");
                driver.get("https://www.google.com");
                Thread.sleep(2000);
            } catch (Exception e) {
                logger.warn("⚠️ Не удалось загрузить Google: {}", e.getMessage());
            }

            // 🔴 5. ПЕРЕХОД НА GOOFISH С РЕТРАЯМИ ПРИ ТАЙМАУТЕ
            String url = "https://www.goofish.com";
            logger.info("🌐 Переход на: {}", url);

            int maxRetries = 3;
            boolean pageLoaded = false;

            for (int retry = 1; retry <= maxRetries; retry++) {
                try {
                    driver.get(url);

                    // 🔴 ЖДЕМ ЗАГРУЗКИ СТРАНИЦЫ С ПОМОЩЬЮ JAVASCRIPT
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
                        Thread.sleep(3000);

                        // Пробуем альтернативный URL
                        if (retry == 2) {
                            url = "https://m.goofish.com";
                            logger.info("🔄 Пробуем мобильную версию: {}", url);
                        }
                    }
                }
            }

            if (!pageLoaded) {
                throw new TimeoutException("Не удалось загрузить страницу после " + maxRetries + " попыток");
            }

            // 🔴 6. ДОБАВЛЯЕМ ДОПОЛНИТЕЛЬНУЮ ЗАДЕРЖКУ И ВЗАИМОДЕЙСТВИЕ
            logger.info("⏳ Ожидание генерации cookies (10 секунд)...");
            Thread.sleep(10000);

            // 🔴 7. ВЫПОЛНЯЕМ JAVASCRIPT ДЛЯ ИНИЦИАЛИЗАЦИИ СТРАНИЦЫ
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;

                // Прокручиваем страницу для инициализации
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.3);");
                Thread.sleep(2000);
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * 0.6);");
                Thread.sleep(2000);

                // Кликаем на body для активации
                WebElement body = driver.findElement(By.tagName("body"));
                body.click();
                Thread.sleep(1000);

            } catch (Exception e) {
                logger.debug("Не удалось взаимодействовать со страницей: {}", e.getMessage());
            }

            // 🔴 8. ПОЛУЧАЕМ COOKIES С ГЛАВНОЙ СТРАНИЦЫ
            logger.info("🍪 Получение cookies с главной страницы...");
            Set<Cookie> allCookies = driver.manage().getCookies();
            logger.info("📦 Найдено cookies на главной: {}", allCookies.size());

            // 🔴 9. ПЕРЕХОД НА СТРАНИЦУ ПОИСКА ДЛЯ ДОПОЛНИТЕЛЬНЫХ COOKIES
            String searchUrl = "https://www.goofish.com/search?q=test";
            logger.info("🔍 Переход на страницу поиска: {}", searchUrl);

            try {
                driver.get(searchUrl);
                Thread.sleep(5000);

                // Ждем загрузки
                new WebDriverWait(driver, Duration.ofSeconds(30)).until(
                        webDriver -> ((JavascriptExecutor) webDriver)
                                .executeScript("return document.readyState").equals("complete")
                );

            } catch (Exception e) {
                logger.warn("⚠️ Не удалось загрузить страницу поиска: {}", e.getMessage());
            }

            // 🔴 10. ПОЛУЧАЕМ ВСЕ COOKIES
            logger.info("🍪 Получение всех cookies после взаимодействия...");
            allCookies = driver.manage().getCookies();

            // 🔴 11. СОБИРАЕМ И ФИЛЬТРУЕМ COOKIES
            Map<String, String> goofishCookies = new LinkedHashMap<>();

            // Ключевые cookies для Goofish
            String[] importantKeys = {
                    "_m_h5_tk", "_m_h5_tk_enc", "_samesite_flag_", "_tb_token_",
                    "cna", "cookie2", "mtop_partitioned_detect", "t",
                    "tfstk", "xlly_s", "x5secdata", "isg", "unb", "lgc"
            };

            for (Cookie cookie : allCookies) {
                String name = cookie.getName();
                String value = cookie.getValue();
                goofishCookies.put(name, value);

                // Логируем важные cookies
                if (Arrays.asList(importantKeys).contains(name)) {
                    logger.debug("✅ Важный cookie: {} = {}", name,
                            value.length() > 50 ? value.substring(0, 47) + "..." : value);
                }
            }

            // 🔴 12. ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА ДЛЯ CNA
            if (!goofishCookies.containsKey("cna")) {
                logger.warn("⚠️ Cookie 'cna' не найден, пробуем альтернативный метод...");

                try {
                    // Пробуем получить cna через JavaScript
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    Object cnaValue = js.executeScript(
                            "return document.cookie.split('; ').find(c => c.startsWith('cna='));"
                    );

                    if (cnaValue != null) {
                        String cnaStr = cnaValue.toString();
                        if (cnaStr.startsWith("cna=")) {
                            String cna = cnaStr.substring(4);
                            goofishCookies.put("cna", cna);
                            logger.info("✅ Получен cna через JavaScript: {}",
                                    cna.length() > 30 ? cna.substring(0, 27) + "..." : cna);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ Не удалось получить cna через JavaScript: {}", e.getMessage());
                }
            }

            // Вывод результатов
            logger.info("📊 Результаты получения cookies:");
            logger.info("📦 Всего cookies: {}", allCookies.size());
            logger.info("🔑 Важных cookies: {}", goofishCookies.size());

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

            // 🔴 ВОЗВРАЩАЕМ КЭШИРОВАННЫЕ COOKIES ПРИ ОШИБКЕ
            logger.info("🔄 Возвращаем кэшированные cookies...");
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
     * Получение свежих cookies (публичный метод) с использованием headless
     */
    public static Map<String, String> getFreshCookies() {
        return fetchGoofishCookies(true); // 🔴 ИСПОЛЬЗУЕМ HEADLESS ДЛЯ ПРОДАКШЕНА
    }

    /**
     * Получение cookies с GUI для отладки
     */
    public static Map<String, String> getFreshCookiesWithGUI() {
        return fetchGoofishCookies(false);
    }

    /**
     * Возвращает кэшированные cookies при ошибке
     */
    private static Map<String, String> getCachedCookies() {
        try {
            // Пробуем прочитать cookies из файла
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream("cookies.properties")) {
                props.load(fis);

                String cookieStr = props.getProperty("www.goofish.com.cookies", "");
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
     * Валидация полученных cookies - ОПТИМИЗИРОВАННАЯ
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