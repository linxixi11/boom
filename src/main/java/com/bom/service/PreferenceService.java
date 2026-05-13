package com.bom.service;

import com.bom.db.DatabaseManager;

import java.sql.*;

/**
 * 应用偏好设置服务，用于保存/读取用户偏好（如分割条位置）。
 * 数据存储在 app_preference 表中。
 */
public class PreferenceService {
    private static final PreferenceService INSTANCE = new PreferenceService();

    public static PreferenceService getInstance() {
        return INSTANCE;
    }

    /**
     * 获取字符串类型的偏好值
     */
    public String getString(String key, String defaultValue) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT pref_value FROM app_preference WHERE pref_key = ?");
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString("pref_value");
                rs.close();
                ps.close();
                return val;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            // 表可能还不存在，返回默认值
        }
        return defaultValue;
    }

    /**
     * 获取整数类型的偏好值
     */
    public int getInt(String key, int defaultValue) {
        String val = getString(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 保存字符串类型的偏好值
     */
    public void putString(String key, String value) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            // MERGE = INSERT ON DUPLICATE UPDATE（H2 支持）
            PreparedStatement ps = conn.prepareStatement(
                "MERGE INTO app_preference (pref_key, pref_value) KEY(pref_key) VALUES (?, ?)");
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存整数类型的偏好值
     */
    public void putInt(String key, int value) {
        putString(key, String.valueOf(value));
    }
}
