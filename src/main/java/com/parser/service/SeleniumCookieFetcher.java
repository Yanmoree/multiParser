package com.parser.service;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Сервис для получения cookies через Selenium
 */
public class SeleniumCookieFetcher {
    private static final Logger logger = LoggerFactory.getLogger(SeleniumCookieFetcher.class);

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

            // Китайский User-Agent
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
            options.addArguments("--user-agent=" + userAgent);

            // Убираем признаки автоматизации
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);

            // 3. Запуск браузера
            driver = new ChromeDriver(options);
            logger.info("✅ Браузер запущен");

            // Настройка таймаутов
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

            // 4. Переход на сайт
            String url = "https://www.goofish.com";
            logger.info("🌐 Переход на: {}", url);
            driver.get(url);

            // 5. Ожидание загрузки
            logger.info("⏳ Ожидание загрузки страницы...");
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(d -> ((JavascriptExecutor) d)
                                .executeScript("return document.readyState").equals("complete"));
            } catch (TimeoutException e) {
                logger.warn("⚠️ Страница загружена не полностью, продолжаем...");
            }

            // 6. Дополнительное ожидание для динамического контента
            Thread.sleep(3000);

            // 7. Прокрутка для инициализации динамического контента
            ((JavascriptExecutor) driver).executeScript(
                    "window.scrollTo(0, document.body.scrollHeight * 0.3);"
            );
            Thread.sleep(1000);
            ((JavascriptExecutor) driver).executeScript(
                    "window.scrollTo(0, document.body.scrollHeight * 0.6);"
            );
            Thread.sleep(1000);

            // 8. Получение cookies
            logger.info("🔍 Получение cookies...");
            Set<Cookie> allCookies = driver.manage().getCookies();

            // 9. Фильтрация важных cookies
            List<String> importantKeys = Arrays.asList(
                    "_m_h5_tk", "_m_h5_tk_enc", "_tb_token_", "cna",
                    "t", "cookie2", "cookie17", "l", "isg",
                    "uc1", "unb", "uc3", "tracknick", "lgc"
            );

            Map<String, String> goofishCookies = new LinkedHashMap<>();

            for (Cookie cookie : allCookies) {
                String name = cookie.getName();
                String value = cookie.getValue();

                // Выделяем важные cookies
                if (importantKeys.contains(name)) {
                    goofishCookies.put(name, value);
                    logger.debug("Найден cookie: {} = {}", name,
                            value.length() > 50 ? value.substring(0, 47) + "..." : value);
                }
            }

            // 10. Вывод результатов
            logger.info("📊 Результаты:");
            logger.info("📦 Всего cookies: {}", allCookies.size());
            logger.info("🔑 Важных cookies: {}", goofishCookies.size());

            if (!goofishCookies.isEmpty()) {
                logger.info("🎯 Важные cookies:");
                for (Map.Entry<String, String> entry : goofishCookies.entrySet()) {
                    String val = entry.getValue();
                    logger.info("   {}: {}",
                            String.format("%-20s", entry.getKey()),
                            val.length() > 50 ? val.substring(0, 47) + "..." : val);
                }

                // Анализ _m_h5_tk
                if (goofishCookies.containsKey("_m_h5_tk")) {
                    String mh5tk = goofishCookies.get("_m_h5_tk");
                    if (mh5tk.contains("_")) {
                        String[] parts = mh5tk.split("_", 2);
                        logger.info("📊 Анализ _m_h5_tk:");
                        logger.info("   Токен: {}",
                                parts[0].length() > 20 ? parts[0].substring(0, 20) + "..." : parts[0]);
                        logger.info("   Время: {}", parts[1]);
                    }
                }
            }

            return goofishCookies;

        } catch (Exception e) {
            logger.error("❌ Ошибка при получении cookies через Selenium: {}", e.getMessage(), e);
            return Collections.emptyMap();
        } finally {
            // 12. Закрытие браузера
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
     * Получение свежих cookies (публичный метод для использования в других классах)
     */
    public static Map<String, String> getFreshCookies() {
        // По умолчанию в headless режиме
        return fetchGoofishCookies(true);
    }

    /**
     * Получение cookies с GUI для отладки
     */
    public static Map<String, String> getFreshCookiesWithGUI() {
        return fetchGoofishCookies(false);
    }

    /**
     * Проверка валидности текущих cookies
     */
    public static boolean validateCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return false;
        }

        // Проверяем наличие ключевых cookies
        String[] requiredKeys = {"_m_h5_tk", "_tb_token_", "cna"};
        for (String key : requiredKeys) {
            if (!cookies.containsKey(key) ||
                    cookies.get(key) == null ||
                    cookies.get(key).isEmpty()) {
                logger.warn("❌ Отсутствует обязательный cookie: {}", key);
                return false;
            }
        }

        // Проверяем формат _m_h5_tk
        String mh5tk = cookies.get("_m_h5_tk");
        if (!mh5tk.contains("_")) {
            logger.warn("❌ Неверный формат _m_h5_tk: {}", mh5tk);
            return false;
        }

        logger.info("✅ Cookies прошли валидацию");
        return true;
    }
}