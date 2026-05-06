package com.bom.dao;

import com.bom.db.DatabaseManager;
import com.bom.model.BomItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BomItemDao {

    /** 一次性把所有 BOM 关系按 parent_id 分组取到内存。供汇总递归使用，避免 N+1。 */
    public Map<Long, List<BomItem>> findAllGrouped() throws SQLException {
        Map<Long, List<BomItem>> map = new HashMap<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT bi.*, c.code AS child_code, c.name AS child_name, c.spec AS child_spec, " +
                "c.type AS child_type, c.unit AS child_unit, c.material AS child_material " +
                "FROM bom_item bi JOIN component c ON bi.child_id = c.id " +
                "ORDER BY bi.parent_id, c.code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BomItem item = mapRow(rs);
                map.computeIfAbsent(item.getParentId(), k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }

    public List<BomItem> findByParentId(Long parentId) throws SQLException {
        List<BomItem> list = new ArrayList<>();
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT bi.*, c.code AS child_code, c.name AS child_name, c.spec AS child_spec, " +
            "c.type AS child_type, c.unit AS child_unit, c.material AS child_material " +
            "FROM bom_item bi JOIN component c ON bi.child_id = c.id WHERE bi.parent_id = ? ORDER BY c.code");
        ps.setLong(1, parentId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        rs.close();
        ps.close();
        return list;
    }

    public void insert(BomItem item) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO bom_item (parent_id, child_id, quantity) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS);
        ps.setLong(1, item.getParentId());
        ps.setLong(2, item.getChildId());
        ps.setDouble(3, item.getQuantity());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) {
            item.setId(keys.getLong(1));
        }
        keys.close();
        ps.close();
    }

    public void update(BomItem item) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE bom_item SET quantity = ? WHERE id = ?");
        ps.setDouble(1, item.getQuantity());
        ps.setLong(2, item.getId());
        ps.executeUpdate();
        ps.close();
    }

    public void delete(Long id) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_item WHERE id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public void deleteByParentId(Long parentId) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_item WHERE parent_id = ?");
        ps.setLong(1, parentId);
        ps.executeUpdate();
        ps.close();
    }

    private BomItem mapRow(ResultSet rs) throws SQLException {
        BomItem item = new BomItem();
        item.setId(rs.getLong("id"));
        item.setParentId(rs.getLong("parent_id"));
        item.setChildId(rs.getLong("child_id"));
        item.setQuantity(rs.getDouble("quantity"));
        item.setChildCode(rs.getString("child_code"));
        item.setChildName(rs.getString("child_name"));
        item.setChildSpec(rs.getString("child_spec"));
        item.setChildType(rs.getString("child_type"));
        item.setChildUnit(rs.getString("child_unit"));
        item.setChildMaterial(rs.getString("child_material"));
        return item;
    }
}
