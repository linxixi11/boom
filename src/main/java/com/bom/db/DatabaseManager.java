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

        stmt.execute(
            "CREATE TABLE IF NOT EXISTS bom_order (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  project_name VARCHAR(200) NOT NULL," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );

        stmt.execute(
            "CREATE TABLE IF NOT EXISTS bom_order_item (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  order_id BIGINT NOT NULL," +
            "  seq_no INT NOT NULL," +
            "  component_type VARCHAR(20)," +
            "  component_code VARCHAR(50)," +
            "  component_name VARCHAR(100)," +
            "  spec VARCHAR(200)," +
            "  material VARCHAR(100)," +
            "  unit VARCHAR(20)," +
            "  total_qty DOUBLE DEFAULT 0," +
            "  stock_qty DOUBLE DEFAULT 0," +
            "  deducted_qty DOUBLE DEFAULT 0," +
            "  shortage_qty DOUBLE DEFAULT 0," +
            "  stock_remark VARCHAR(500)," +
            "  FOREIGN KEY (order_id) REFERENCES bom_order(id) ON DELETE CASCADE" +
            ")"
        );

        // 订单原始选择项（成品/半成品/零件/外购件 + 数量），用于订单可再次编辑/重算汇总
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS bom_order_pick (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  order_id BIGINT NOT NULL," +
            "  component_id BIGINT NOT NULL," +
            "  quantity DOUBLE NOT NULL," +
            "  FOREIGN KEY (order_id) REFERENCES bom_order(id) ON DELETE CASCADE" +
            ")"
        );

        stmt.close();
        initOptionTable(conn);
        initPreferenceTable(conn);
    }

    private void initOptionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS app_option (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  category VARCHAR(30) NOT NULL," +
                "  option_value VARCHAR(100) NOT NULL," +
                "  sort_order INT DEFAULT 0," +
                "  UNIQUE(category, option_value)" +
                ")"
            );
        }
        seedOptionsIfEmpty(conn, "MATERIAL", new String[]{"敷铝锌板", "冷轧板"});
        seedOptionsIfEmpty(conn, "UNIT", new String[]{"个", "张", "米", "台", "套", "件"});
    }

    private void seedOptionsIfEmpty(Connection conn, String category, String[] values) throws SQLException {
        try (PreparedStatement countPs = conn.prepareStatement("SELECT COUNT(*) FROM app_option WHERE category = ?")) {
            countPs.setString(1, category);
            try (ResultSet rs = countPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
        }

        try (PreparedStatement insertPs = conn.prepareStatement(
                "INSERT INTO app_option (category, option_value, sort_order) VALUES (?, ?, ?)")) {
            for (int i = 0; i < values.length; i++) {
                insertPs.setString(1, category);
                insertPs.setString(2, values[i]);
                insertPs.setInt(3, i + 1);
                insertPs.addBatch();
            }
            insertPs.executeBatch();
        }
    }

    private void initPreferenceTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS app_preference (" +
                "  pref_key VARCHAR(100) PRIMARY KEY," +
                "  pref_value VARCHAR(500)" +
                ")"
            );
        }
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
