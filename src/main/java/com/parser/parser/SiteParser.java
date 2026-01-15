package com.parser.parser;

import com.parser.model.Product;
import java.util.List;

/**
 * Интерфейс для всех парсеров сайтов
 */
public interface SiteParser {

    /**
     * Получение названия сайта
     */
    String getSiteName();

    /**
     * Поиск товаров по запросу
     *
     * @param query поисковый запрос
     * @param maxPages максимальное количество страниц для парсинга
     * @param rowsPerPage количество товаров на странице
     * @param maxAgeMinutes максимальный возраст товаров в минутах
     * @return список найденных товаров
     */
    List<Product> search(String query, int maxPages, int rowsPerPage, int maxAgeMinutes);

    /**
     * Поиск товаров с использованием настроек по умолчанию
     */
    default List<Product> search(String query) {
        return search(query, 3, 100, 1440);
    }

    /**
     * Проверка доступности сайта
     */
    default boolean isSiteAvailable() {
        return true; // Базовая реализация, можно переопределить
    }

    /**
     * Получение информации о парсере
     */
    default String getParserInfo() {
        return "SiteParser for " + getSiteName();
    }
}