package org.example.gp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Автоматичен backup на MySQL базата чрез mysqldump.
 *
 * ВАЖНО: На хостинг платформи с ефимерен диск (напр. Render free план)
 * локалните файлове в backup директорията се губят при рестарт/redeploy.
 * Затова всеки успешен backup се изпраща и по имейл (ако е конфигуриран
 * app.backup.notify-email) — това е "off-server" копие без нужда от
 * допълнителна облачна интеграция.
 */
@Service
public class BackupService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${app.backup.dir}")
    private String backupDirPath;

    @Value("${app.backup.retention:14}")
    private int retention;

    @Value("${app.backup.notify-email:}")
    private String notifyEmail;

    public BackupService(EmailService emailService, AuditLogService auditLogService) {
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------------------
    // Планиран backup — по подразбиране всяка нощ в 03:00 (app.backup.cron)
    // -------------------------------------------------------------------------
    @Scheduled(cron = "${app.backup.cron:0 0 3 * * *}")
    public void scheduledBackup() {
        try {
            Path backup = createBackup();
            auditLogService.log("system", null, null, "backup.created", "SCHEDULED", "-",
                    backup.getFileName().toString(), "-", true, null);
            emailBackupCopy(backup);
        } catch (Exception e) {
            auditLogService.log("system", null, null, "backup.failed", "SCHEDULED", "-",
                    null, "-", false, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Създава нов backup (mysqldump → gzip). Хвърля IOException при провал.
    // -------------------------------------------------------------------------
    public Path createBackup() throws IOException, InterruptedException {
        Path dir = ensureBackupDir();

        String[] conn = parseJdbcUrl(datasourceUrl);
        String host = conn[0], port = conn[1], dbName = conn[2];

        String timestamp = LocalDateTime.now().format(FILE_TS);
        Path target = dir.resolve("backup_" + dbName + "_" + timestamp + ".sql.gz");

        ProcessBuilder pb = new ProcessBuilder(
                "mysqldump",
                "-h", host,
                "-P", port,
                "-u", datasourceUsername,
                "--single-transaction",
                "--routines",
                "--events",
                "--no-tablespaces",
                dbName
        );
        // Подаваме паролата през env, за да не се вижда в списъка на процесите
        pb.environment().put("MYSQL_PWD", datasourcePassword != null ? datasourcePassword : "");

        Process process = pb.start();

        StringBuilder errorOutput = new StringBuilder();
        Thread errorReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // потокът е затворен заедно с процеса
            }
        });
        errorReaderThread.start();

        try (var stdOut = process.getInputStream();
             var fileOut = Files.newOutputStream(target);
             var gzOut = new GZIPOutputStream(fileOut)) {
            stdOut.transferTo(gzOut);
        }

        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        errorReaderThread.join(2000);

        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(target);
            throw new IOException("mysqldump не завърши в рамките на 5 минути (timeout).");
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(target);
            throw new IOException("mysqldump завърши с грешка (код " + process.exitValue() + "): "
                    + errorOutput.toString().trim());
        }

        cleanupOldBackups();
        return target;
    }

    // -------------------------------------------------------------------------
    // Изпраща копие на backup-а по имейл, ако е конфигуриран получател.
    // -------------------------------------------------------------------------
    private void emailBackupCopy(Path backup) {
        if (notifyEmail == null || notifyEmail.isBlank()) return;
        try {
            long sizeBytes = Files.size(backup);
            emailService.sendWithAttachment(notifyEmail, "Backup на базата данни",
                    "backup-created", Map.of(
                            "fileName", backup.getFileName().toString(),
                            "fileSize", humanReadableSize(sizeBytes),
                            "timestamp", LocalDateTime.now().format(DISPLAY_TS)
                    ), backup.toFile(), backup.getFileName().toString());
        } catch (IOException ignored) {
            // ако не можем да прочетем размера, просто пропускаме имейла
        }
    }

    // -------------------------------------------------------------------------
    // Списък с наличните backup файлове, най-новите първи.
    // -------------------------------------------------------------------------
    public List<Path> listBackups() throws IOException {
        Path dir = ensureBackupDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".sql.gz"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .collect(Collectors.toList());
        }
    }

    public Path getBackupDir() {
        return Path.of(backupDirPath);
    }

    public void deleteBackup(String filename) throws IOException {
        validateFilename(filename);
        Files.deleteIfExists(ensureBackupDir().resolve(filename));
    }

    /** Проверява, че имейлите се изпращат при backup (за информация в UI-то). */
    public boolean isEmailNotificationConfigured() {
        return notifyEmail != null && !notifyEmail.isBlank();
    }

    public void validateFilename(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")
                || !filename.matches("backup_.+\\.sql\\.gz")) {
            throw new IllegalArgumentException("Невалидно ime на backup файл.");
        }
    }

    private void cleanupOldBackups() throws IOException {
        List<Path> files = listBackups();
        if (files.size() > retention) {
            for (Path old : files.subList(retention, files.size())) {
                Files.deleteIfExists(old);
            }
        }
    }

    private Path ensureBackupDir() throws IOException {
        Path dir = Path.of(backupDirPath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /** Извлича host, port, database ime от JDBC URL от вида jdbc:mysql://host:port/dbname?params */
    private String[] parseJdbcUrl(String jdbcUrl) {
        String withoutPrefix = jdbcUrl.replaceFirst("^jdbc:mysql://", "");
        String hostPortAndPath = withoutPrefix;
        int slashIdx = hostPortAndPath.indexOf('/');
        String hostPort = slashIdx >= 0 ? hostPortAndPath.substring(0, slashIdx) : hostPortAndPath;
        String pathAndQuery = slashIdx >= 0 ? hostPortAndPath.substring(slashIdx + 1) : "";
        String dbName = pathAndQuery.contains("?") ? pathAndQuery.substring(0, pathAndQuery.indexOf('?')) : pathAndQuery;

        String host;
        String port = "3306";
        if (hostPort.contains(":")) {
            String[] parts = hostPort.split(":");
            host = parts[0];
            port = parts[1];
        } else {
            host = hostPort;
        }
        return new String[]{host, port, dbName};
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
