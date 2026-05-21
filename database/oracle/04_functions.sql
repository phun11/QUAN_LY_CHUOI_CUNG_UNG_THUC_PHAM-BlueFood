-- ============================================================
-- 04_functions.sql - Business functions
-- ============================================================
-- Function dùng để đưa logic kiểm tra quan trọng vào DBMS.
-- Ưu điểm:
-- - Java/UI gọi lại được nhiều nơi.
-- - Logic đánh giá QR thống nhất, tránh mỗi màn hình tự tính một kiểu.
-- - Có thể dùng trong procedure, view, report hoặc câu SELECT kiểm thử.
-- ============================================================

-- FN_COUNT_QR_SCANS_TODAY(P_QR_TOKEN)
-- Chức năng:
-- - Đếm số lần một QR token được quét trong ngày hiện tại.
-- Ý nghĩa nghiệp vụ:
-- - QR thật thường có tần suất quét vừa phải.
-- - Nếu một token bị quét quá nhiều lần trong ngày, có thể QR đã bị copy/dán lên nhiều sản phẩm giả.
-- Dùng bởi:
-- - FN_QR_RISK_LEVEL để tăng mức cảnh báo khi scan-count vượt ngưỡng.
CREATE OR REPLACE FUNCTION FN_COUNT_QR_SCANS_TODAY(P_QR_TOKEN IN VARCHAR2)
RETURN NUMBER
IS
    V_COUNT NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_COUNT
    FROM QR_SCAN_LOGS
    WHERE QR_TOKEN = P_QR_TOKEN
      AND TRUNC(SCANNED_AT) = TRUNC(SYSDATE);
    RETURN V_COUNT;
END;
/

-- FN_IS_CERTIFICATE_VALID(P_PRODUCT_ID)
-- Chức năng:
-- - Kiểm tra sản phẩm/lô hàng có chứng nhận VALID và còn hạn hay không.
-- Trả về:
-- - 'VALID' nếu có ít nhất một chứng nhận hợp lệ.
-- - 'MISSING_OR_EXPIRED' nếu thiếu chứng nhận hoặc chứng nhận đã hết hạn.
-- Ý nghĩa nghiệp vụ:
-- - Trang khách hàng có thể hiển thị cảnh báo nếu chứng nhận không còn hợp lệ.
CREATE OR REPLACE FUNCTION FN_IS_CERTIFICATE_VALID(P_PRODUCT_ID IN NUMBER)
RETURN VARCHAR2
IS
    V_VALID NUMBER;
BEGIN
    SELECT COUNT(*) INTO V_VALID
    FROM CERTIFICATES
    WHERE PRODUCT_ID = P_PRODUCT_ID
      AND STATUS = 'VALID'
      AND (EXPIRED_DATE IS NULL OR EXPIRED_DATE >= TRUNC(SYSDATE));

    IF V_VALID > 0 THEN RETURN 'VALID';
    ELSE RETURN 'MISSING_OR_EXPIRED';
    END IF;
END;
/

-- FN_QR_RISK_LEVEL(P_QR_TOKEN)
-- Chức năng:
-- - Đánh giá mức rủi ro của QR dựa trên trạng thái QR, trạng thái bán, trạng thái sản phẩm và tần suất quét.
-- Trả về:
-- - LOW: QR bình thường.
-- - MEDIUM: QR có dấu hiệu cần theo dõi, ví dụ quét quá nhiều lần trong ngày.
-- - HIGH: QR/sản phẩm ở trạng thái dễ gây rủi ro như đã bán, hết hạn, sold out.
-- - CRITICAL: token không tồn tại hoặc QR đã bị revoke.
-- Ý nghĩa chống giả:
-- - QR có thể bị chụp/copy. Hệ thống không chỉ kiểm token tồn tại mà còn xét vòng đời QR và scan log.
CREATE OR REPLACE FUNCTION FN_QR_RISK_LEVEL(P_QR_TOKEN IN VARCHAR2)
RETURN VARCHAR2
IS
    V_STATUS QR_CODES.STATUS%TYPE;
    V_SALE_STATUS QR_CODES.SALE_STATUS%TYPE;
    V_SCANS NUMBER;
    V_PRODUCT_STATUS PRODUCTS.STATUS%TYPE;
BEGIN
    SELECT Q.STATUS, Q.SALE_STATUS, P.STATUS INTO V_STATUS, V_SALE_STATUS, V_PRODUCT_STATUS
    FROM QR_CODES Q JOIN PRODUCTS P ON Q.PRODUCT_ID = P.PRODUCT_ID
    WHERE Q.QR_TOKEN = P_QR_TOKEN;

    V_SCANS := FN_COUNT_QR_SCANS_TODAY(P_QR_TOKEN);

    IF V_STATUS = 'REVOKED' THEN RETURN 'CRITICAL'; END IF;
    IF V_STATUS = 'SUSPICIOUS' THEN RETURN 'HIGH'; END IF;
    IF V_SALE_STATUS = 'SOLD' THEN RETURN 'HIGH'; END IF;
    IF V_PRODUCT_STATUS IN ('SOLD_OUT','EXPIRED','REVOKED') THEN RETURN 'HIGH'; END IF;
    IF V_SCANS >= 50 THEN RETURN 'MEDIUM'; END IF;
    RETURN 'LOW';
EXCEPTION
    WHEN NO_DATA_FOUND THEN RETURN 'CRITICAL';
END;
/
