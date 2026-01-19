package com.parser;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ProjectStructureExporter {
    private static final String OUTPUT_FILE = "project_structure.txt";
    private static final String[] IGNORED_DIRS = {".git", ".idea", "target", "build", "node_modules", "out", "bin"};
    private static final int MAX_FILE_SIZE = 1024 * 1024 * 1024; // 1MB - максимальный размер файла для чтения

    public static void main(String[] args) throws IOException {
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        System.out.println("Сканирую директорию: " + currentDir);

        try (PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_FILE))) {
            writer.println("СТРУКТУРА ПРОЕКТА");
            writer.println("=================");
            writer.println("Директория: " + currentDir);
            writer.println("Дата создания отчета: " + java.time.LocalDateTime.now());
            writer.println("\n" + "=".repeat(80) + "\n");

            Files.walkFileTree(currentDir, new SimpleFileVisitor<Path>() {
                private int depth = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Пропускаем игнорируемые директории
                    for (String ignored : IGNORED_DIRS) {
                        if (dir.toString().contains(File.separator + ignored)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }

                    // Печатаем структуру директорий
                    String indent = "  ".repeat(depth);
                    writer.println(indent + "[DIR] " + dir.getFileName());
                    depth++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String indent = "  ".repeat(depth);
                    String fileName = file.getFileName().toString();

                    // Пропускаем сам файл отчета
                    if (fileName.equals(OUTPUT_FILE)) {
                        return FileVisitResult.CONTINUE;
                    }

                    writer.println("\n" + indent + "[FILE] " + fileName);
                    writer.println(indent + "  Путь: " + file);
                    writer.println(indent + "  Размер: " + attrs.size() + " байт");
                    writer.println(indent + "  Дата изменения: " + attrs.lastModifiedTime());

                    // Читаем содержимое текстовых файлов
                    if (isTextFile(fileName) && attrs.size() <= MAX_FILE_SIZE) {
                        writer.println(indent + "  Содержимое:");
                        writer.println(indent + "  " + "-".repeat(40));
                        try {
                            String content = Files.readString(file);
                            String[] lines = content.split("\n");
                            for (int i = 0; i < Math.min(lines.length, 10000); i++) { // Ограничиваем 100 строками
                                writer.println(indent + "  " + lines[i]);
                            }
                            if (lines.length > 10000) {
                                writer.println(indent + "  ... (файл усечен, показано 100 из " + lines.length + " строк)");
                            }
                        } catch (IOException e) {
                            writer.println(indent + "  Невозможно прочитать файл (возможно, бинарный)");
                        }
                        writer.println(indent + "  " + "-".repeat(40));
                    } else {
                        writer.println(indent + "  [БИНАРНЫЙ ФАЙЛ ИЛИ СЛИШКОМ БОЛЬШОЙ]");
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    writer.println("\nОшибка при доступе к файлу: " + file);
                    return FileVisitResult.CONTINUE;
                }
            });

            writer.println("\n" + "=".repeat(80));
            writer.println("Отчет успешно создан!");
        }

        System.out.println("Отчет сохранен в файл: " + OUTPUT_FILE);
    }

    private static boolean isTextFile(String fileName) {
        String[] textExtensions = {
                ".java", ".txt", ".xml", ".html", ".htm", ".css", ".js", ".json",
                ".properties", ".yml", ".yaml", ".md", ".gradle", ".kt", ".py",
                ".cpp", ".c", ".h", ".hpp", ".sql", ".sh", ".bat", ".cfg", ".ini"
        };

        String lowerName = fileName.toLowerCase();
        for (String ext : textExtensions) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}