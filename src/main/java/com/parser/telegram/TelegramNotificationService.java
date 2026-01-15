package com.parser.telegram;

import com.parser.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.net.URL;

/**
 * Сервис для отправки уведомлений через Telegram
 */
public class TelegramNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificationService.class);

    private static TelegramBotService botInstance;

    /**
     * Установка экземпляра бота для отправки уведомлений
     */
    public static void setBotInstance(TelegramBotService bot) {
        botInstance = bot;
        logger.info("TelegramNotificationService initialized with bot instance");
    }

    /**
     * Отправка текстового сообщения
     */
    public static boolean sendMessage(int userId, String text) {
        if (botInstance == null) {
            logger.error("Bot instance not set for TelegramNotificationService");
            return false;
        }

        if (text == null || text.trim().isEmpty()) {
            logger.warn("Attempted to send empty message to user {}", userId);
            return false;
        }

        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(userId));
            message.setText(text);
            message.enableMarkdown(true);

            botInstance.execute(message);
            logger.debug("Message sent to user {}: {}", userId, text.substring(0, Math.min(50, text.length())));
            return true;

        } catch (TelegramApiException e) {
            logger.error("Error sending message to user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Отправка сообщения с фото
     */
    public static boolean sendPhoto(int userId, String photoUrl, String caption) {
        if (botInstance == null) {
            logger.error("Bot instance not set for TelegramNotificationService");
            return false;
        }

        try {
            SendPhoto photo = new SendPhoto();
            photo.setChatId(String.valueOf(userId));
            photo.setPhoto(new InputFile(photoUrl));

            if (caption != null && !caption.isEmpty()) {
                photo.setCaption(caption);
                if (caption.length() > 1024) {
                    photo.setCaption(caption.substring(0, 1024));
                }
            }

            botInstance.execute(photo);
            logger.debug("Photo sent to user {}: {}", userId, photoUrl);
            return true;

        } catch (Exception e) {
            logger.error("Error sending photo to user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Отправка сообщения с форматированием
     */
    public static boolean sendTestNotification(int userId) {
        logger.info("Sending test notification to user {}", userId);

        String message = "✅ **Тестовое уведомление**\n\n" +
                "Парсер работает корректно!\n" +
                "Это тестовое сообщение подтверждает, что система уведомлений функционирует.\n\n" +
                "Время: " + new java.util.Date();

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления о найденных товарах
     */
    public static boolean sendProductsNotification(int userId, int count, String query) {
        String message = String.format("""
            🛍️ **Найдены товары!**

            По запросу: *%s*
            Найдено товаров: *%d*

            Подробности в следующем сообщении...
            """, query, count);

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления об ошибке
     */
    public static boolean sendErrorNotification(int userId, String errorMessage) {
        String message = String.format("""
            ❌ **Ошибка парсера**

            Произошла ошибка:
            `%s`

            Парсер будет перезапущен автоматически.
            """, errorMessage);

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления о состоянии парсера
     */
    public static boolean sendStatusNotification(int userId, String status, String details) {
        String emoji = "🟢";
        if (status.contains("остановлен")) emoji = "🔴";
        if (status.contains("приостановлен")) emoji = "⏸️";
        if (status.contains("ошибка")) emoji = "❌";

        String message = String.format("""
            %s **Статус парсера изменен**

            Новый статус: *%s*

            %s
            """, emoji, status, details);

        return sendMessage(userId, message);
    }


    /**
     * Отправка уведомления администратору
     */
    public static boolean sendAdminNotification(String message) {
        long adminId = Config.getInt("telegram.admin.id", 0);
        if (adminId == 0) {
            logger.warn("Admin ID not configured");
            return false;
        }

        String adminMessage = String.format("""
            👑 **Админское уведомление**

            %s

            Время: %s
            """, message, new java.util.Date());

        return sendMessage((int) adminId, adminMessage);
    }

    /**
     * Проверка доступности бота
     */
    public static boolean isBotAvailable() {
        return botInstance != null;
    }

    /**
     * Получение статистики отправки уведомлений
     */
    public static String getStats() {
        if (botInstance == null) {
            return "Bot not initialized";
        }

        // В реальном проекте здесь можно вести статистику отправленных сообщений
        return "TelegramNotificationService is operational";
    }
}