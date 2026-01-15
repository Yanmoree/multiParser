package com.parser.telegram;

import com.parser.config.Config;
import com.parser.core.ThreadManager;
import com.parser.model.UserSettings;
import com.parser.storage.WhitelistManager;
import com.parser.storage.UserDataManager;
import com.parser.util.JsonUtils;
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
        int userId = chatId.intValue();

        logger.info("Message from {}: {}", chatId, text);

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
    private void handleCommand(Long chatId, int userId, String command, Integer messageId) {
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
    private void handleTextResponse(Long chatId, int userId, String text, Integer messageId) {
        // Проверка состояния пользователя
        String state = stateManager.getUserState(userId);

        if (state == null) {
            sendMessage(chatId, "Для работы с ботом используйте команды. /help - список команд");
            return;
        }

        switch (state) {
            case "AWAITING_QUERY":
                // Добавление нового запроса
                if (UserDataManager.addUserQuery(userId, text)) {
                    sendMessage(chatId, "✅ Запрос добавлен: " + text);
                    stateManager.clearUserState(userId);
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
        int userId = chatId.intValue();
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
// В методе handleStart (строка ~244)
    private void handleStart(Long chatId, int userId) {
        if (WhitelistManager.addUser(userId)) {
            String welcomeMessage = "🎉 Добро пожаловать в Парсер товаров!\n\n" +
                    "Я помогу вам отслеживать товары на маркетплейсах и уведомлять о новых предложениях.\n\n" +
                    "📋 **Основные команды:**\n" +
                    "/addquery [текст] - добавить поисковый запрос\n" +
                    "/listqueries - список ваших запросов\n" +
                    "/settings - настройки парсера\n" +
                    "/start_parser - запустить парсер\n" +
                    "/status - статус работы\n" +
                    "/help - подробная справка\n\n" +
                    "⚙️ **Сначала настройте парсер:**\n" +
                    "1. Добавьте поисковые запросы\n" +
                    "2. Настройте параметры в /settings\n" +
                    "3. Запустите парсер\n\n" +
                    "Удачи в поисках выгодных предложений! 🛍️";

            sendMessage(chatId, welcomeMessage);
        } else {
            sendMessage(chatId, "👋 С возвращением! Используйте /help для списка команд.");
        }
    }

    /**
     * Команда /help
     */
    private void sendHelpMessage(Long chatId) {
        String helpMessage = """
            📚 **Справка по командам**

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

            🛠️ **Другие команды:**
            /help - эта справка
            /clear history - очистить историю товаров

            ⚡ **Быстрый старт:**
            1. Добавьте запросы
            2. Настройте параметры
            3. Запустите парсер
            4. Получайте уведомления!

            💡 **Совет:** Используйте точные запросы для лучших результатов.
            """;

        sendMessage(chatId, helpMessage);
    }

    /**
     * Команда /status
     */
    private void handleStatus(Long chatId, int userId) {
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
    private void handleAddQuery(Long chatId, int userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            // Запрашиваем запрос у пользователя
            stateManager.setUserState(userId, "AWAITING_QUERY");
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
    private void handleListQueries(Long chatId, int userId) {
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
    private void handleRemoveQuery(Long chatId, int userId, String arg) {
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
    private void showSettingsMenu(Long chatId, int userId) {
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
    private void handleSettingCallback(Long chatId, int userId, String callbackData, Integer messageId) {
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
    private void requestSettingValue(Long chatId, int userId, String settingName,
                                     String range, String settingKey) {
        stateManager.setUserState(userId, "AWAITING_SETTING_VALUE");
        stateManager.setUserData(userId, "setting_key", settingKey);

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
    private void handleSettingValue(Long chatId, int userId, String value) {
        String settingKey = stateManager.getUserData(userId, "setting_key");

        if (settingKey == null) {
            sendMessage(chatId, "❌ Ошибка: не указана настройка");
            stateManager.clearUserState(userId);
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
            stateManager.clearUserState(userId);
        }
    }

    /**
     * Переключение валюты
     */
    private void togglePriceCurrency(Long chatId, int userId, Integer messageId) {
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
    private void toggleNotifyNewOnly(Long chatId, int userId, Integer messageId) {
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
    private void showAdvancedSettings(Long chatId, int userId) {
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
    private void handlePriceFilter(Long chatId, int userId, String state, String value) {
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
            stateManager.clearUserState(userId);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат цены");
        }
    }

    /**
     * Сохранение настроек
     */
    private void handleSaveSettings(Long chatId, int userId, Integer messageId) {
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
    private void handleStartParser(Long chatId, int userId) {
        if (threadManager.startUserParser(userId)) {
            sendMessage(chatId, "✅ Парсер успешно запущен!");
        }
    }

    /**
     * Команда /stop_parser
     */
    private void handleStopParser(Long chatId, int userId) {
        if (threadManager.stopUserParser(userId)) {
            sendMessage(chatId, "🛑 Парсер остановлен");
        }
    }

    /**
     * Команда /pause_parser
     */
    private void handlePauseParser(Long chatId, int userId) {
        if (threadManager.pauseUserParser(userId)) {
            sendMessage(chatId, "⏸ Парсер приостановлен");
        } else {
            sendMessage(chatId, "ℹ️ Парсер не запущен или уже приостановлен");
        }
    }

    /**
     * Команда /resume_parser
     */
    private void handleResumeParser(Long chatId, int userId) {
        if (threadManager.resumeUserParser(userId)) {
            sendMessage(chatId, "▶️ Парсер возобновлен");
        } else {
            sendMessage(chatId, "ℹ️ Парсер не был приостановлен");
        }
    }

    /**
     * Команда /stats
     */
    private void handleStats(Long chatId, int userId) {
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

        sendMessage(chatId, message.toString());
    }

    /**
     * Команда /clear
     */
    private void handleClear(Long chatId, int userId, String arg) {
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
    private void handleAdmin(Long chatId, int userId, String arg) {
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
     * Вспомогательные методы
     */

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

    private void handleCancel(Long chatId, int userId, Integer messageId) {
        stateManager.clearUserState(userId);
        sendMessage(chatId, "❌ Действие отменено");

        if (messageId != null) {
            deleteMessage(chatId, messageId);
        }
    }

    private void handlePageCallback(Long chatId, int userId, String callbackData, Integer messageId) {
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

            this.execute(new org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands(
                    commands, new BotCommandScopeDefault(), null
            ));

            logger.info("Bot commands registered");
        } catch (TelegramApiException e) {
            logger.error("Error registering commands: {}", e.getMessage());
        }
    }

    private void showAdminStats(Long chatId) {
        // Реализация отображения административной статистики
        // В реальном проекте здесь будет детальная статистика
        sendMessage(chatId, "📊 Админская статистика (заглушка)");
    }

    private void showAdminUsers(Long chatId) {
        // Реализация отображения списка пользователей
        // В реальном проекте здесь будет список всех пользователей
        sendMessage(chatId, "👥 Список пользователей (заглушка)");
    }

    private void handleAdminAddUser(Long chatId, String param) {
        // Реализация добавления пользователя администратором
        sendMessage(chatId, "✅ Пользователь добавлен (заглушка)");
    }

    private void handleAdminRemoveUser(Long chatId, String param) {
        // Реализация удаления пользователя администратором
        sendMessage(chatId, "✅ Пользователь удален (заглушка)");
    }

    private void handleAdminBroadcast(Long chatId, String param) {
        // Реализация рассылки сообщений всем пользователям
        sendMessage(chatId, "📢 Рассылка отправлена (заглушка)");
    }

    private void handleAdminRestart(Long chatId) {
        // Реализация перезапуска парсеров
        sendMessage(chatId, "🔄 Парсеры перезапущены (заглушка)");
    }

    @Override
    public String getBotUsername() {
        return Config.getString("telegram.bot.username", "");
    }
}