package org.example.gp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Автоматичен backup на MySQL базата — генерира пълен SQL дъмп директно
 * през JDBC връзката на приложението (същия драйвер, който вече се ползва
 * за нормалната работа), без да разчита на външен CLI инструмент.
 *
 * Защо не mysqldump: инструментите на mariadb-client (mysqldump/mariadb-dump)
 * в Alpine образи не носят caching_sha2_password плъгина, който MySQL 8
 * (напр. Aiven) ползва по подразбиране, и не могат да се свържат. Официалният
 * MySQL Connector/J (com.mysql.cj.jdbc.Driver), който приложението вече
 * използва, поддържа caching_sha2_password нативно — затова генерираме
 * дъмпа изцяло в Java, без нужда от системни пакети в Docker образа.
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
    private static final int BATCH_SIZE = 500; // редове на INSERT изявление

    private final DataSource dataSource;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${app.backup.dir}")
    private String backupDirPath;

    @Value("${app.backup.retention:14}")
    private int retention;

    @Value("${app.backup.notify-email:}")
    private String notifyEmail;

    public BackupService(DataSource dataSource, EmailService emailService, AuditLogService auditLogService) {
        this.dataSource = dataSource;
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
    // Създава нов backup (чист JDBC SQL дъмп → gzip). Хвърля Exception при провал.
    // -------------------------------------------------------------------------
    public Path createBackup() throws IOException, SQLException {
        Path dir = ensureBackupDir();
        String dbName = parseDbNameFromUrl(datasourceUrl);

        String timestamp = LocalDateTime.now().format(FILE_TS);
        Path target = dir.resolve("backup_" + dbName + "_" + timestamp + ".sql.gz");

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // консистентна снимка за целия дъмп
            String actualDb = conn.getCatalog() != null ? conn.getCatalog() : dbName;

            try (Writer writer = new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(target)), StandardCharsets.UTF_8)) {

                writer.write("-- GDD Program — MySQL backup\n");
                writer.write("-- База: " + actualDb + "\n");
                writer.write("-- Дата: " + LocalDateTime.now().format(DISPLAY_TS) + "\n\n");
                writer.write("SET NAMES utf8mb4;\n");
                writer.write("SET FOREIGN_KEY_CHECKS=0;\n\n");

                List<String> tables = listTables(conn, actualDb);
                for (String table : tables) {
                    dumpTable(conn, table, writer);
                }

                writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
            } finally {
                conn.rollback(); // само четем — нищо не трябва да се комитва
            }
        } catch (Exception e) {
            Files.deleteIfExists(target);
            if (e instanceof SQLException se) throw se;
            if (e instanceof IOException ie) throw ie;
            throw new IOException(e.getMessage(), e);
        }

        if (Files.size(target) == 0) {
            Files.deleteIfExists(target);
            throw new IOException("Backup файлът излезе празен.");
        }

        cleanupOldBackups();
        return target;
    }

    private List<String> listTables(Connection conn, String dbName) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        }
        return tables;
    }

    private void dumpTable(Connection conn, String table, Writer writer) throws SQLException, IOException {
        // --- схема ---
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            if (rs.next()) {
                writer.write("DROP TABLE IF EXISTS `" + table + "`;\n");
                writer.write(rs.getString(2) + ";\n\n");
            }
        }

        // --- данни ---
        try (Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(BATCH_SIZE);
            try (ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "`")) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                String columnList = buildColumnList(meta, columnCount);

                List<String> rowsBuffer = new ArrayList<>(BATCH_SIZE);
                while (rs.next()) {
                    rowsBuffer.add(buildValueTuple(rs, meta, columnCount));
                    if (rowsBuffer.size() >= BATCH_SIZE) {
                        writeInsert(writer, table, columnList, rowsBuffer);
                        rowsBuffer.clear();
                    }
                }
                if (!rowsBuffer.isEmpty()) {
                    writeInsert(writer, table, columnList, rowsBuffer);
                }
            }
        }
        writer.write("\n");
    }

    private String buildColumnList(ResultSetMetaData meta, int columnCount) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) sb.append(", ");
            sb.append('`').append(meta.getColumnName(i)).append('`');
        }
        return sb.toString();
    }

    private String buildValueTuple(ResultSet rs, ResultSetMetaData meta, int columnCount) throws SQLException {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) sb.append(", ");
            sb.append(formatValue(rs, meta.getColumnType(i), i));
        }
        return sb.append(')').toString();
    }

    private String formatValue(ResultSet rs, int sqlType, int index) throws SQLException {
        switch (sqlType) {
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY: {
                byte[] bytes = rs.getBytes(index);
                if (rs.wasNull() || bytes == null) return "NULL";
                StringBuilder hex = new StringBuilder("0x");
                for (byte b : bytes) hex.append(String.format("%02x", b));
                return hex.toString();
            }
            case Types.BOOLEAN:
            case Types.BIT: {
                boolean v = rs.getBoolean(index);
                return rs.wasNull() ? "NULL" : (v ? "1" : "0");
            }
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.DOUBLE:
            case Types.FLOAT: {
                String v = rs.getString(index);
                return (rs.wasNull() || v == null) ? "NULL" : v;
            }
            default: {
                String v = rs.getString(index);
                if (rs.wasNull() || v == null) return "NULL";
                return "'" + escapeSql(v) + "'";
            }
        }
    }

    private String escapeSql(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\0", "");
    }

    private void writeInsert(Writer writer, String table, String columnList, List<String> valueTuples) throws IOException {
        writer.write("INSERT INTO `" + table + "` (" + columnList + ") VALUES\n");
        writer.write(String.join(",\n", valueTuples));
        writer.write(";\n");
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

    /** Извлича database ime от JDBC URL от вида jdbc:mysql://host:port/dbname?params (само за ime на файла). */
    private String parseDbNameFromUrl(String jdbcUrl) {
        try {
            String withoutPrefix = jdbcUrl.replaceFirst("^jdbc:mysql://", "");
            int slashIdx = withoutPrefix.indexOf('/');
            String pathAndQuery = slashIdx >= 0 ? withoutPrefix.substring(slashIdx + 1) : "";
            String dbName = pathAndQuery.contains("?") ? pathAndQuery.substring(0, pathAndQuery.indexOf('?')) : pathAndQuery;
            return (dbName == null || dbName.isBlank()) ? "gdd" : dbName;
        } catch (Exception e) {
            return "gdd";
        }
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
