package com.parser.telegram;

import com.parser.config.Config;
import com.parser.config.CookieConfig;
import com.parser.core.ThreadManager;
import com.parser.model.UserSettings;
import com.parser.service.CookieService;
import com.parser.storage.WhitelistManager;
import com.parser.storage.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

/**
 * Основной сервис Telegram бота
 */
public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private final ThreadManager threadManager;
    private final TelegramStateManager stateManager;
    private final long adminId;

    // Кэш сообщений для редактирования
    private final Map<Long, Integer> lastMessageIdCache = new HashMap<>();

    public TelegramBotService(String token, ThreadManager threadManager) {
        super(token);
        this.threadManager = threadManager;
        this.stateManager = new TelegramStateManager();
        this.adminId = Config.getInt("telegram.admin.id", 0);

        // Регистрация команд
        registerCommands();
        logger.info("TelegramBotService initialized");
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка текстовых сообщений
     */
    private void handleMessage(org.telegram.telegrambots.meta.api.objects.Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        long userId = chatId; // Используем long напрямую

        logger.info("Message from {} (user {}): {}", chatId, userId, text);

        // Обработка команд
        if (text.startsWith("/")) {
            handleCommand(chatId, userId, text, message.getMessageId());
        } else {
            // Обработка текстовых ответов
            handleTextResponse(chatId, userId, text, message.getMessageId());
        }
    }

    /**
     * Обработка команд
     */
    private void handleCommand(Long chatId, long userId, String command, Integer messageId) {
        try {
            String[] parts = command.split(" ", 2);
            String cmd = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            logger.debug("Command: {}, Args: {}", cmd, args);

            switch (cmd) {
                case "/start":
                    handleStart(chatId, userId);
                    break;

                case "/help":
                    sendHelpMessage(chatId);
                    break;

                case "/status":
                    handleStatus(chatId, userId);
                    break;

                case "/addquery":
                    handleAddQuery(chatId, userId, args);
                    break;

                case "/listqueries":
                    handleListQueries(chatId, userId);
                    break;

                case "/removequery":
                    handleRemoveQuery(chatId, userId, args);
                    break;

                case "/settings":
                    showSettingsMenu(chatId, userId);
                    break;

                case "/start_parser":
                    handleStartParser(chatId, userId);
                    break;

                case "/stop_parser":
                    handleStopParser(chatId, userId);
                    break;

                case "/pause_parser":
                    handlePauseParser(chatId, userId);
                    break;

                case "/resume_parser":
                    handleResumeParser(chatId, userId);
                    break;

                case "/stats":
                    handleStats(chatId, userId);
                    break;

                case "/clear":
                    handleClear(chatId, userId, args);
                    break;

                case "/admin":
                    handleAdmin(chatId, userId, args);
                    break;

                case "/cookies":
                    handleCookiesCommand(chatId, userId, args);
                    break;

                case "/checkwhitelist":
                    handleCheckWhitelist(chatId, userId);
                    break;

                case "/debug":
                    handleDebug(chatId, userId);
                    break;

                case "/getid":
                    handleGetIdCommand(chatId, userId);
                    break;

                default:
                    sendMessage(chatId, "❓ Неизвестная команда. Используйте /help для списка команд.");
            }

        } catch (Exception e) {
            logger.error("Error handling command: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    /**
     * Обработка текстовых ответов
     */
    private void handleTextResponse(Long chatId, long userId, String text, Integer messageId) {
        // Проверка состояния пользователя
        String state = stateManager.getUserState((int) userId); // Приводим к int для stateManager

        if (state == null) {
            sendMessage(chatId, "Для работы с ботом используйте команды. /help - список команд");
            return;
        }

        switch (state) {
            case "AWAITING_QUERY":
                // Добавление нового запроса
                if (UserDataManager.addUserQuery(userId, text)) {
                    sendMessage(chatId, "✅ Запрос добавлен: " + text);
                    stateManager.clearUserState((int) userId);
                } else {
                    sendMessage(chatId, "⚠️ Этот запрос уже существует");
                }
                break;

            case "AWAITING_SETTING_VALUE":
                // Обработка значения настройки
                handleSettingValue(chatId, userId, text);
                break;

            case "AWAITING_MIN_PRICE":
            case "AWAITING_MAX_PRICE":
                // Обработка ценового фильтра
                handlePriceFilter(chatId, userId, state, text);
                break;

            default:
                sendMessage(chatId, "Используйте команды для взаимодействия. /help - список команд");
        }
    }

    /**
     * Обработка callback запросов (инлайн-кнопки)
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        long userId = chatId; // Используем chatId как userId
        Integer messageId = callbackQuery.getMessage().getMessageId();

        logger.debug("Callback from {}: {}", chatId, callbackData);

        try {
            // Подтверждение получения callback
            answerCallbackQuery(callbackQuery.getId());

            // Обработка callback данных
            if (callbackData.startsWith("setting_")) {
                handleSettingCallback(chatId, userId, callbackData, messageId);
            } else if (callbackData.startsWith("page_")) {
                handlePageCallback(chatId, userId, callbackData, messageId);
            } else if (callbackData.equals("save_settings")) {
                handleSaveSettings(chatId, userId, messageId);
            } else if (callbackData.equals("cancel")) {
                handleCancel(chatId, userId, messageId);
            }

        } catch (Exception e) {
            logger.error("Error handling callback: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка обработки запроса");
        }
    }

    /**
     * Команда /start
     */
    private void handleStart(Long chatId, long userId) {
        logger.info("Processing /start for user {} (chat {})", userId, chatId);

        // Проверяем текущий статус пользователя
        boolean isInWhitelistBefore = WhitelistManager.isUserAllowed(userId);
        logger.info("User {} in whitelist before /start: {}", userId, isInWhitelistBefore);

        boolean isNewUser = WhitelistManager.addUser(userId);

        // Получаем обновленный статус
        boolean isInWhitelistAfter = WhitelistManager.isUserAllowed(userId);
        logger.info("User {} in whitelist after /start: {} (isNewUser: {})",
                userId, isInWhitelistAfter, isNewUser);

        String welcomeMessage;
        if (isNewUser) {
            welcomeMessage = String.format("""
                🎉 Добро пожаловать в Парсер товаров с динамическими куками!
                
                🆔 **Ваш ID:** `%d`
                
                🆕 **Новые возможности:**
                • Автоматическое обновление кук через Selenium
                • Динамическое получение свежих кук с сайта Goofish
                • Автоматическое восстановление при ошибках авторизации
                
                📋 **Основные команды:**
                /addquery [текст] - добавить поисковый запрос
                /listqueries - список ваших запросов
                /settings - настройки парсера
                /start_parser - запустить парсер
                /status - статус работы
                /help - подробная справка
                
                👑 **Админские команды:**
                /cookies - управление динамическими куками
                
                ⚡ **Быстрый старт:**
                1. Добавьте запросы командой /addquery
                2. Настройте параметры в /settings
                3. Запустите парсер /start_parser
                4. Получайте уведомления о новых товарах!
                
                Удачи в поисках выгодных предложений! 🛍️
                """, userId);
        } else {
            welcomeMessage = String.format("""
                👋 С возвращением!
                
                🆔 **Ваш ID:** `%d`
                ✅ **В whitelist:** ДА
                
                Используйте /help для списка команд.
                """, userId);
        }

        sendMessage(chatId, welcomeMessage);

        // Дополнительная отладочная информация для новых пользователей
        if (isNewUser) {
            String debugInfo = String.format(
                    "\n\n📊 **Отладочная информация:**\n" +
                            "Ваш ID: %d\n" +
                            "Добавлен в whitelist: ✅ ДА\n" +
                            "Всего пользователей: %d",
                    userId, WhitelistManager.getUserCount()
            );
            sendMessage(chatId, debugInfo);
        }
    }

    /**
     * Команда /getid - для получения ID
     */
    private void handleGetIdCommand(Long chatId, long userId) {
        String message = String.format("""
            🆔 **Ваши идентификаторы:**
            
            • **Chat ID:** `%d`
            • **User ID для системы:** `%d`
            
            **Важно:** Используйте второй ID (`%d`) для регистрации.
            
            **Если ID отрицательный:** Это нормально для Telegram.
            Система автоматически преобразует его.
            """, chatId, userId, userId);

        sendMessage(chatId, message);
    }

    /**
     * Команда /checkwhitelist - для отладки
     */
    private void handleCheckWhitelist(Long chatId, long userId) {
        boolean isInWhitelist = WhitelistManager.isUserAllowed(userId);
        List<Long> allUsers = WhitelistManager.getAllUsers();

        String message = String.format(
                "📋 **Whitelist Status**\n\n" +
                        "Ваш ID: `%d`\n" +
                        "В whitelist: %s\n\n" +
                        "Все пользователи в whitelist (%d):\n%s\n\n" +
                        "**Отладочная информация:**\n" +
                        "Путь к файлу: %s\n" +
                        "Admin ID: %d\n" +
                        "Ваш Chat ID: %d",
                userId,
                isInWhitelist ? "✅ YES" : "❌ NO",
                allUsers.size(),
                allUsers.isEmpty() ? "Пользователей пока нет" :
                        allUsers.stream()
                                .map(id -> "• " + id + (id == userId ? " (вы)" : ""))
                                .collect(java.util.stream.Collectors.joining("\n")),
                com.parser.storage.FileStorage.getFilePath("whitelist.txt"),
                adminId,
                chatId
        );

        sendMessage(chatId, message);
    }

    /**
     * Команда /debug - дополнительная отладка
     */
    private void handleDebug(Long chatId, long userId) {
        boolean isInWhitelist = WhitelistManager.isUserAllowed(userId);
        List<Long> allUsers = WhitelistManager.getAllUsers();
        boolean isParserRunning = threadManager.isUserParserRunning(userId);

        String message = String.format(
                "🔧 **Отладочная информация**\n\n" +
                        "👤 **Пользователь:**\n" +
                        "• User ID: %d\n" +
                        "• Chat ID: %d\n" +
                        "• В whitelist: %s\n\n" +
                        "🤖 **Бот:**\n" +
                        "• Admin ID: %d\n" +
                        "• Вы админ: %s\n\n" +
                        "🔄 **Парсер:**\n" +
                        "• Парсер запущен: %s\n" +
                        "• Активных пользователей: %d\n\n" +
                        "📋 **Whitelist:**\n" +
                        "• Всего пользователей: %d\n" +
                        "• Пользователи: %s",
                userId,
                chatId,
                isInWhitelist ? "✅ ДА" : "❌ НЕТ",
                adminId,
                (userId == adminId) ? "✅ ДА" : "❌ НЕТ",
                isParserRunning ? "✅ ДА" : "❌ НЕТ",
                threadManager.getActiveUsers().size(),
                allUsers.size(),
                allUsers.isEmpty() ? "Нет пользователей" :
                        allUsers.stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(", "))
        );

        sendMessage(chatId, message);
    }

    /**
     * Команда /help
     */
    private void sendHelpMessage(Long chatId) {
        String helpMessage = """
            📚 **Справка по командам**
            
            🔑 **Получение ID:**
            /getid - показать ваш Telegram ID
            /checkwhitelist - проверить статус в белом списке
            
            🎯 **Управление запросами:**
            /addquery [текст] - добавить поисковый запрос
            /listqueries - показать все запросы
            /removequery [номер] - удалить запрос
            /clear queries - очистить все запросы
            
            ⚙️ **Настройки парсера:**
            /settings - меню настроек
            /stats - статистика работы
            
            ▶️ **Управление парсером:**
            /start_parser - запустить парсер
            /stop_parser - остановить парсер
            /pause_parser - приостановить
            /resume_parser - возобновить
            /status - статус работы
            
            🍪 **Управление куками (админ):**
            /cookies - меню управления динамическими куками
            
            🛠️ **Другие команды:**
            /help - эта справка
            /clear history - очистить историю товаров
            /debug - отладочная информация
            
            🔄 **Новые возможности:**
            • Автоматическое обновление кук через Selenium
            • Динамические куки с сайта Goofish
            • Самовосстановление при ошибках
            
            💡 **Совет:** Используйте точные запросы для лучших результатов.
            """;

        sendMessage(chatId, helpMessage);
    }

    /**
     * Команда /status
     */
    private void handleStatus(Long chatId, long userId) {
        Map<String, Object> status = threadManager.getUserStatus(userId);

        if (status == null) {
            String message = """
                📊 **Статус парсера**
                
                🔴 Парсер не запущен
                
                Чтобы начать работу:
                1. Добавьте запросы: /addquery [текст]
                2. Настройте параметры: /settings
                3. Запустите парсер: /start_parser
                """;
            sendMessage(chatId, message);
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("📊 **Статус парсера**\n\n");

        boolean isRunning = (Boolean) status.get("running");
        boolean isPaused = (Boolean) status.get("paused");

        if (isRunning && !isPaused) {
            message.append("🟢 **Запущен**\n");
        } else if (isPaused) {
            message.append("⏸ **Приостановлен**\n");
        } else {
            message.append("🔴 **Остановлен**\n");
        }

        message.append("\n📈 **Статистика:**\n");
        message.append("Найдено товаров: ").append(status.get("totalProductsFound")).append("\n");
        message.append("Выполнено запросов: ").append(status.get("requestsMade")).append("\n");
        message.append("Ошибок: ").append(status.get("errorsCount")).append("\n");

        if (status.get("uptime") != null) {
            message.append("Время работы: ").append(status.get("uptime")).append("\n");
        }

        message.append("Активных запросов: ").append(status.get("queriesCount")).append("\n");

        // Кнопки управления
        InlineKeyboardMarkup keyboard = createStatusKeyboard(isRunning, isPaused);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(message.toString());
        sendMessage.setReplyMarkup(keyboard);
        sendMessage.enableMarkdown(true);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.error("Error sending status message: {}", e.getMessage());
        }
    }

    /**
     * Создание клавиатуры для статуса
     */
    private InlineKeyboardMarkup createStatusKeyboard(boolean isRunning, boolean isPaused) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (isRunning && !isPaused) {
            // Парсер запущен
            rows.add(List.of(
                    createButton("⏸ Приостановить", "pause_parser"),
                    createButton("🛑 Остановить", "stop_parser")
            ));
        } else if (isPaused) {
            // Парсер приостановлен
            rows.add(List.of(
                    createButton("▶️ Возобновить", "resume_parser"),
                    createButton("🛑 Остановить", "stop_parser")
            ));
        } else {
            // Парсер остановлен
            rows.add(List.of(
                    createButton("▶️ Запустить", "start_parser")
            ));
        }

        rows.add(List.of(
                createButton("⚙️ Настройки", "settings"),
                createButton("📋 Запросы", "listqueries")
        ));

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Команда /addquery
     */
    private void handleAddQuery(Long chatId, long userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            // Запрашиваем запрос у пользователя
            stateManager.setUserState((int) userId, "AWAITING_QUERY");
            sendMessage(chatId, "Введите поисковый запрос:");
            return;
        }

        if (UserDataManager.addUserQuery(userId, query.trim())) {
            sendMessage(chatId, "✅ Запрос добавлен: " + query);
        } else {
            sendMessage(chatId, "⚠️ Этот запрос уже существует");
        }
    }

    /**
     * Команда /listqueries
     */
    private void handleListQueries(Long chatId, long userId) {
        List<String> queries = UserDataManager.getUserQueries(userId);

        if (queries.isEmpty()) {
            sendMessage(chatId, "📭 У вас нет поисковых запросов.\n" +
                    "Добавьте запрос командой /addquery [текст]");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("📋 **Ваши поисковые запросы:**\n\n");

        for (int i = 0; i < queries.size(); i++) {
            message.append(i + 1).append(". ").append(queries.get(i)).append("\n");
        }

        message.append("\n**Управление:**\n");
        message.append("/removequery [номер] - удалить запрос\n");
        message.append("/addquery [текст] - добавить новый\n");
        message.append("/clear queries - очистить все");

        sendMessage(chatId, message.toString());
    }

    /**
     * Команда /removequery
     */
    private void handleRemoveQuery(Long chatId, long userId, String arg) {
        try {
            if (arg == null || arg.trim().isEmpty()) {
                sendMessage(chatId, "Используйте: /removequery [номер]\n" +
                        "Номер можно посмотреть в /listqueries");
                return;
            }

            int index = Integer.parseInt(arg.trim()) - 1;
            List<String> queries = UserDataManager.getUserQueries(userId);

            if (index < 0 || index >= queries.size()) {
                sendMessage(chatId, "❌ Неверный номер запроса");
                return;
            }

            String removedQuery = queries.get(index);
            UserDataManager.removeUserQuery(userId, removedQuery);

            sendMessage(chatId, "✅ Запрос удален: " + removedQuery);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат номера");
        }
    }

    /**
     * Меню настроек
     */
    private void showSettingsMenu(Long chatId, long userId) {
        UserSettings settings = UserDataManager.getUserSettings(userId);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Основные настройки
        rows.add(List.of(
                createButton("⏱️ Интервал: " + settings.getCheckInterval() + " сек",
                        "setting_check_interval")
        ));

        rows.add(List.of(
                createButton("📅 Возраст: " + settings.getMaxAgeMinutes() + " мин",
                        "setting_max_age")
        ));

        rows.add(List.of(
                createButton("📄 Страниц: " + settings.getMaxPages(),
                        "setting_max_pages")
        ));

        rows.add(List.of(
                createButton("🛒 Товаров на стр: " + settings.getRowsPerPage(),
                        "setting_rows_per_page")
        ));

        // Валюта
        String currencyText = settings.getPriceCurrency().equals("rubles") ? "🇷🇺 Рубли" : "¥ Юани";
        rows.add(List.of(
                createButton("💰 Валюта: " + currencyText, "setting_price_currency")
        ));

        // Уведомления
        String notifyText = settings.isNotifyNewOnly() ? "Только новые" : "Все";
        rows.add(List.of(
                createButton("🔔 Уведомления: " + notifyText, "setting_notify_new_only")
        ));

        // Дополнительные настройки
        rows.add(List.of(
                createButton("⚙️ Дополнительно", "setting_advanced")
        ));

        // Управление
        rows.add(List.of(
                createButton("💾 Сохранить", "save_settings"),
                createButton("❌ Отмена", "cancel")
        ));

        keyboard.setKeyboard(rows);

        String messageText = """
            ⚙️ **Настройки парсера**
            
            Текущие параметры:
            • Интервал проверки: %d сек
            • Макс. возраст товара: %d мин
            • Страниц для парсинга: %d
            • Товаров на странице: %d
            • Валюта отображения: %s
            • Уведомления: %s
            
            Выберите параметр для изменения:
            """.formatted(
                settings.getCheckInterval(),
                settings.getMaxAgeMinutes(),
                settings.getMaxPages(),
                settings.getRowsPerPage(),
                currencyText,
                notifyText
        );

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error showing settings menu: {}", e.getMessage());
        }
    }

    /**
     * Обработка callback настроек
     */
    private void handleSettingCallback(Long chatId, long userId, String callbackData, Integer messageId) {
        String setting = callbackData.substring(8); // Убираем "setting_"

        switch (setting) {
            case "check_interval":
                requestSettingValue(chatId, userId, "Интервал проверки (сек)",
                        "10-3600", "check_interval");
                break;

            case "max_age":
                requestSettingValue(chatId, userId, "Максимальный возраст товара (мин)",
                        "1-10080", "max_age_minutes");
                break;

            case "max_pages":
                requestSettingValue(chatId, userId, "Количество страниц",
                        "1-50", "max_pages");
                break;

            case "rows_per_page":
                requestSettingValue(chatId, userId, "Товаров на странице",
                        "10-1000", "rows_per_page");
                break;

            case "price_currency":
                togglePriceCurrency(chatId, userId, messageId);
                break;

            case "notify_new_only":
                toggleNotifyNewOnly(chatId, userId, messageId);
                break;

            case "advanced":
                showAdvancedSettings(chatId, userId);
                break;
        }
    }

    /**
     * Запрос значения настройки у пользователя
     */
    private void requestSettingValue(Long chatId, long userId, String settingName,
                                     String range, String settingKey) {
        stateManager.setUserState((int) userId, "AWAITING_SETTING_VALUE");
        stateManager.setUserData((int) userId, "setting_key", settingKey);

        String message = String.format("""
            ✏️ **%s**
            
            Введите новое значение:
            Допустимый диапазон: %s
            
            Например: 300
            """, settingName, range);

        sendMessage(chatId, message);
    }

    /**
     * Обработка введенного значения настройки
     */
    private void handleSettingValue(Long chatId, long userId, String value) {
        String settingKey = stateManager.getUserData((int) userId, "setting_key");

        if (settingKey == null) {
            sendMessage(chatId, "❌ Ошибка: не указана настройка");
            stateManager.clearUserState((int) userId);
            return;
        }

        try {
            int intValue = Integer.parseInt(value.trim());
            UserSettings settings = UserDataManager.getUserSettings(userId);

            switch (settingKey) {
                case "check_interval":
                    settings.setCheckInterval(intValue);
                    break;
                case "max_age_minutes":
                    settings.setMaxAgeMinutes(intValue);
                    break;
                case "max_pages":
                    settings.setMaxPages(intValue);
                    break;
                case "rows_per_page":
                    settings.setRowsPerPage(intValue);
                    break;
            }

            UserDataManager.saveUserSettings(userId, settings);
            sendMessage(chatId, "✅ Настройка сохранена");

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат числа");
        } finally {
            stateManager.clearUserState((int) userId);
        }
    }

    /**
     * Переключение валюты
     */
    private void togglePriceCurrency(Long chatId, long userId, Integer messageId) {
        UserSettings settings = UserDataManager.getUserSettings(userId);

        if ("rubles".equals(settings.getPriceCurrency())) {
            settings.setPriceCurrency("yuan");
        } else {
            settings.setPriceCurrency("rubles");
        }

        UserDataManager.saveUserSettings(userId, settings);

        // Обновляем сообщение с настройками
        showSettingsMenu(chatId, userId);

        // Удаляем старое сообщение
        if (messageId != null) {
            deleteMessage(chatId, messageId);
        }
    }

    /**
     * Переключение режима уведомлений
     */
    private void toggleNotifyNewOnly(Long chatId, long userId, Integer messageId) {
        UserSettings settings = UserDataManager.getUserSettings(userId);
        settings.setNotifyNewOnly(!settings.isNotifyNewOnly());
        UserDataManager.saveUserSettings(userId, settings);

        showSettingsMenu(chatId, userId);

        if (messageId != null) {
            deleteMessage(chatId, messageId);
        }
    }

    /**
     * Показать расширенные настройки
     */
    private void showAdvancedSettings(Long chatId, long userId) {
        UserSettings settings = UserDataManager.getUserSettings(userId);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Ценовые фильтры
        String minPrice = settings.getMinPrice() > 0 ? String.valueOf(settings.getMinPrice()) : "Нет";
        String maxPrice = settings.getMaxPrice() > 0 ? String.valueOf(settings.getMaxPrice()) : "Нет";

        rows.add(List.of(
                createButton("💰 Мин. цена: " + minPrice, "setting_min_price"),
                createButton("💰 Макс. цена: " + maxPrice, "setting_max_price")
        ));

        // Задержка запросов
        rows.add(List.of(
                createButton("⏱️ Задержка: " + settings.getRequestDelay() + " мс",
                        "setting_request_delay")
        ));

        // Максимальное количество повторов
        rows.add(List.of(
                createButton("🔄 Повторов: " + settings.getMaxRetries(),
                        "setting_max_retries")
        ));

        // Назад
        rows.add(List.of(
                createButton("🔙 Назад", "settings")
        ));

        keyboard.setKeyboard(rows);

        String messageText = """
            ⚙️ **Расширенные настройки**
            
            Текущие параметры:
            • Минимальная цена: %s
            • Максимальная цена: %s
            • Задержка между запросами: %d мс
            • Макс. количество повторов: %d
            
            Выберите параметр для изменения:
            """.formatted(
                minPrice,
                maxPrice,
                settings.getRequestDelay(),
                settings.getMaxRetries()
        );

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error showing advanced settings: {}", e.getMessage());
        }
    }

    /**
     * Обработка ценового фильтра
     */
    private void handlePriceFilter(Long chatId, long userId, String state, String value) {
        try {
            double price = Double.parseDouble(value.trim());
            UserSettings settings = UserDataManager.getUserSettings(userId);

            if ("AWAITING_MIN_PRICE".equals(state)) {
                settings.setMinPrice(price);
                sendMessage(chatId, "✅ Минимальная цена установлена: " + price);
            } else {
                settings.setMaxPrice(price);
                sendMessage(chatId, "✅ Максимальная цена установлена: " + price);
            }

            UserDataManager.saveUserSettings(userId, settings);
            stateManager.clearUserState((int) userId);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат цены");
        }
    }

    /**
     * Сохранение настроек
     */
    private void handleSaveSettings(Long chatId, long userId, Integer messageId) {
        UserSettings settings = UserDataManager.getUserSettings(userId);

        if (!settings.isValid()) {
            sendMessage(chatId, "❌ Некорректные настройки. Проверьте значения.");
            return;
        }

        UserDataManager.saveUserSettings(userId, settings);

        String message = """
            ✅ **Настройки сохранены!**
            
            Текущие параметры:
            • Интервал проверки: %d сек
            • Макс. возраст товара: %d мин (%d ч)
            • Страниц для парсинга: %d
            • Товаров на странице: %d
            • Валюта: %s
            
            Для запуска парсера используйте /start_parser
            """.formatted(
                settings.getCheckInterval(),
                settings.getMaxAgeMinutes(),
                settings.getMaxAgeMinutes() / 60,
                settings.getMaxPages(),
                settings.getRowsPerPage(),
                settings.getPriceCurrency().equals("rubles") ? "Рубли" : "Юани"
        );

        sendMessage(chatId, message);

        if (messageId != null) {
            deleteMessage(chatId, messageId);
        }
    }

    /**
     * Команда /start_parser
     */
    private void handleStartParser(Long chatId, long userId) {
        logger.info("User {} requested to start parser", userId);

        // Проверяем whitelist перед запуском
        if (!WhitelistManager.isUserAllowed(userId)) {
            logger.warn("User {} not in whitelist, cannot start parser", userId);
            sendMessage(chatId, "⛔ Вы не авторизованы для использования парсера.\n" +
                    "Используйте команду /start для регистрации");
            return;
        }

        if (threadManager.startUserParser(userId)) {
            sendMessage(chatId, "✅ Парсер успешно запущен!");
        } else {
            sendMessage(chatId, "❌ Не удалось запустить парсер. Проверьте логи.");
        }
    }

    /**
     * Команда /stop_parser
     */
    private void handleStopParser(Long chatId, long userId) {
        if (threadManager.stopUserParser(userId)) {
            sendMessage(chatId, "🛑 Парсер остановлен");
        } else {
            sendMessage(chatId, "ℹ️ Парсер не был запущен");
        }
    }

    /**
     * Команда /pause_parser
     */
    private void handlePauseParser(Long chatId, long userId) {
        if (threadManager.pauseUserParser(userId)) {
            sendMessage(chatId, "⏸ Парсер приостановлен");
        } else {
            sendMessage(chatId, "ℹ️ Парсер не запущен или уже приостановлен");
        }
    }

    /**
     * Команда /resume_parser
     */
    private void handleResumeParser(Long chatId, long userId) {
        if (threadManager.resumeUserParser(userId)) {
            sendMessage(chatId, "▶️ Парсер возобновлен");
        } else {
            sendMessage(chatId, "ℹ️ Парсер не был приостановлен");
        }
    }

    /**
     * Команда /stats
     */
    private void handleStats(Long chatId, long userId) {
        Map<String, Object> userStats = threadManager.getUserStatus(userId);
        Map<String, Object> globalStats = threadManager.getGlobalStatistics();

        StringBuilder message = new StringBuilder();
        message.append("📊 **Статистика**\n\n");

        if (userStats != null) {
            message.append("👤 **Ваша статистика:**\n");
            message.append("Найдено товаров: ").append(userStats.get("totalProductsFound")).append("\n");
            message.append("Выполнено запросов: ").append(userStats.get("requestsMade")).append("\n");
            message.append("Ошибок: ").append(userStats.get("errorsCount")).append("\n");

            if (userStats.get("uptime") != null) {
                message.append("Время работы: ").append(userStats.get("uptime")).append("\n");
            }
            message.append("\n");
        }

        message.append("🌐 **Общая статистика:**\n");
        message.append("Активных пользователей: ").append(globalStats.get("totalUsers")).append("\n");
        message.append("Всего найдено товаров: ").append(globalStats.get("totalProductsFound")).append("\n");
        message.append("Всего запросов: ").append(globalStats.get("totalRequestsMade")).append("\n");

        long uptime = (Long) globalStats.get("uptime");
        long hours = uptime / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        message.append("Время работы сервиса: ").append(hours).append("ч ").append(minutes).append("м\n");

        message.append("Активных потоков: ").append(globalStats.get("activeThreads")).append("\n");
        message.append("Размер пула: ").append(globalStats.get("poolSize")).append("\n");

        // Добавляем статистику кук
        Map<String, Object> cookieStats = CookieService.getCacheStats();
        message.append("\n🍪 **Статистика кук:**\n");
        message.append("Динамические куки: ").append(Config.isDynamicCookiesEnabled() ? "Включено" : "Выключено").append("\n");
        message.append("Последнее обновление: ").append(cookieStats.get("lastRefreshTime")).append("\n");

        sendMessage(chatId, message.toString());
    }

    /**
     * Команда /clear
     */
    private void handleClear(Long chatId, long userId, String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            sendMessage(chatId, """
                🗑️ **Очистка данных**
                
                Доступные опции:
                /clear queries - очистить все поисковые запросы
                /clear history - очистить историю найденных товаров
                /clear settings - сбросить настройки к значениям по умолчанию
                """);
            return;
        }

        String option = arg.trim().toLowerCase();

        switch (option) {
            case "queries":
                UserDataManager.clearUserQueries(userId);
                sendMessage(chatId, "✅ Все поисковые запросы очищены");
                break;

            case "history":
                UserDataManager.clearUserProducts(userId);
                sendMessage(chatId, "✅ История товаров очищена");
                break;

            case "settings":
                UserSettings defaultSettings = new UserSettings();
                UserDataManager.saveUserSettings(userId, defaultSettings);
                sendMessage(chatId, "✅ Настройки сброшены к значениям по умолчанию");
                break;

            default:
                sendMessage(chatId, "❌ Неверная опция очистки");
        }
    }

    /**
     * Админские команды
     */
    private void handleAdmin(Long chatId, long userId, String arg) {
        if (userId != adminId) {
            sendMessage(chatId, "⛔ У вас нет прав администратора");
            return;
        }

        if (arg == null || arg.trim().isEmpty()) {
            showAdminMenu(chatId);
            return;
        }

        String[] parts = arg.split(" ", 2);
        String command = parts[0].toLowerCase();
        String param = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "stats":
                showAdminStats(chatId);
                break;

            case "users":
                showAdminUsers(chatId);
                break;

            case "adduser":
                handleAdminAddUser(chatId, param);
                break;

            case "removeuser":
                handleAdminRemoveUser(chatId, param);
                break;

            case "broadcast":
                handleAdminBroadcast(chatId, param);
                break;

            case "restart":
                handleAdminRestart(chatId);
                break;

            default:
                sendMessage(chatId, "❌ Неизвестная админская команда");
        }
    }

    /**
     * Меню администратора
     */
    private void showAdminMenu(Long chatId) {
        String menu = """
            👑 **Панель администратора**
            
            Доступные команды:
            /admin stats - подробная статистика
            /admin users - список пользователей
            /admin adduser [id] - добавить пользователя
            /admin removeuser [id] - удалить пользователя
            /admin broadcast [текст] - рассылка всем
            /admin restart - перезапуск парсеров
            
            Пользователей в системе: %d
            """.formatted(WhitelistManager.getAllUsers().size());

        sendMessage(chatId, menu);
    }

    /**
     * Команда /cookies (только для админа)
     */
    private void handleCookiesCommand(Long chatId, long userId, String arg) {
        if (userId != adminId) {
            sendMessage(chatId, "⛔ У вас нет прав администратора");
            return;
        }

        if (arg == null || arg.trim().isEmpty()) {
            showCookiesMenu(chatId);
            return;
        }

        String[] parts = arg.split(" ", 2);
        String command = parts[0].toLowerCase();
        String param = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "refresh":
                refreshCookies(chatId, param);
                break;
            case "refresh_gui":
                refreshCookiesWithGUI(chatId, param);
                break;
            case "clear":
                clearCookies(chatId, param);
                break;
            case "status":
                showCookiesStats(chatId);
                break;
            case "dynamic":
                toggleDynamicCookies(chatId);
                break;
            default:
                sendMessage(chatId, "❌ Неизвестная команда для куки");
        }
    }

    /**
     * Меню управления куки
     */
    private void showCookiesMenu(Long chatId) {
        String menu = """
            🍪 **Управление cookies через Selenium**
            
            🆕 *Новые команды:*
            /cookies refresh - получить свежие cookies через Selenium (headless)
            /cookies refresh_gui - получить cookies с открытием браузера (для отладки)
            /cookies status - статус cookies
            
            🛠️ *Управление:*
            /cookies clear [домен] - очистить cookies
            /cookies dynamic - включить/выключить динамические cookies
            
            ⚙️ *Настройки:*
            • Автоматическое обновление: %s
            • Динамические cookies: %s
            • Интервал обновления: %d мин
            """.formatted(
                Config.getBoolean("cookie.auto.update", true) ? "Включено" : "Выключено",
                Config.isDynamicCookiesEnabled() ? "Включено" : "Выключено",
                Config.getInt("cookie.update.interval.minutes", 60)
        );

        sendMessage(chatId, menu);
    }

    /**
     * Обновление cookies
     */
    private void refreshCookies(Long chatId, String domain) {
        String targetDomain = domain.trim();
        if (targetDomain.isEmpty()) {
            targetDomain = "h5api.m.goofish.com";
        }

        sendMessage(chatId, "🔄 Получаю свежие cookies через Selenium (headless) для " + targetDomain + "...");

        try {
            boolean success = CookieService.refreshCookies(targetDomain);
            if (success) {
                sendMessage(chatId, String.format(
                        "✅ Cookies успешно обновлены через Selenium\n" +
                                "Время: %s",
                        new Date()
                ));
            } else {
                sendMessage(chatId, "❌ Не удалось получить свежие cookies");
            }
        } catch (Exception e) {
            logger.error("Error refreshing cookies: {}", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении cookies: " + e.getMessage());
        }
    }

    /**
     * Обновление cookies через GUI
     */
    private void refreshCookiesWithGUI(Long chatId, String domain) {
        String targetDomain = domain.trim();
        if (targetDomain.isEmpty()) {
            targetDomain = "h5api.m.goofish.com";
        }

        sendMessage(chatId, "🔄 Получаю свежие cookies через Selenium с GUI для " + targetDomain + "...");

        try {
            boolean success = CookieService.refreshCookiesWithGUI(targetDomain);
            if (success) {
                sendMessage(chatId, String.format(
                        "✅ Cookies успешно обновлены через GUI\n" +
                                "Время: %s",
                        new Date()
                ));
            } else {
                sendMessage(chatId, "❌ Не удалось получить свежие cookies через GUI");
            }
        } catch (Exception e) {
            logger.error("Error refreshing cookies with GUI: {}", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при обновлении cookies через GUI: " + e.getMessage());
        }
    }

    /**
     * Очистка cookies
     */
    private void clearCookies(Long chatId, String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            sendMessage(chatId, "⚠️ Укажите домен для очистки\n" +
                    "Пример: /cookies clear h5api.m.goofish.com");
            return;
        }

        CookieConfig.clearCookiesForDomain(domain.trim());
        CookieService.clearCache(); // Также очищаем кэш динамических cookies
        sendMessage(chatId, "✅ Cookies очищены для домена: " + domain);
    }

    /**
     * Статистика cookies
     */
    private void showCookiesStats(Long chatId) {
        Map<String, Object> cookieStats = CookieService.getCacheStats();

        StringBuilder message = new StringBuilder();
        message.append("📊 **Статистика cookies**\n\n");

        message.append("⚙️ *Настройки:*\n");
        message.append("Динамические cookies: ").append(Config.isDynamicCookiesEnabled() ? "🟢 Включено" : "🔴 Выключено").append("\n");
        message.append("Автообновление: ").append(Config.getBoolean("cookie.auto.update", true) ? "🟢 Включено" : "🔴 Выключено").append("\n");
        message.append("Интервал обновления: ").append(Config.getInt("cookie.update.interval.minutes", 60)).append(" мин\n");

        message.append("\n📅 *Состояние:*\n");
        message.append("Последнее обновление: ").append(cookieStats.get("lastRefreshTime")).append("\n");

        String[] domains = CookieConfig.getCookieDomains();
        message.append("\n🌐 *Домены с cookies:*\n");
        message.append("Всего доменов: ").append(domains.length).append("\n");

        for (String domain : domains) {
            String cookies = CookieConfig.getCookiesForDomain(domain);
            int cookieCount = cookies.split("; ").length;
            message.append("• ").append(domain).append(": ").append(cookieCount).append(" cookies\n");
        }

        sendMessage(chatId, message.toString());
    }

    /**
     * Переключение динамических cookies
     */
    private void toggleDynamicCookies(Long chatId) {
        boolean current = Config.isDynamicCookiesEnabled();
        Config.setProperty("cookie.dynamic.enabled", String.valueOf(!current));
        Config.saveConfig();

        if (!current) {
            sendMessage(chatId, "✅ Динамические cookies включены");
        } else {
            sendMessage(chatId, "✅ Динамические cookies выключены");
        }
    }

    // Вспомогательные методы

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.enableMarkdown(true);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to {}: {}", chatId, e.getMessage());
        }
    }

    private void deleteMessage(Long chatId, Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId.toString());
        deleteMessage.setMessageId(messageId);

        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            logger.error("Error deleting message: {}", e.getMessage());
        }
    }

    private void answerCallbackQuery(String callbackId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Error answering callback query: {}", e.getMessage());
        }
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private void handleCancel(Long chatId, long userId, Integer messageId) {
        stateManager.clearUserState((int) userId);
        sendMessage(chatId, "❌ Действие отменено");

        if (messageId != null) {
            deleteMessage(chatId, messageId);
        }
    }

    private void handlePageCallback(Long chatId, long userId, String callbackData, Integer messageId) {
        // Реализация пагинации
        // В реальном проекте здесь будет обработка переключения страниц
    }

    private void registerCommands() {
        try {
            List<BotCommand> commands = new ArrayList<>();
            commands.add(new BotCommand("start", "Запустить бота"));
            commands.add(new BotCommand("help", "Помощь и справка"));
            commands.add(new BotCommand("addquery", "Добавить поисковый запрос"));
            commands.add(new BotCommand("listqueries", "Список запросов"));
            commands.add(new BotCommand("settings", "Настройки парсера"));
            commands.add(new BotCommand("status", "Статус работы"));
            commands.add(new BotCommand("start_parser", "Запустить парсер"));
            commands.add(new BotCommand("stop_parser", "Остановить парсер"));
            commands.add(new BotCommand("stats", "Статистика"));
            commands.add(new BotCommand("cookies", "Управление куками (админ)"));
            commands.add(new BotCommand("checkwhitelist", "Проверить whitelist"));
            commands.add(new BotCommand("debug", "Отладочная информация"));
            commands.add(new BotCommand("getid", "Получить свой ID"));

            this.execute(new org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands(
                    commands, new BotCommandScopeDefault(), null
            ));

            logger.info("Bot commands registered");
        } catch (TelegramApiException e) {
            logger.error("Error registering commands: {}", e.getMessage());
        }
    }

    private void showAdminStats(Long chatId) {
        Map<String, Object> stats = threadManager.getGlobalStatistics();
        Map<String, Object> cookieStats = CookieService.getCacheStats();

        StringBuilder message = new StringBuilder();
        message.append("📊 **Админская статистика**\n\n");

        message.append("👥 **Пользователи:**\n");
        message.append("Всего пользователей: ").append(WhitelistManager.getUserCount()).append("\n");
        message.append("Активных сессий: ").append(stats.get("totalUsers")).append("\n");

        message.append("\n⚙️ **Система:**\n");
        message.append("Всего товаров найдено: ").append(stats.get("totalProductsFound")).append("\n");
        message.append("Всего запросов: ").append(stats.get("totalRequestsMade")).append("\n");
        message.append("Активных потоков: ").append(stats.get("activeThreads")).append("/").append(stats.get("poolSize")).append("\n");

        long uptime = (Long) stats.get("uptime");
        long days = uptime / (1000 * 60 * 60 * 24);
        long hours = (uptime % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        message.append("Время работы: ").append(days).append("д ").append(hours).append("ч ").append(minutes).append("м\n");

        message.append("\n🍪 **Cookies:**\n");
        message.append("Динамические cookies: ").append(Config.isDynamicCookiesEnabled() ? "Включено" : "Выключено").append("\n");
        message.append("Последнее обновление: ").append(cookieStats.get("lastRefreshTime")).append("\n");

        sendMessage(chatId, message.toString());
    }

    private void showAdminUsers(Long chatId) {
        List<Long> users = WhitelistManager.getAllUsers();

        if (users.isEmpty()) {
            sendMessage(chatId, "📭 Нет пользователей в системе");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("👥 **Список пользователей**\n\n");
        message.append("Всего пользователей: ").append(users.size()).append("\n\n");

        // Показываем первых 20 пользователей
        int count = Math.min(20, users.size());
        for (int i = 0; i < count; i++) {
            long userId = users.get(i);
            boolean isActive = threadManager.isUserParserRunning(userId);
            message.append(i + 1).append(". ID: ").append(userId);
            message.append(isActive ? " 🟢" : " 🔴").append("\n");
        }

        if (users.size() > 20) {
            message.append("\n... и еще ").append(users.size() - 20).append(" пользователей");
        }

        message.append("\n\n**Команды управления:**\n");
        message.append("/admin adduser [id] - добавить пользователя\n");
        message.append("/admin removeuser [id] - удалить пользователя");

        sendMessage(chatId, message.toString());
    }

    private void handleAdminAddUser(Long chatId, String param) {
        if (param == null || param.trim().isEmpty()) {
            sendMessage(chatId, "⚠️ Используйте: /admin adduser [id]\nПример: /admin adduser 123456789");
            return;
        }

        try {
            long userId = Long.parseLong(param.trim());
            if (WhitelistManager.addUser(userId)) {
                sendMessage(chatId, "✅ Пользователь " + userId + " добавлен в белый список");
            } else {
                sendMessage(chatId, "ℹ️ Пользователь " + userId + " уже в белом списке");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID пользователя");
        }
    }

    private void handleAdminRemoveUser(Long chatId, String param) {
        if (param == null || param.trim().isEmpty()) {
            sendMessage(chatId, "⚠️ Используйте: /admin removeuser [id]\nПример: /admin removeuser 123456789");
            return;
        }

        try {
            long userId = Long.parseLong(param.trim());
            if (WhitelistManager.removeUser(userId)) {
                sendMessage(chatId, "✅ Пользователь " + userId + " удален из белого списка");

                // Останавливаем парсер пользователя, если он запущен
                threadManager.stopUserParser(userId);
            } else {
                sendMessage(chatId, "ℹ️ Пользователь " + userId + " не найден в белом списке");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID пользователя");
        }
    }

    private void handleAdminBroadcast(Long chatId, String param) {
        if (param == null || param.trim().isEmpty()) {
            sendMessage(chatId, "⚠️ Используйте: /admin broadcast [текст]\nПример: /admin broadcast Обновление системы");
            return;
        }

        String message = param.trim();
        List<Long> users = WhitelistManager.getAllUsers();
        int sent = 0;
        int failed = 0;

        sendMessage(chatId, "📢 Начинаю рассылку для " + users.size() + " пользователей...");

        for (long userId : users) {
            try {
                TelegramNotificationService.sendMessage(userId,
                        "📢 **Административное сообщение**\n\n" + message + "\n\n_Это автоматическое сообщение от администратора_");
                sent++;
                Thread.sleep(100); // Небольшая задержка чтобы не спамить
            } catch (Exception e) {
                logger.error("Failed to send broadcast to user {}: {}", userId, e.getMessage());
                failed++;
            }
        }

        sendMessage(chatId, String.format(
                "✅ Рассылка завершена\n" +
                        "Отправлено: %d\n" +
                        "Не отправлено: %d",
                sent, failed
        ));
    }

    private void handleAdminRestart(Long chatId) {
        sendMessage(chatId, "🔄 Перезапускаю все парсеры...");

        try {
            // Получаем список активных пользователей
            List<Long> activeUsers = threadManager.getActiveUsers();
            int stopped = 0;
            int started = 0;

            // Останавливаем все парсеры
            for (long userId : activeUsers) {
                if (threadManager.stopUserParser(userId)) {
                    stopped++;
                }
            }

            // Небольшая пауза
            Thread.sleep(2000);

            // Запускаем парсеры для пользователей с запросами
            for (long userId : WhitelistManager.getAllUsers()) {
                if (!UserDataManager.getUserQueries(userId).isEmpty()) {
                    if (threadManager.startUserParser(userId)) {
                        started++;
                    }
                }
            }

            sendMessage(chatId, String.format(
                    "✅ Перезапуск завершен\n" +
                            "Остановлено парсеров: %d\n" +
                            "Запущено парсеров: %d",
                    stopped, started
            ));

        } catch (Exception e) {
            logger.error("Error restarting parsers: {}", e.getMessage());
            sendMessage(chatId, "❌ Ошибка при перезапуске: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return Config.getString("telegram.bot.username", "");
    }
}