package com.bom.service;

import com.bom.db.DatabaseManager;
import com.bom.service.BomService.BomSummaryRow;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderService {

    public static class Order {
        public Long id;
        public String projectName;
        public Timestamp createdAt;
    }

    public static class OrderItem {
        public Long id;
        public Long orderId;
        public int seqNo;
        public String componentType;
        public String componentCode;
        public String componentName;
        public String spec;
        public String material;
        public String unit;
        public double totalQty;
        public double stockQty;
        public double deductedQty;
        public double shortageQty;
        public String stockRemark;
    }

    /** 订单的原始选择项：用户挑选的成品/半成品/零件/外购件 + 数量。 */
    public static class OrderPick {
        public Long componentId;
        public double quantity;

        public OrderPick() {}

        public OrderPick(Long componentId, double quantity) {
            this.componentId = componentId;
            this.quantity = quantity;
        }
    }

    public long saveOrder(String projectName, List<BomSummaryRow> rows) throws SQLException {
        return saveOrder(projectName, rows, null);
    }

    public long saveOrder(String projectName, List<BomSummaryRow> rows, List<OrderPick> picks) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        boolean autoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            long orderId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO bom_order (project_name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, projectName);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        orderId = rs.getLong(1);
                    } else {
                        throw new SQLException("无法获取生成的订单ID");
                    }
                }
            }

            insertItems(conn, orderId, rows);
            insertPicks(conn, orderId, picks);
            deductStock(conn, rows);

            conn.commit();
            return orderId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /**
     * 覆盖已有订单：先把上次扣减的库存还回，再删旧明细/选择项，写入新数据并重新扣库存。
     * 整个过程在一个事务里，保证库存不会重复扣减。
     */
    public void updateOrder(long orderId, String projectName, List<BomSummaryRow> rows, List<OrderPick> picks) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        boolean autoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            restorePreviousDeductions(conn, orderId);

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_order_item WHERE order_id = ?")) {
                ps.setLong(1, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_order_pick WHERE order_id = ?")) {
                ps.setLong(1, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE bom_order SET project_name = ? WHERE id = ?")) {
                ps.setString(1, projectName);
                ps.setLong(2, orderId);
                ps.executeUpdate();
            }

            insertItems(conn, orderId, rows);
            insertPicks(conn, orderId, picks);
            deductStock(conn, rows);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private void insertItems(Connection conn, long orderId, List<BomSummaryRow> rows) throws SQLException {
        if (rows == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bom_order_item (order_id, seq_no, component_type, component_code, " +
                "component_name, spec, material, unit, total_qty, stock_qty, deducted_qty, " +
                "shortage_qty, stock_remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int seq = 1;
            for (BomSummaryRow row : rows) {
                ps.setLong(1, orderId);
                ps.setInt(2, seq++);
                // 存储时使用转换后的类型标签，确保和界面一致
                ps.setString(3, BomService.typeLabel(row.type));
                ps.setString(4, row.code);
                ps.setString(5, row.name);
                ps.setString(6, row.spec);
                ps.setString(7, row.material);
                ps.setString(8, row.unit);
                ps.setDouble(9, row.totalQty);
                ps.setDouble(10, row.stockQty);
                ps.setDouble(11, row.deductedQty);
                ps.setDouble(12, row.shortageQty);
                ps.setString(13, row.stockRemark);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertPicks(Connection conn, long orderId, List<OrderPick> picks) throws SQLException {
        if (picks == null || picks.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bom_order_pick (order_id, component_id, quantity) VALUES (?, ?, ?)")) {
            for (OrderPick pick : picks) {
                if (pick == null || pick.componentId == null || pick.quantity <= 0) continue;
                ps.setLong(1, orderId);
                ps.setLong(2, pick.componentId);
                ps.setDouble(3, pick.quantity);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deductStock(Connection conn, List<BomSummaryRow> rows) throws SQLException {
        if (rows == null) return;
        try (PreparedStatement byId = conn.prepareStatement(
                "UPDATE component SET stock_qty = GREATEST(0, stock_qty - ?) WHERE id = ?");
             PreparedStatement byCode = conn.prepareStatement(
                "UPDATE component SET stock_qty = GREATEST(0, stock_qty - ?) WHERE code = ?")) {
            for (BomSummaryRow row : rows) {
                if (row.deductedQty <= 0) continue;
                if (row.componentId != null) {
                    byId.setDouble(1, row.deductedQty);
                    byId.setLong(2, row.componentId);
                    byId.addBatch();
                } else if (row.code != null && !row.code.trim().isEmpty()) {
                    // 旧订单明细没有 componentId，按物料编号扣减，保证覆盖保存时库存对账一致
                    byCode.setDouble(1, row.deductedQty);
                    byCode.setString(2, row.code);
                    byCode.addBatch();
                }
            }
            byId.executeBatch();
            byCode.executeBatch();
        }
    }

    /** 把该订单上一次扣减的库存按物料编号加回，便于覆盖保存时重新扣减。 */
    private void restorePreviousDeductions(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT component_code, deducted_qty FROM bom_order_item WHERE order_id = ? AND deducted_qty > 0");
             PreparedStatement restore = conn.prepareStatement(
                "UPDATE component SET stock_qty = stock_qty + ? WHERE code = ?")) {
            select.setLong(1, orderId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("component_code");
                    double deducted = rs.getDouble("deducted_qty");
                    if (code == null || code.trim().isEmpty() || deducted <= 0) continue;
                    restore.setDouble(1, deducted);
                    restore.setString(2, code);
                    restore.addBatch();
                }
            }
            restore.executeBatch();
        }
    }

    public List<Order> getAllOrders() throws SQLException {
        List<Order> orders = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, project_name, created_at FROM bom_order ORDER BY created_at DESC")) {
            while (rs.next()) {
                Order order = new Order();
                order.id = rs.getLong("id");
                order.projectName = rs.getString("project_name");
                order.createdAt = rs.getTimestamp("created_at");
                orders.add(order);
            }
        }
        return orders;
    }

    public List<OrderItem> getOrderItems(Long orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM bom_order_item WHERE order_id = ? ORDER BY seq_no")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.id = rs.getLong("id");
                    item.orderId = rs.getLong("order_id");
                    item.seqNo = rs.getInt("seq_no");
                    item.componentType = rs.getString("component_type");
                    item.componentCode = rs.getString("component_code");
                    item.componentName = rs.getString("component_name");
                    item.spec = rs.getString("spec");
                    item.material = rs.getString("material");
                    item.unit = rs.getString("unit");
                    item.totalQty = rs.getDouble("total_qty");
                    item.stockQty = rs.getDouble("stock_qty");
                    item.deductedQty = rs.getDouble("deducted_qty");
                    item.shortageQty = rs.getDouble("shortage_qty");
                    item.stockRemark = rs.getString("stock_remark");
                    items.add(item);
                }
            }
        }
        return items;
    }

    public List<OrderPick> getOrderPicks(long orderId) throws SQLException {
        List<OrderPick> picks = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT component_id, quantity FROM bom_order_pick WHERE order_id = ? ORDER BY id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    picks.add(new OrderPick(rs.getLong("component_id"), rs.getDouble("quantity")));
                }
            }
        }
        return picks;
    }

    public void deleteOrder(Long orderId) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_order WHERE id = ?")) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }
}
