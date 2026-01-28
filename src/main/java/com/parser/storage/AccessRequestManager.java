package com.parser.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Хранит заявки на доступ к боту (когда пользователь без whitelist пытается воспользоваться ботом).
 * Сделано максимально просто: запись в файл в data-dir через FileStorage.
 */
public class AccessRequestManager {
    private static final Logger logger = LoggerFactory.getLogger(AccessRequestManager.class);

    private static final String REQUESTS_FILE = "access_requests.txt";
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Записать заявку (если для пользователя ещё нет записи).
     */
    public static void recordAccessRequest(long userId, String reason) {
        if (userId <= 0) return;

        try {
            List<String> lines = FileStorage.readLines(REQUESTS_FILE);
            for (String line : lines) {
                if (line.startsWith(userId + "|")) {
                    return; // уже есть заявка
                }
            }

            String safeReason = reason == null ? "" : reason.replace("|", "/").trim();
            String entry = userId + "|" + TS.format(new Date()) + "|" + safeReason;
            lines.add(entry);
            FileStorage.writeLines(REQUESTS_FILE, lines);
            logger.info("Access request recorded for user {}", userId);
        } catch (Exception e) {
            logger.warn("Failed to record access request for {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Получить список заявок (сырьём).
     */
    public static List<String> getRequests() {
        return FileStorage.readLines(REQUESTS_FILE);
    }

    /**
     * Удалить заявку пользователя.
     */
    public static void removeRequest(long userId) {
        try {
            List<String> lines = FileStorage.readLines(REQUESTS_FILE);
            List<String> updated = new ArrayList<>();
            for (String line : lines) {
                if (!line.startsWith(userId + "|")) {
                    updated.add(line);
                }
            }
            FileStorage.writeLines(REQUESTS_FILE, updated);
        } catch (Exception e) {
            logger.warn("Failed to remove access request for {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Очистить все заявки.
     */
    public static void clearAll() {
        FileStorage.writeLines(REQUESTS_FILE, List.of());
    }
}

