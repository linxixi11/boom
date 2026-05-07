package com.bom.db;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.sql.*;
import java.util.Locale;

public class DatabaseManager {
    private static final String DB_URL;
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    static {
        File dataDir = resolveDataDir();
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new ExceptionInInitializerError("无法创建数据库目录: " + dataDir.getAbsolutePath());
        }
        DB_URL = "jdbc:h2:" + new File(dataDir, "bom_data").getAbsolutePath() + ";AUTO_SERVER=TRUE";
    }

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {}

    private static File resolveDataDir() {
        String configuredDir = System.getProperty("bom.data.dir");
        if (configuredDir != null && !configuredDir.trim().isEmpty()) {
            return new File(configuredDir.trim()).getAbsoluteFile();
        }
        return new File(resolveApplicationDir(), "data").getAbsoluteFile();
    }

    private static File resolveApplicationDir() {
        File launcherDir = resolveLauncherDir();
        if (launcherDir != null) {
            return launcherDir;
        }

        File codeSourceDir = resolveCodeSourceDir();
        if (codeSourceDir != null) {
            return codeSourceDir;
        }

        return new File(".").getAbsoluteFile();
    }

    private static File resolveLauncherDir() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command == null || command.trim().isEmpty()) {
                return null;
            }

            File launcher = new File(command);
            String launcherName = launcher.getName().toLowerCase(Locale.ROOT);
            if ("java".equals(launcherName) || "java.exe".equals(launcherName)
                    || "javaw".equals(launcherName) || "javaw.exe".equals(launcherName)) {
                return null;
            }

            File parent = launcher.getParentFile();
            return parent == null ? null : parent.getAbsoluteFile();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static File resolveCodeSourceDir() {
        try {
            CodeSource codeSource = DatabaseManager.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }

            URI locationUri = codeSource.getLocation().toURI();
            File location = Paths.get(locationUri).toFile();
            File baseDir = location.isFile() ? location.getParentFile() : location;
            if (baseDir == null) {
                return null;
            }

            if ("app".equalsIgnoreCase(baseDir.getName()) && baseDir.getParentFile() != null) {
                return baseDir.getParentFile().getAbsoluteFile();
            }

            if ("classes".equalsIgnoreCase(baseDir.getName())) {
                File targetDir = baseDir.getParentFile();
                if (targetDir != null && "target".equalsIgnoreCase(targetDir.getName())
                        && targetDir.getParentFile() != null) {
                    return targetDir.getParentFile().getAbsoluteFile();
                }
            }

            if ("target".equalsIgnoreCase(baseDir.getName()) && baseDir.getParentFile() != null) {
                return baseDir.getParentFile().getAbsoluteFile();
            }

            return baseDir.getAbsoluteFile();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(1)) {
            connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
        }
        return connection;
    }

    public void initDatabase() throws SQLException {
        Connection conn = getConnection();
        Statement stmt = conn.createStatement();

        stmt.execute(
            "CREATE TABLE IF NOT EXISTS component (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  type VARCHAR(20) NOT NULL," +
            "  code VARCHAR(50) UNIQUE," +
            "  name VARCHAR(100) NOT NULL," +
            "  spec VARCHAR(200)," +
            "  unit VARCHAR(20)," +
            "  material VARCHAR(100)," +
            "  remark VARCHAR(500)," +
            "  stock_qty DOUBLE DEFAULT 0" +
            ")"
        );

        stmt.execute("ALTER TABLE component ADD COLUMN IF NOT EXISTS stock_qty DOUBLE DEFAULT 0");

        stmt.execute(
            "CREATE TABLE IF NOT EXISTS bom_item (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  parent_id BIGINT," +
            "  child_id BIGINT," +
            "  quantity DOUBLE NOT NULL," +
            "  FOREIGN KEY (parent_id) REFERENCES component(id) ON DELETE CASCADE," +
            "  FOREIGN KEY (child_id) REFERENCES component(id)" +
            ")"
        );

        stmt.close();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
