package com.parser.storage;

import com.parser.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;

/**
 * Утилита для работы с файловым хранилищем
 */
public class FileStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileStorage.class);

    // Блокировки для предотвращения конкурентного доступа
    private static final Map<String, ReentrantLock> fileLocks = new HashMap<>();

    /**
     * Получение блокировки для файла
     */
    private static synchronized ReentrantLock getFileLock(String filename) {
        return fileLocks.computeIfAbsent(filename, k -> new ReentrantLock());
    }

    /**
     * Создание директории для данных, если она не существует
     */
    public static void ensureDataDir() {
        String dataDir = Config.getString("storage.data.dir", "./data");
        File dir = new File(dataDir);

        if (!dir.exists()) {
            if (dir.mkdirs()) {
                logger.info("Created data directory: {}", dataDir);

                // Создание поддиректорий
                new File(dataDir + "/user_settings").mkdirs();
                new File(dataDir + "/user_products").mkdirs();
                new File(dataDir + "/backups").mkdirs();
                new File(dataDir + "/logs").mkdirs();

            } else {
                logger.error("Failed to create data directory: {}", dataDir);
                throw new RuntimeException("Failed to create data directory: " + dataDir);
            }
        }
    }

    /**
     * Получение пути к файлу
     */
    public static String getFilePath(String filename) {
        String dataDir = Config.getString("storage.data.dir", "./data");
        return dataDir + "/" + filename;
    }

    /**
     * Чтение строк из файла
     */
    public static List<String> readLines(String filename) {
        ReentrantLock lock = getFileLock(filename);
        lock.lock();

        try {
            File file = new File(getFilePath(filename));
            if (!file.exists()) {
                return new ArrayList<>();
            }

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        lines.add(line);
                    }
                }

                logger.debug("Read {} lines from {}", lines.size(), filename);
                return lines;

            } catch (IOException e) {
                logger.error("Error reading file {}: {}", filename, e.getMessage());
                return new ArrayList<>();
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Запись строк в файл
     */
    public static void writeLines(String filename, List<String> lines) {
        ReentrantLock lock = getFileLock(filename);
        lock.lock();

        try {
            ensureDataDir();
            File file = new File(getFilePath(filename));

            // Создание резервной копии, если файл существует
            if (file.exists()) {
                createBackup(filename);
            }

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }

                logger.debug("Wrote {} lines to {}", lines.size(), filename);

            } catch (IOException e) {
                logger.error("Error writing file {}: {}", filename, e.getMessage());
                throw new RuntimeException("Failed to write file: " + filename, e);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Добавление строки в конец файла
     */
    public static void appendLine(String filename, String line) {
        ReentrantLock lock = getFileLock(filename);
        lock.lock();

        try {
            ensureDataDir();
            File file = new File(getFilePath(filename));

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {

                writer.write(line);
                writer.newLine();

                logger.debug("Appended line to {}", filename);

            } catch (IOException e) {
                logger.error("Error appending to file {}: {}", filename, e.getMessage());
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Чтение JSON объекта из файла
     */
    public static String readJson(String filename) {
        ReentrantLock lock = getFileLock(filename);
        lock.lock();

        try {
            File file = new File(getFilePath(filename));
            if (!file.exists()) {
                return "{}";
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }

                return content.toString();

            } catch (IOException e) {
                logger.error("Error reading JSON file {}: {}", filename, e.getMessage());
                return "{}";
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Запись JSON объекта в файл
     */
    public static void writeJson(String filename, String json) {
        writeLines(filename, List.of(json));
    }

    /**
     * Проверка существования файла
     */
    public static boolean fileExists(String filename) {
        File file = new File(getFilePath(filename));
        return file.exists();
    }

    /**
     * Удаление файла
     */
    public static boolean deleteFile(String filename) {
        ReentrantLock lock = getFileLock(filename);
        lock.lock();

        try {
            File file = new File(getFilePath(filename));
            boolean deleted = file.delete();

            if (deleted) {
                logger.debug("Deleted file: {}", filename);
            } else {
                logger.warn("Failed to delete file: {}", filename);
            }

            return deleted;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Копирование файла
     */
    public static boolean copyFile(String sourceFilename, String destFilename) {
        ReentrantLock sourceLock = getFileLock(sourceFilename);
        ReentrantLock destLock = getFileLock(destFilename);

        sourceLock.lock();
        destLock.lock();

        try {
            Path source = Paths.get(getFilePath(sourceFilename));
            Path destination = Paths.get(getFilePath(destFilename));

            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Copied {} to {}", sourceFilename, destFilename);
            return true;

        } catch (IOException e) {
            logger.error("Error copying file {} to {}: {}",
                    sourceFilename, destFilename, e.getMessage());
            return false;
        } finally {
            destLock.unlock();
            sourceLock.unlock();
        }
    }

    /**
     * Создание резервной копии файла
     */
    public static void createBackup(String filename) {
        if (!Config.getBoolean("storage.backup.enabled", true)) {
            return;
        }

        try {
            String backupDir = getFilePath("backups");
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new java.util.Date());

            String backupFilename = String.format("%s/%s_%s.backup",
                    backupDir, filename.replace("/", "_"), timestamp);

            copyFile(filename, backupFilename);

            // Сжатие резервной копии
            compressFile(backupFilename);

            // Очистка старых резервных копий
            cleanupOldBackups(filename);

        } catch (Exception e) {
            logger.error("Error creating backup for {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Сжатие файла с использованием GZIP
     */
    private static void compressFile(String filename) {
        try {
            File inputFile = new File(filename);
            File outputFile = new File(filename + ".gz");

            try (FileInputStream fis = new FileInputStream(inputFile);
                 FileOutputStream fos = new FileOutputStream(outputFile);
                 GZIPOutputStream gzipOS = new GZIPOutputStream(fos)) {

                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    gzipOS.write(buffer, 0, len);
                }
            }

            // Удаление несжатого файла
            inputFile.delete();
            logger.debug("Compressed backup file: {}", outputFile.getName());

        } catch (IOException e) {
            logger.error("Error compressing file {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Очистка старых резервных копий
     */
    private static void cleanupOldBackups(String originalFilename) {
        try {
            String backupDir = getFilePath("backups");
            File dir = new File(backupDir);

            if (!dir.exists()) {
                return;
            }

            String baseName = originalFilename.replace("/", "_");
            File[] backupFiles = dir.listFiles((d, name) ->
                    name.startsWith(baseName + "_") && name.endsWith(".backup.gz"));

            if (backupFiles == null || backupFiles.length <= 10) {
                return; // Сохраняем последние 10 копий
            }

            // Сортировка по времени изменения (старые первыми)
            Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));

            // Удаление старых копий
            for (int i = 0; i < backupFiles.length - 10; i++) {
                backupFiles[i].delete();
                logger.debug("Deleted old backup: {}", backupFiles[i].getName());
            }

        } catch (Exception e) {
            logger.error("Error cleaning up old backups: {}", e.getMessage());
        }
    }

    /**
     * Получение списка файлов в директории
     */
    public static List<String> listFiles(String directory) {
        String dirPath = getFilePath(directory);
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }

        List<String> fileList = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                fileList.add(file.getName());
            }
        }

        return fileList;
    }

    /**
     * Получение размера файла в байтах
     */
    public static long getFileSize(String filename) {
        File file = new File(getFilePath(filename));
        return file.exists() ? file.length() : 0;
    }

    /**
     * Получение времени последнего изменения файла
     */
    public static long getLastModified(String filename) {
        File file = new File(getFilePath(filename));
        return file.exists() ? file.lastModified() : 0;
    }

    /**
     * Проверка, является ли файл пустым
     */
    public static boolean isEmpty(String filename) {
        File file = new File(getFilePath(filename));
        return !file.exists() || file.length() == 0;
    }

    /**
     * Создание директории
     */
    public static boolean createDirectory(String directory) {
        String dirPath = getFilePath(directory);
        File dir = new File(dirPath);
        return dir.mkdirs();
    }

    /**
     * Получение статистики файлового хранилища
     */
    public static Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();

        String dataDir = Config.getString("storage.data.dir", "./data");
        File dir = new File(dataDir);

        if (!dir.exists()) {
            stats.put("status", "directory_not_exists");
            return stats;
        }

        stats.put("directory", dataDir);
        stats.put("exists", true);
        stats.put("totalSpace", dir.getTotalSpace());
        stats.put("freeSpace", dir.getFreeSpace());
        stats.put("usableSpace", dir.getUsableSpace());

        // Подсчет файлов
        File[] files = dir.listFiles();
        if (files != null) {
            stats.put("totalFiles", files.length);

            long totalSize = 0;
            for (File file : files) {
                totalSize += file.length();
            }
            stats.put("totalSizeBytes", totalSize);
            stats.put("totalSizeMB", totalSize / (1024 * 1024));
        }

        return stats;
    }
}