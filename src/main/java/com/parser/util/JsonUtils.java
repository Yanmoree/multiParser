package com.parser.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Утилиты для работы с JSON
 */
public class JsonUtils {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * Преобразование объекта в JSON строку
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            logger.error("Error converting object to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Преобразование JSON строки в объект
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            logger.error("Error parsing JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Проверка валидности JSON строки
     */
    public static boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Форматирование JSON строки
     */
    public static String formatJson(String json) {
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (Exception e) {
            logger.error("Error formatting JSON: {}", e.getMessage());
            return json;
        }
    }

    /**
     * Создание JSON из ключ-значение
     */
    public static String createJson(String key, String value) {
        return String.format("{\"%s\": \"%s\"}", key, escapeJson(value));
    }

    /**
     * Экранирование строки для JSON
     */
    public static String escapeJson(String str) {
        if (str == null) return "";

        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Получение значения из JSON строки по ключу
     */
    public static String getJsonValue(String json, String key) {
        try {
            var node = objectMapper.readTree(json);
            var valueNode = node.get(key);
            return valueNode != null ? valueNode.asText() : null;
        } catch (Exception e) {
            logger.error("Error getting JSON value for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Обновление значения в JSON строке
     */
    public static String updateJsonValue(String json, String key, String value) {
        try {
            var node = objectMapper.readTree(json);
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put(key, value);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            logger.error("Error updating JSON value for key {}: {}", key, e.getMessage());
            return json;
        }
    }

    /**
     * Получение ObjectMapper экземпляра
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}