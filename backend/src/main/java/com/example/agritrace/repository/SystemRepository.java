package com.example.agritrace.repository;

import com.example.agritrace.dto.CertificateRequest;
import com.example.agritrace.dto.StoreReceiveRequest;
import com.example.agritrace.dto.StoreSaleRequest;
import com.example.agritrace.dto.TransportRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.Map;

/**
 * Repository cho nghiệp vụ vận hành hệ thống theo role.
 *
 * DBMS note:
 * - Các thao tác vận chuyển/cửa hàng cập nhật nhiều bảng nên được đặt trong transaction.
 * - Mỗi thay đổi trạng thái đều ghi BATCH_STATUS_HISTORY và AUDIT_LOGS.
 */
@Repository
public class SystemRepository {
    private final JdbcTemplate jdbc;
    private final AuditRepository audit;

    public SystemRepository(JdbcTemplate jdbc, AuditRepository audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Số liệu dashboard cho admin HoneyBee. */
    public Map<String, Object> dashboard() {
        return Map.of(
                "products", jdbc.queryForObject("SELECT COUNT(*) FROM PRODUCTS", Long.class),
                "farms", jdbc.queryForObject("SELECT COUNT(*) FROM FARMS", Long.class),
                "transporters", jdbc.queryForObject("SELECT COUNT(*) FROM TRANSPORTERS", Long.class),
                "stores", jdbc.queryForObject("SELECT COUNT(*) FROM STORES", Long.class),
                "scanLogs", jdbc.queryForObject("SELECT COUNT(*) FROM QR_SCAN_LOGS", Long.class),
                "securityAlerts", jdbc.queryForObject("SELECT COUNT(*) FROM QR_SECURITY_ALERTS WHERE RESOLVED_STATUS='OPEN'", Long.class),
                "auditLogs", jdbc.queryForObject("SELECT COUNT(*) FROM AUDIT_LOGS", Long.class)
        );
    }

    public List<Map<String, Object>> users() { return jdbc.queryForList("SELECT USER_ID, USERNAME, FULL_NAME, ROLE, FARM_ID, TRANSPORTER_ID, STORE_ID, STATUS FROM USERS ORDER BY USER_ID"); }
    public List<Map<String, Object>> transporters() { return jdbc.queryForList("SELECT * FROM TRANSPORTERS ORDER BY TRANSPORTER_ID"); }
    public List<Map<String, Object>> stores() { return jdbc.queryForList("SELECT * FROM STORES ORDER BY STORE_ID"); }
    public List<Map<String, Object>> alerts() { return jdbc.queryForList("SELECT * FROM QR_SECURITY_ALERTS ORDER BY CREATED_AT DESC, ALERT_ID DESC FETCH FIRST 50 ROWS ONLY"); }

    /**
     * Danh sách QR/lô dùng cho admin, trang lô hàng và demo lấy ảnh QR.
     *
     * DB/QR note:
     * - QR_TOKEN là khóa định danh public, không dùng PRODUCT_ID tuần tự để tránh đoán mã.
     * - QR_SIGNATURE được backend kiểm tra khi khách quét QR.
     * - SALE_STATUS cho biết mã đã được POS/cửa hàng ghi nhận bán hay chưa.
     */
    public List<Map<String, Object>> qrCodes() {
        return jdbc.queryForList("""
                SELECT p.PRODUCT_ID, p.BATCH_CODE, p.PRODUCT_NAME, p.CATEGORY, p.PRICE,
                       p.STATUS AS PRODUCT_STATUS, p.IMAGE_URL,
                       q.QR_ID, q.QR_TOKEN, q.QR_SIGNATURE, q.QR_URL, q.QR_IMAGE_URL,
                       q.STATUS AS QR_STATUS, q.SALE_STATUS, q.SOLD_AT, q.SOLD_NOTE, q.CREATED_AT
                FROM QR_CODES q
                JOIN PRODUCTS p ON q.PRODUCT_ID = p.PRODUCT_ID
                ORDER BY p.PRODUCT_ID
                """);
    }
    public List<Map<String, Object>> certificates(Long productId) {
        if (productId == null) return jdbc.queryForList("SELECT * FROM CERTIFICATES ORDER BY CERTIFICATE_ID DESC");
        return jdbc.queryForList("SELECT * FROM CERTIFICATES WHERE PRODUCT_ID=? OR PRODUCT_ID IS NULL ORDER BY CERTIFICATE_ID DESC", productId);
    }


    /**
     * Danh sách lô/QR cho dashboard cửa hàng.
     *
     * DB/QR note:
     * - Join PRODUCTS + QR_CODES + DISTRIBUTION_HISTORY để cửa hàng thấy đúng QR của lô.
     * - SALE_STATUS nằm ở QR_CODES vì QR là đơn vị khách hàng quét lại sau khi mua.
     * - Nếu hệ thống POS tích hợp sau này, POS chỉ cần gọi API sale-status bằng qrToken.
     */
    public List<Map<String, Object>> storeProducts(Long storeId) {
        if (storeId == null) {
            return jdbc.queryForList("""
                    SELECT p.PRODUCT_ID, p.BATCH_CODE, p.PRODUCT_NAME, p.STATUS AS PRODUCT_STATUS,
                           q.QR_TOKEN, q.STATUS AS QR_STATUS, q.SALE_STATUS, q.SOLD_AT, q.SOLD_NOTE,
                           s.STORE_ID, s.STORE_NAME
                    FROM PRODUCTS p
                    JOIN QR_CODES q ON p.PRODUCT_ID=q.PRODUCT_ID
                    LEFT JOIN DISTRIBUTION_HISTORY d ON p.PRODUCT_ID=d.PRODUCT_ID
                    LEFT JOIN STORES s ON d.STORE_ID=s.STORE_ID
                    ORDER BY p.PRODUCT_ID DESC
                    """);
        }
        return jdbc.queryForList("""
                SELECT p.PRODUCT_ID, p.BATCH_CODE, p.PRODUCT_NAME, p.STATUS AS PRODUCT_STATUS,
                       q.QR_TOKEN, q.STATUS AS QR_STATUS, q.SALE_STATUS, q.SOLD_AT, q.SOLD_NOTE,
                       s.STORE_ID, s.STORE_NAME
                FROM PRODUCTS p
                JOIN QR_CODES q ON p.PRODUCT_ID=q.PRODUCT_ID
                LEFT JOIN DISTRIBUTION_HISTORY d ON p.PRODUCT_ID=d.PRODUCT_ID
                LEFT JOIN STORES s ON d.STORE_ID=s.STORE_ID
                WHERE s.STORE_ID=? OR s.STORE_ID IS NULL
                ORDER BY p.PRODUCT_ID DESC
                """, storeId);
    }

    /**
     * Store đánh dấu QR/lô đã bán, chưa bán hoặc hoàn trả.
     *
     * Cơ chế chống copy QR:
     * - Đếm lượt quét chỉ phát hiện bất thường gián tiếp.
     * - SALE_STATUS phát hiện trực tiếp hơn: nếu QR đã SOLD mà vẫn bị quét lại trên kệ/chợ,
     *   khách hàng sẽ thấy cảnh báo lô đã bán và nên kiểm tra tem/sản phẩm.
     *
     * Transaction note:
     * - Cập nhật QR_CODES, PRODUCTS, BATCH_STATUS_HISTORY, AUDIT_LOGS trong cùng transaction.
     * - Nếu lỗi ở bước nào, rollback toàn bộ để tránh QR báo SOLD nhưng lô chưa ghi lịch sử.
     */
    @Transactional
    public void markQrSaleStatus(StoreSaleRequest r) {
        String saleStatus = r.saleStatus == null ? "SOLD" : r.saleStatus.toUpperCase();
        if (!List.of("NOT_SOLD", "SOLD", "RETURNED").contains(saleStatus)) {
            throw new IllegalArgumentException("saleStatus must be NOT_SOLD, SOLD or RETURNED");
        }

        Map<String, Object> qr;
        if (r.qrToken != null && !r.qrToken.isBlank()) {
            qr = jdbc.queryForMap("SELECT * FROM QR_CODES WHERE QR_TOKEN=?", r.qrToken.trim());
        } else if (r.productId != null) {
            qr = jdbc.queryForMap("SELECT * FROM QR_CODES WHERE PRODUCT_ID=?", r.productId);
        } else {
            throw new IllegalArgumentException("Missing qrToken or productId");
        }

        Long productId = ((Number) qr.get("PRODUCT_ID")).longValue();
        String token = String.valueOf(qr.get("QR_TOKEN"));
        String oldProductStatus = jdbc.queryForObject("SELECT STATUS FROM PRODUCTS WHERE PRODUCT_ID=?", String.class, productId);

        if ("SOLD".equals(saleStatus)) {
            jdbc.update("UPDATE QR_CODES SET SALE_STATUS='SOLD', SOLD_AT=CURRENT_TIMESTAMP, SOLD_BY_STORE_ID=?, SOLD_NOTE=? WHERE QR_TOKEN=?",
                    r.storeId, r.note, token);
            jdbc.update("UPDATE PRODUCTS SET STATUS='SOLD_OUT', VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", productId);
            jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)",
                    productId, oldProductStatus, "SOLD_OUT", r.userId, r.note == null ? "Cửa hàng/POS đánh dấu QR đã bán." : r.note);
            audit.log("QR_CODES", token, "STATUS_CHANGE", oldProductStatus, "SALE_STATUS=SOLD", "store#" + r.storeId, null);
        } else {
            jdbc.update("UPDATE QR_CODES SET SALE_STATUS=?, SOLD_AT=NULL, SOLD_BY_STORE_ID=?, SOLD_NOTE=? WHERE QR_TOKEN=?",
                    saleStatus, r.storeId, r.note, token);
            String newProductStatus = "RETURNED".equals(saleStatus) ? "STORE_RECEIVED" : "AVAILABLE_FOR_SALE";
            jdbc.update("UPDATE PRODUCTS SET STATUS=?, VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", newProductStatus, productId);
            jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)",
                    productId, oldProductStatus, newProductStatus, r.userId, r.note == null ? "Cửa hàng cập nhật lại trạng thái bán của QR." : r.note);
            audit.log("QR_CODES", token, "STATUS_CHANGE", oldProductStatus, "SALE_STATUS=" + saleStatus, "store#" + r.storeId, null);
        }
    }

    /** Thêm chứng chỉ; audit giúp chứng minh chứng chỉ được tạo khi nào, bởi ai. */
    public void addCertificate(CertificateRequest r) {
        jdbc.update("INSERT INTO CERTIFICATES(FARM_ID, PRODUCT_ID, CERTIFICATE_NAME, ISSUED_BY, ISSUE_DATE, EXPIRED_DATE, FILE_URL, STATUS) VALUES(?,?,?,?,?,?,?,?)",
                r.farmId, r.productId, r.certificateName, r.issuedBy,
                r.issueDate == null ? null : Date.valueOf(r.issueDate),
                r.expiredDate == null ? null : Date.valueOf(r.expiredDate),
                r.fileUrl, r.status == null ? "VALID" : r.status);
        audit.log("CERTIFICATES", String.valueOf(r.productId), "INSERT", null, r.certificateName, "system", null);
    }

    /** Transporter thêm một chặng vận chuyển và cập nhật trạng thái lô trong cùng transaction. */
    @Transactional
    public void addTransport(TransportRequest r) {
        jdbc.update("INSERT INTO TRANSPORT_HISTORY(PRODUCT_ID, TRANSPORTER_ID, TRANSPORT_COMPANY, FROM_LOCATION, TO_LOCATION, STORAGE_TEMPERATURE, STATUS, NOTE, CREATED_BY) VALUES(?,?,?,?,?,?,?,?,?)",
                r.productId, r.transporterId, r.transportCompany, r.fromLocation, r.toLocation, r.storageTemperature,
                r.status == null ? "IN_TRANSIT" : r.status, r.note, r.userId);
        String oldStatus = jdbc.queryForObject("SELECT STATUS FROM PRODUCTS WHERE PRODUCT_ID=?", String.class, r.productId);
        String newStatus = ("DELIVERED".equals(r.status) || "DELIVERED_TO_STORE".equals(r.status)) ? "DELIVERED_TO_STORE" : "IN_TRANSIT";
        jdbc.update("UPDATE PRODUCTS SET STATUS=?, VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", newStatus, r.productId);
        jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)", r.productId, oldStatus, newStatus, r.userId, r.note);
        audit.log("TRANSPORT_HISTORY", String.valueOf(r.productId), "INSERT", null, r.fromLocation + " -> " + r.toLocation, "user#" + r.userId, null);
    }



    /**
     * Danh sách lô mà transporter cần xử lý.
     * - READY_FOR_TRANSPORT: farm đã tạo/xác nhận xong, chờ bên vận chuyển nhận hàng.
     * - IN_TRANSIT: transporter đang giữ hàng và cần xác nhận đã giao tới cửa hàng.
     */
    public List<Map<String, Object>> pendingShipments(Long transporterId) {
        return jdbc.queryForList("""
                SELECT p.PRODUCT_ID, p.BATCH_CODE, p.PRODUCT_NAME, p.CATEGORY, p.STATUS,
                       p.QUANTITY, p.UNIT, f.FARM_NAME, f.ADDRESS AS FARM_ADDRESS,
                       q.QR_TOKEN, q.QR_SIGNATURE,
                       t.TRANSPORTER_ID, t.TRANSPORTER_NAME,
                       COALESCE(s.STORE_ID, 1) AS STORE_ID,
                       COALESCE(s.STORE_NAME, 'Honey Mart Quận 1') AS STORE_NAME,
                       COALESCE(s.ADDRESS, 'Quận 1, TP.HCM') AS STORE_ADDRESS
                FROM PRODUCTS p
                JOIN FARMS f ON p.FARM_ID=f.FARM_ID
                LEFT JOIN QR_CODES q ON p.PRODUCT_ID=q.PRODUCT_ID
                LEFT JOIN TRANSPORTERS t ON (? IS NULL OR t.TRANSPORTER_ID=?)
                LEFT JOIN DISTRIBUTION_HISTORY d ON p.PRODUCT_ID=d.PRODUCT_ID
                LEFT JOIN STORES s ON d.STORE_ID=s.STORE_ID
                WHERE p.STATUS IN ('READY_FOR_TRANSPORT','IN_TRANSIT')
                ORDER BY CASE p.STATUS WHEN 'READY_FOR_TRANSPORT' THEN 1 ELSE 2 END, p.PRODUCT_ID DESC
                """, transporterId, transporterId);
    }

    /** Xem lịch sử vận chuyển đã lưu để transporter/admin kiểm tra lại dữ liệu đã ghi. */
    public List<Map<String, Object>> transportHistory(Long transporterId) {
        if (transporterId == null) {
            return jdbc.queryForList("""
                    SELECT th.*, p.BATCH_CODE, p.PRODUCT_NAME
                    FROM TRANSPORT_HISTORY th
                    JOIN PRODUCTS p ON th.PRODUCT_ID=p.PRODUCT_ID
                    ORDER BY th.TRANSPORT_TIME DESC, th.TRANSPORT_ID DESC
                    """);
        }
        return jdbc.queryForList("""
                SELECT th.*, p.BATCH_CODE, p.PRODUCT_NAME
                FROM TRANSPORT_HISTORY th
                JOIN PRODUCTS p ON th.PRODUCT_ID=p.PRODUCT_ID
                WHERE th.TRANSPORTER_ID=?
                ORDER BY th.TRANSPORT_TIME DESC, th.TRANSPORT_ID DESC
                """, transporterId);
    }

    /**
     * Transporter nhận hàng từ farm/kho.
     * DBMS note:
     * - SELECT FOR UPDATE khóa dòng lô hàng để tránh 2 transporter cùng nhận một lô.
     * - Chỉ cho phép nhận hàng khi trạng thái là READY_FOR_TRANSPORT.
     * - Ghi TRANSPORT_HISTORY + BATCH_STATUS_HISTORY + AUDIT_LOGS trong một transaction.
     */
    @Transactional
    public void pickupShipment(TransportRequest r) {
        String oldStatus = jdbc.queryForObject("SELECT STATUS FROM PRODUCTS WHERE PRODUCT_ID=? FOR UPDATE", String.class, r.productId);
        if (!"READY_FOR_TRANSPORT".equals(oldStatus)) {
            throw new IllegalArgumentException("Chỉ lô READY_FOR_TRANSPORT mới được nhận vận chuyển. Trạng thái hiện tại: " + oldStatus);
        }
        String company = r.transportCompany == null ? jdbc.queryForObject("SELECT TRANSPORTER_NAME FROM TRANSPORTERS WHERE TRANSPORTER_ID=?", String.class, r.transporterId) : r.transportCompany;
        jdbc.update("""
                INSERT INTO TRANSPORT_HISTORY(PRODUCT_ID, TRANSPORTER_ID, TRANSPORT_COMPANY, FROM_LOCATION, TO_LOCATION,
                                              STORAGE_TEMPERATURE, STATUS, NOTE, CREATED_BY)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, r.productId, r.transporterId, company, r.fromLocation, r.toLocation,
                r.storageTemperature, "IN_TRANSIT", r.note, r.userId);
        jdbc.update("UPDATE PRODUCTS SET STATUS='IN_TRANSIT', VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", r.productId);
        jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)",
                r.productId, oldStatus, "IN_TRANSIT", r.userId, r.note == null ? "Transporter nhận hàng và bắt đầu vận chuyển." : r.note);
        audit.log("TRANSPORT_HISTORY", String.valueOf(r.productId), "PICKUP", oldStatus, "IN_TRANSIT", "transport#" + r.transporterId, null);
    }

    /**
     * Transporter xác nhận đã giao lô tới cửa hàng.
     * Sau bước này store mới có quyền xác nhận nhận hàng/đưa lên bán.
     */
    @Transactional
    public void deliverShipment(TransportRequest r) {
        String oldStatus = jdbc.queryForObject("SELECT STATUS FROM PRODUCTS WHERE PRODUCT_ID=? FOR UPDATE", String.class, r.productId);
        if (!"IN_TRANSIT".equals(oldStatus)) {
            throw new IllegalArgumentException("Chỉ lô IN_TRANSIT mới được xác nhận đã đến cửa hàng. Trạng thái hiện tại: " + oldStatus);
        }
        String company = r.transportCompany == null ? jdbc.queryForObject("SELECT TRANSPORTER_NAME FROM TRANSPORTERS WHERE TRANSPORTER_ID=?", String.class, r.transporterId) : r.transportCompany;
        jdbc.update("""
                INSERT INTO TRANSPORT_HISTORY(PRODUCT_ID, TRANSPORTER_ID, TRANSPORT_COMPANY, FROM_LOCATION, TO_LOCATION,
                                              STORAGE_TEMPERATURE, STATUS, NOTE, CREATED_BY)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, r.productId, r.transporterId, company, r.fromLocation, r.toLocation,
                r.storageTemperature, "DELIVERED_TO_STORE", r.note, r.userId);
        jdbc.update("UPDATE PRODUCTS SET STATUS='DELIVERED_TO_STORE', VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", r.productId);
        jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)",
                r.productId, oldStatus, "DELIVERED_TO_STORE", r.userId, r.note == null ? "Transporter xác nhận lô đã đến cửa hàng." : r.note);
        audit.log("TRANSPORT_HISTORY", String.valueOf(r.productId), "DELIVER", oldStatus, "DELIVERED_TO_STORE", "transport#" + r.transporterId, null);
    }


    /** Store xác nhận nhận hàng; không sửa lịch sử cũ mà insert distribution history mới. */
    @Transactional
    public void confirmStoreReceive(StoreReceiveRequest r) {
        jdbc.update("INSERT INTO DISTRIBUTION_HISTORY(PRODUCT_ID, STORE_ID, RECEIVED_BY, QUANTITY_RECEIVED, STATUS, NOTE) VALUES(?,?,?,?,?,?)",
                r.productId, r.storeId, r.userId, r.quantityReceived, r.status == null ? "RECEIVED" : r.status, r.note);
        String oldStatus = jdbc.queryForObject("SELECT STATUS FROM PRODUCTS WHERE PRODUCT_ID=?", String.class, r.productId);
        String newStatus = "SOLD_OUT".equals(r.status) ? "SOLD_OUT" : ("REJECTED".equals(r.status) ? "REJECTED" : "STORE_RECEIVED");
        jdbc.update("UPDATE PRODUCTS SET STATUS=?, VERSION_NO=VERSION_NO+1, UPDATED_AT=CURRENT_TIMESTAMP WHERE PRODUCT_ID=?", newStatus, r.productId);
        jdbc.update("INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE) VALUES(?,?,?,?,?)", r.productId, oldStatus, newStatus, r.userId, r.note);
        audit.log("DISTRIBUTION_HISTORY", String.valueOf(r.productId), "INSERT", null, "store=" + r.storeId + ", status=" + newStatus, "user#" + r.userId, null);
    }
}
