package com.bom.dao;

import com.bom.db.DatabaseManager;
import com.bom.model.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ComponentDao {

    /**
     * 根据类型前缀自动生成下一个编码，如 LJ-001, BC-002, CP-003
     */
    public String nextCode(String type) throws SQLException {
        String prefix;
        switch (type) {
            case Component.TYPE_PART:    prefix = "LJ"; break;
            case Component.TYPE_SEMI:    prefix = "BC"; break;
            case Component.TYPE_PRODUCT: prefix = "CP"; break;
            default: prefix = "XX";
        }
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT code FROM component WHERE type = ? ORDER BY code DESC LIMIT 1");
        ps.setString(1, type);
        ResultSet rs = ps.executeQuery();
        int nextNum = 1;
        if (rs.next()) {
            String lastCode = rs.getString("code");
            if (lastCode != null && lastCode.contains("-")) {
                try {
                    nextNum = Integer.parseInt(lastCode.substring(lastCode.lastIndexOf('-') + 1)) + 1;
                } catch (NumberFormatException ignored) {}
            }
        }
        rs.close();
        ps.close();
        return prefix + "-" + String.format("%03d", nextNum);
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

    public void delete(Long id) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("DELETE FROM component WHERE id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();
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
