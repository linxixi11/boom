package com.bom.service;

import com.bom.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OptionService {
    public static final String CATEGORY_UNIT = "UNIT";
    public static final String CATEGORY_MATERIAL = "MATERIAL";

    public List<String> getOptions(String category) throws SQLException {
        List<String> options = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT option_value FROM app_option WHERE category = ? ORDER BY sort_order, id")) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    options.add(rs.getString("option_value"));
                }
            }
        }
        return options;
    }

    public void addOption(String category, String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) return;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement countPs = conn.prepareStatement("SELECT MAX(sort_order) FROM app_option WHERE category = ?")) {
            countPs.setString(1, category);
            int nextSort = 1;
            try (ResultSet rs = countPs.executeQuery()) {
                if (rs.next()) {
                    nextSort = rs.getInt(1) + 1;
                }
            }

            try (PreparedStatement insertPs = conn.prepareStatement(
                    "INSERT INTO app_option (category, option_value, sort_order) VALUES (?, ?, ?)")) {
                insertPs.setString(1, category);
                insertPs.setString(2, value.trim());
                insertPs.setInt(3, nextSort);
                insertPs.executeUpdate();
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // 已存在，忽略
        }
    }
    
    public void deleteOption(String category, String value) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM app_option WHERE category = ? AND option_value = ?")) {
            ps.setString(1, category);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
