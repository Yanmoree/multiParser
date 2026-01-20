package com.parser.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Сервис для получения cookies через Selenium
 */
public class SeleniumCookieFetcher {
    private static final Logger logger = LoggerFactory.getLogger(SeleniumCookieFetcher.class);

    /**
     * Основной метод получения cookies для Goofish
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

            // 2. Конфигурация Chrome
            ChromeOptions options = new ChromeOptions();

            // Headless режим
            if (headless) {
                options.addArguments("--headless=new");
                logger.info("🌐 Режим: Headless");
            } else {
                logger.info("🌐 Режим: С GUI (для отладки)");
            }

            // Опции для обхода защиты
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-features=VizDisplayCompositor");
            options.addArguments("--disable-software-rasterizer");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-logging");
            options.addArguments("--log-level=3");

            // Китайский User-Agent из примера запроса
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 YaBrowser/25.10.0.0 Safari/537.36";
            options.addArguments("--user-agent=" + userAgent);

            // Убираем признаки автоматизации
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);

            // 3. Запуск браузера
            driver = new ChromeDriver(options);
            logger.info("✅ Браузер запущен");

            // Настройка таймаутов
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            // 4. Переход на главную страницу Goofish
            String url = "https://www.goofish.com";
            logger.info("🌐 Переход на: {}", url);
            driver.get(url);

            // 5. Ожидание загрузки
            logger.info("⏳ Ожидание загрузки страницы...");
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(d -> ((JavascriptExecutor) d)
                                .executeScript("return document.readyState").equals("complete"));

                // Дополнительное ожидание для инициализации куки
                Thread.sleep(5000);

                // 6. Прокрутка для активации JavaScript
                ((JavascriptExecutor) driver).executeScript(
                        "window.scrollTo(0, document.body.scrollHeight * 0.3);"
                );
                Thread.sleep(2000);

                ((JavascriptExecutor) driver).executeScript(
                        "window.scrollTo(0, document.body.scrollHeight * 0.6);"
                );
                Thread.sleep(2000);

                // 7. Переход на страницу поиска для получения полных куки
                String searchUrl = "https://www.goofish.com/search?q=test&spm=a21ybx.search.searchInput.0";
                logger.info("🔍 Переход на страницу поиска: {}", searchUrl);
                driver.get(searchUrl);

                Thread.sleep(5000);

                // 8. Получение всех cookies
                logger.info("🍪 Получение cookies...");
                Set<Cookie> allCookies = driver.manage().getCookies();

                // 9. Фильтрация и сбор важных cookies
                Map<String, String> goofishCookies = new LinkedHashMap<>();

                // Ключевые cookies из примера запроса
                String[] importantKeys = {
                        "_m_h5_tk", "_m_h5_tk_enc", "_samesite_flag_", "_tb_token_",
                        "cna", "cookie2", "mtop_partitioned_detect", "t",
                        "tfstk", "xlly_s", "x5secdata", "isg", "unb", "lgc"
                };

                for (Cookie cookie : allCookies) {
                    String name = cookie.getName();
                    String value = cookie.getValue();

                    // Сохраняем все куки, но выделяем важные
                    goofishCookies.put(name, value);

                    // Логируем важные куки
                    if (Arrays.asList(importantKeys).contains(name)) {
                        logger.debug("✅ Важный cookie: {} = {}", name,
                                value.length() > 50 ? value.substring(0, 47) + "..." : value);
                    }
                }

                // 10. Вывод результатов
                logger.info("📊 Результаты:");
                logger.info("📦 Всего cookies: {}", allCookies.size());
                logger.info("🔑 Важных cookies: {}", goofishCookies.size());

                if (!goofishCookies.isEmpty()) {
                    logger.info("🎯 Ключевые cookies:");
                    for (String key : importantKeys) {
                        if (goofishCookies.containsKey(key)) {
                            String val = goofishCookies.get(key);
                            logger.info("   {}: {}",
                                    String.format("%-20s", key),
                                    val.length() > 50 ? val.substring(0, 47) + "..." : val);
                        }
                    }
                }

                return goofishCookies;

            } catch (TimeoutException e) {
                logger.error("❌ Таймаут при загрузке страницы: {}", e.getMessage());
                return Collections.emptyMap();
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при получении cookies через Selenium: {}", e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
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
     * Валидация полученных cookies
     */
    public static boolean validateCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            logger.error("❌ Cookies пусты или null");
            return false;
        }

        // Проверяем наличие ключевых cookies
        String[] requiredKeys = {"_m_h5_tk", "_tb_token_", "cna", "cookie2", "t"};
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

        // Проверяем _m_h5_tk на наличие timestamp
        if (cookies.containsKey("_m_h5_tk")) {
            String mh5tk = cookies.get("_m_h5_tk");
            if (mh5tk.contains("_")) {
                String[] parts = mh5tk.split("_", 2);
                logger.info("📊 Анализ _m_h5_tk:");
                logger.info("   Токен: {}",
                        parts[0].length() > 20 ? parts[0].substring(0, 17) + "..." : parts[0]);
                logger.info("   Время: {}", parts[1]);
            } else {
                logger.warn("⚠️ _m_h5_tk не содержит timestamp");
            }
        }

        boolean isValid = foundCount >= 3; // Минимум 3 ключевых cookie
        logger.info("📊 Валидация cookies: {} (найдено {}/{} ключевых)",
                isValid ? "✅ УСПЕХ" : "❌ ОШИБКА", foundCount, requiredKeys.length);

        return isValid;
    }

    /**
     * Тестовый метод для запуска из командной строки
     */
    public static void main(String[] args) {
        System.out.println("Тестирование SeleniumCookieFetcher...");

        // Тест с GUI (для отладки)
        System.out.println("\n1. Тест с GUI:");
        Map<String, String> guiCookies = getFreshCookiesWithGUI();
        System.out.println("Получено cookies с GUI: " + guiCookies.size());

        // Тест в headless режиме
        System.out.println("\n2. Тест в headless режиме:");
        Map<String, String> headlessCookies = getFreshCookies();
        System.out.println("Получено cookies в headless: " + headlessCookies.size());

        // Валидация
        System.out.println("\n3. Валидация:");
        boolean isValid = validateCookies(headlessCookies);
        System.out.println("Cookies валидны: " + isValid);

        if (!headlessCookies.isEmpty()) {
            System.out.println("\n4. Пример cookies:");
            headlessCookies.forEach((key, value) -> {
                if (key.startsWith("_") || key.equals("cna") || key.equals("cookie2") || key.equals("t")) {
                    System.out.println(String.format("%-20s: %s",
                            key, value.length() > 50 ? value.substring(0, 47) + "..." : value));
                }
            });
        }
    }
}