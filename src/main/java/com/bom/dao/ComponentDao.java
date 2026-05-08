package com.bom.dao;

import com.bom.db.DatabaseManager;
import com.bom.model.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComponentDao {

    /**
     * 根据类型前缀自动生成下一个编码，如 LJ-001, BC-002, CP-003
     */
    public String nextCode(String type) throws SQLException {
        String prefix;
        switch (type) {
            case Component.TYPE_PART:    prefix = "LJ"; break;
            case Component.TYPE_PURCHASE: prefix = "WG"; break;
            case Component.TYPE_SEMI:    prefix = "BC"; break;
            case Component.TYPE_PRODUCT: prefix = "CP"; break;
            default: prefix = "XX";
        }
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT code FROM component WHERE type = ?");
        ps.setString(1, type);
        ResultSet rs = ps.executeQuery();
        int nextNum = 1;
        int width = 3;
        Pattern trailingNumber = Pattern.compile("(\\d+)$");
        if (rs.next()) {
            do {
                String code = rs.getString("code");
                if (code == null) continue;
                Matcher matcher = trailingNumber.matcher(code.trim());
                if (!matcher.find()) continue;
                try {
                    nextNum = Math.max(nextNum, Integer.parseInt(matcher.group(1)) + 1);
                    width = Math.max(width, matcher.group(1).length());
                } catch (NumberFormatException ignored) {}
            } while (rs.next());
        }
        rs.close();
        ps.close();
        return prefix + "-" + String.format("%0" + width + "d", nextNum);
    }

    public List<Component> findByType(String type) throws SQLException {
        List<Component> list = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM component WHERE type = ? ORDER BY code");
        ps.setString(1, type);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        rs.close();
        ps.close();
        return list;
    }

    public List<Component> findAll() throws SQLException {
        List<Component> list = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM component ORDER BY type, code");
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        rs.close();
        stmt.close();
        return list;
    }

    public Component findById(Long id) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM component WHERE id = ?");
        ps.setLong(1, id);
        ResultSet rs = ps.executeQuery();
        Component c = null;
        if (rs.next()) {
            c = mapRow(rs);
        }
        rs.close();
        ps.close();
        return c;
    }

    public Component findByCode(String code) throws SQLException {
        if (code == null || code.trim().isEmpty()) return null;
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM component WHERE code = ?");
        ps.setString(1, code.trim());
        ResultSet rs = ps.executeQuery();
        Component c = null;
        if (rs.next()) {
            c = mapRow(rs);
        }
        rs.close();
        ps.close();
        return c;
    }

    public void insert(Component c) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO component (type, code, name, spec, unit, material, remark, stock_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, c.getType());
        ps.setString(2, c.getCode());
        ps.setString(3, c.getName());
        ps.setString(4, c.getSpec());
        ps.setString(5, c.getUnit());
        ps.setString(6, c.getMaterial());
        ps.setString(7, c.getRemark());
        ps.setDouble(8, c.getStockQty());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) {
            c.setId(keys.getLong(1));
        }
        keys.close();
        ps.close();
    }

    public void update(Component c) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE component SET code=?, name=?, spec=?, unit=?, material=?, remark=?, stock_qty=? WHERE id=?");
        ps.setString(1, c.getCode());
        ps.setString(2, c.getName());
        ps.setString(3, c.getSpec());
        ps.setString(4, c.getUnit());
        ps.setString(5, c.getMaterial());
        ps.setString(6, c.getRemark());
        ps.setDouble(7, c.getStockQty());
        ps.setLong(8, c.getId());
        ps.executeUpdate();
        ps.close();
    }

    public void updateIncludingType(Component c) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE component SET type=?, code=?, name=?, spec=?, unit=?, material=?, remark=?, stock_qty=? WHERE id=?");
        ps.setString(1, c.getType());
        ps.setString(2, c.getCode());
        ps.setString(3, c.getName());
        ps.setString(4, c.getSpec());
        ps.setString(5, c.getUnit());
        ps.setString(6, c.getMaterial());
        ps.setString(7, c.getRemark());
        ps.setDouble(8, c.getStockQty());
        ps.setLong(9, c.getId());
        ps.executeUpdate();
        ps.close();
        if (isLeafType(c.getType())) {
            deleteChildrenOf(c.getId());
        }
    }

    public void saveFromImport(Component imported) throws SQLException {
        Component existing = null;
        if (imported.getId() != null) {
            existing = findById(imported.getId());
        }
        if (existing == null) {
            existing = findByCode(imported.getCode());
        }
        if (existing == null) {
            insert(imported);
        } else {
            imported.setId(existing.getId());
            updateIncludingType(imported);
        }
    }

    public void updateType(Long id, String targetType) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            if (isLeafType(targetType)) {
                deleteChildrenOf(id);
            }
            PreparedStatement ps = conn.prepareStatement("UPDATE component SET type=? WHERE id=?");
            ps.setString(1, targetType);
            ps.setLong(2, id);
            ps.executeUpdate();
            ps.close();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    public void delete(Long id) throws SQLException {
        deleteMany(Collections.singletonList(id));
    }

    public void deleteMany(List<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return;
        Connection conn = DatabaseManager.getInstance().getConnection();
        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            PreparedStatement deleteBom = conn.prepareStatement(
                "DELETE FROM bom_item WHERE parent_id = ? OR child_id = ?");
            PreparedStatement deleteComponent = conn.prepareStatement("DELETE FROM component WHERE id = ?");
            for (Long id : ids) {
                if (id == null) continue;
                deleteBom.setLong(1, id);
                deleteBom.setLong(2, id);
                deleteBom.executeUpdate();
                deleteComponent.setLong(1, id);
                deleteComponent.executeUpdate();
            }
            deleteBom.close();
            deleteComponent.close();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    private void deleteChildrenOf(Long parentId) throws SQLException {
        if (parentId == null) return;
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_item WHERE parent_id = ?");
        ps.setLong(1, parentId);
        ps.executeUpdate();
        ps.close();
    }

    private boolean isLeafType(String type) {
        return Component.TYPE_PART.equals(type) || Component.TYPE_PURCHASE.equals(type);
    }

    private Component mapRow(ResultSet rs) throws SQLException {
        Component c = new Component();
        c.setId(rs.getLong("id"));
        c.setType(rs.getString("type"));
        c.setCode(rs.getString("code"));
        c.setName(rs.getString("name"));
        c.setSpec(rs.getString("spec"));
        c.setUnit(rs.getString("unit"));
        c.setMaterial(rs.getString("material"));
        c.setRemark(rs.getString("remark"));
        c.setStockQty(rs.getDouble("stock_qty"));
        return c;
    }
}
