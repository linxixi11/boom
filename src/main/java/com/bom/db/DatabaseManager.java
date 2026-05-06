package com.bom.db;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL;
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    static {
        String dataDir = System.getProperty("bom.data.dir");
        if (dataDir == null || dataDir.isEmpty()) {
            dataDir = System.getProperty("user.home") + File.separator + ".bom";
        }
        File dir = new File(dataDir);
        if (!dir.exists()) dir.mkdirs();
        DB_URL = "jdbc:h2:" + dataDir + File.separator + "bom_data;AUTO_SERVER=TRUE";
    }

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {}

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
