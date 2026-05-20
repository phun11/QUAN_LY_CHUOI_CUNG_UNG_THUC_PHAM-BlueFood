package com.example.agritrace.repository;

import com.example.agritrace.dto.ProductRequest;
import com.example.agritrace.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Repository quản lý lô sản phẩm.
 *
 * DBMS note:
 * - createByProcedure mô phỏng một nghiệp vụ nguyên tử: tạo lô + origin + status history + audit.
 * - updateStatusWithVersion dùng VERSION_NO để chống Lost Update.
 */
@Repository
public class ProductRepository {
    private final JdbcTemplate jdbc;
    private final AuditRepository audit;

    public ProductRepository(JdbcTemplate jdbc, AuditRepository audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private final RowMapper<Product> productMapper = (rs, i) -> {
        Product p = new Product();
        p.productId = rs.getLong("PRODUCT_ID");
        p.farmId = rs.getLong("FARM_ID");
        p.productName = rs.getString("PRODUCT_NAME");
        p.category = rs.getString("CATEGORY");
        p.description = rs.getString("DESCRIPTION");
        p.price = rs.getBigDecimal("PRICE");
        p.status = rs.getString("STATUS");
        p.imageUrl = rs.getString("IMAGE_URL");
        return p;
    };

    public List<Product> findAll() {
        return jdbc.query("SELECT * FROM PRODUCTS ORDER BY PRODUCT_ID DESC", productMapper);
    }

    public Product findById(Long id) {
        return jdbc.queryForObject("SELECT * FROM PRODUCTS WHERE PRODUCT_ID = ?", productMapper, id);
    }

    /** Tạo lô hàng theo transaction để tránh dữ liệu dở dang khi lỗi giữa chừng. */
    @Transactional
    public Long createByProcedure(ProductRequest r) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO PRODUCTS(FARM_ID, BATCH_CODE, PRODUCT_NAME, CATEGORY, DESCRIPTION, QUALITY_SUMMARY, PRICE, QUANTITY, UNIT, IMAGE_URL, STATUS) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, r.farmId);
            ps.setString(2, "HB-BATCH-" + System.currentTimeMillis());
            ps.setString(3, r.productName);
            ps.setString(4, r.category);
            ps.setString(5, r.description);
            ps.setString(6, "Lô hàng được tạo trong hệ thống HoneyBee Trace.");
            ps.setBigDecimal(7, r.price);
            ps.setBigDecimal(8, java.math.BigDecimal.ZERO);
            ps.setString(9, "kg");
            ps.setString(10, r.imageUrl);
            ps.setString(11, "CREATED");
            return ps;
        }, keyHolder);

        Long productId = keyHolder.getKey().longValue();
        jdbc.update("INSERT INTO PRODUCT_ORIGIN(PRODUCT_ID, CULTIVATION_PLACE, SOWING_DATE, HARVEST_DATE, EXPIRED_DATE, PRODUCTION_PROCESS) VALUES(?,?,?,?,?,?)",
                productId, r.cultivationPlace,
                r.sowingDate == null ? null : java.sql.Date.valueOf(r.sowingDate),
                r.harvestDate == null ? null : java.sql.Date.valueOf(r.harvestDate),
                r.harvestDate == null ? null : java.sql.Date.valueOf(r.harvestDate.plusDays(5)),
                r.productionProcess);
        jdbc.update("INSERT INTO PRODUCT_UPDATES(PRODUCT_ID, UPDATE_TITLE, UPDATE_CONTENT, UPDATED_BY) VALUES(?,?,?,?)",
                productId, "Tạo lô hàng", "Lô hàng được tạo và chờ xác nhận chứng chỉ/QR.", "system");
        jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)",
                productId, null, "CREATED", null, "Tạo lô hàng mới");
        // QR được sinh ngay khi farm tạo lô để đảm bảo 100% lô hàng có QR truy xuất.
        // Demo signature dùng chuỗi cố định theo token; bản production sẽ dùng HMAC-SHA256 secret key.
        String token = "HB-QR-" + productId + "-" + Long.toHexString(System.currentTimeMillis()).toUpperCase();
        String signature = "DEMO_SIGNATURE_" + productId;
        String qrUrl = "http://localhost:8080/product-detail.html?token=" + token + "&sig=" + signature;
        jdbc.update("INSERT INTO QR_CODES(PRODUCT_ID, QR_TOKEN, QR_SIGNATURE, QR_URL, QR_IMAGE_URL, STATUS, SALE_STATUS) VALUES(?,?,?,?,?,?,?)",
                productId, token, signature, qrUrl, "/api/qr/product/" + productId + "/image", "ACTIVE", "NOT_SOLD");

        audit.log("PRODUCTS", String.valueOf(productId), "INSERT", null, r.productName, "farm#" + r.farmId, null);
        audit.log("QR_CODES", token, "INSERT", null, "Tạo QR cho lô hàng mới", "system", null);
        return productId;
    }

    /**
     * Danh sách lô theo farm để farmer chỉ thao tác trên dữ liệu của mình.
     * Nếu farmId null, trả toàn bộ để admin kiểm tra.
     */
    public List<Map<String, Object>> findByFarm(Long farmId) {
        if (farmId == null) {
            return jdbc.queryForList("""
                    SELECT p.*, f.FARM_NAME, q.QR_TOKEN, q.QR_SIGNATURE, q.SALE_STATUS, q.STATUS AS QR_STATUS
                    FROM PRODUCTS p
                    JOIN FARMS f ON p.FARM_ID=f.FARM_ID
                    LEFT JOIN QR_CODES q ON p.PRODUCT_ID=q.PRODUCT_ID
                    ORDER BY p.PRODUCT_ID DESC
                    """);
        }
        return jdbc.queryForList("""
                SELECT p.*, f.FARM_NAME, q.QR_TOKEN, q.QR_SIGNATURE, q.SALE_STATUS, q.STATUS AS QR_STATUS
                FROM PRODUCTS p
                JOIN FARMS f ON p.FARM_ID=f.FARM_ID
                LEFT JOIN QR_CODES q ON p.PRODUCT_ID=q.PRODUCT_ID
                WHERE p.FARM_ID=?
                ORDER BY p.PRODUCT_ID DESC
                """, farmId);
    }

    /**
     * Farmer chuyển lô sang READY_FOR_TRANSPORT sau khi đã nhập đủ thông tin nguồn gốc.
     * Đây là điểm bàn giao dữ liệu từ farm sang đơn vị vận chuyển.
     */
    @Transactional
    public void markReadyForTransport(Long productId, Long userId, String note) {
        String oldStatus = jdbc.queryForObject("SELECT STATUS FROM PRODUCTS WHERE PRODUCT_ID=?", String.class, productId);
        if (!("CREATED".equals(oldStatus) || "FARM_CONFIRMED".equals(oldStatus))) {
            throw new IllegalArgumentException("Chỉ lô CREATED/FARM_CONFIRMED mới được chuyển sang chờ vận chuyển.");
        }
        String newStatus = "READY_FOR_TRANSPORT";
        jdbc.update("UPDATE PRODUCTS SET STATUS=?, VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", newStatus, productId);
        jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)",
                productId, oldStatus, newStatus, userId, note == null ? "Farm xác nhận lô sẵn sàng vận chuyển." : note);
        audit.log("PRODUCTS", String.valueOf(productId), "STATUS_CHANGE", oldStatus, newStatus, "user#" + userId, null);
    }

    public void update(Long id, ProductRequest r) {
        jdbc.update("UPDATE PRODUCTS SET FARM_ID=?, PRODUCT_NAME=?, CATEGORY=?, DESCRIPTION=?, PRICE=?, IMAGE_URL=?, VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?",
                r.farmId, r.productName, r.category, r.description, r.price, r.imageUrl, id);
        jdbc.update("UPDATE PRODUCT_ORIGIN SET CULTIVATION_PLACE=?, SOWING_DATE=?, HARVEST_DATE=?, PRODUCTION_PROCESS=?, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?",
                r.cultivationPlace,
                r.sowingDate == null ? null : java.sql.Date.valueOf(r.sowingDate),
                r.harvestDate == null ? null : java.sql.Date.valueOf(r.harvestDate),
                r.productionProcess, id);
        audit.log("PRODUCTS", String.valueOf(id), "UPDATE", null, r.productName, "system", null);
    }

    /** Xóa mềm để không mất lịch sử truy xuất. */
    public void delete(Long id) {
        jdbc.update("UPDATE PRODUCTS SET STATUS='CANCELLED', VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", id);
        audit.log("PRODUCTS", String.valueOf(id), "UPDATE", null, "CANCELLED", "system", null);
    }
}
