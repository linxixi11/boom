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

    public long saveOrder(String projectName, List<BomSummaryRow> rows) throws SQLException {
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

            // 自动扣减库存：根据每行的 deductedQty 减少对应组件的 stock_qty
            try (PreparedStatement deductPs = conn.prepareStatement(
                    "UPDATE component SET stock_qty = GREATEST(0, stock_qty - ?) WHERE id = ?")) {
                for (BomSummaryRow row : rows) {
                    if (row.deductedQty > 0 && row.componentId != null) {
                        deductPs.setDouble(1, row.deductedQty);
                        deductPs.setLong(2, row.componentId);
                        deductPs.addBatch();
                    }
                }
                deductPs.executeBatch();
            }

            conn.commit();
            return orderId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
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

    public void deleteOrder(Long orderId) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bom_order WHERE id = ?")) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }
}
