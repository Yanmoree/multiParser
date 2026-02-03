package com.parser.service;

import com.parser.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoCleanupService {
    private static final Logger logger = LoggerFactory.getLogger(AutoCleanupService.class);
    private static ScheduledExecutorService scheduler;

    public static void start() {
        scheduler = Executors.newScheduledThreadPool(1);

        // Очистка старых логов каждые 24 часа
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupOldLogs();
                cleanupTempFiles();
                cleanupOldBackups();
            } catch (Exception e) {
                logger.error("Ошибка при очистке файлов: {}", e.getMessage());
            }
        }, 1, 24, TimeUnit.HOURS); // Запуск через 1 час, затем каждые 24 часа

        logger.info("Сервис автоочистки запущен");
    }

    private static void cleanupOldLogs() {
        String logDir = "logs";
        File dir = new File(logDir);
        if (!dir.exists()) return;

        long cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000); // 30 дней

        for (File file : dir.listFiles()) {
            if (file.isFile() && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    logger.debug("Удален старый лог: {}", file.getName());
                }
            }
        }
    }

    private static void cleanupTempFiles() {
        // Удаляем старые debug файлы кук
        File currentDir = new File(".");
        long cutoffTime = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000); // 7 дней

        for (File file : currentDir.listFiles()) {
            String name = file.getName();
            if (name.startsWith("cookies_debug_") && name.endsWith(".properties") &&
                    file.isFile() && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    logger.debug("Удален debug файл кук: {}", name);
                }
            }
        }
    }

    private static void cleanupOldBackups() {
        String backupDir = "data/backups";
        File dir = new File(backupDir);
        if (!dir.exists()) return;

        long cutoffTime = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000); // 14 дней

        for (File file : dir.listFiles()) {
            if (file.isFile() && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    logger.debug("Удалена старая резервная копия: {}", file.getName());
                }
            }
        }
    }

    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Сервис автоочистки остановлен");
        }
    }
}