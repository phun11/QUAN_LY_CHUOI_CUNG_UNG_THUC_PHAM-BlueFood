-- ============================================================
-- 05_procedures.sql - PL/SQL business procedures
-- ============================================================
-- Đây là phần thể hiện DBMS xử lý nghiệp vụ, transaction và concurrency.
-- Nguyên tắc:
-- - Procedure KHÔNG tự COMMIT để service/transaction bên ngoài kiểm soát toàn bộ nghiệp vụ.
-- - Dùng SELECT ... FOR UPDATE khi cần khóa bi quan dòng dữ liệu quan trọng.
-- - Dùng VERSION_NO để kiểm tra optimistic locking, tránh lost update.
-- ============================================================

-- PRC_CHANGE_BATCH_STATUS
-- Chức năng:
-- - Đổi trạng thái lô hàng theo luồng hợp lệ trong STATUS_TRANSITIONS.
-- - Ghi BATCH_STATUS_HISTORY và AUDIT_LOGS.
-- Quản lý đồng thời:
-- - SELECT ... FOR UPDATE khóa dòng PRODUCTS theo PRODUCT_ID.
-- - Transaction khác muốn sửa cùng PRODUCT_ID phải chờ, tránh hai người đổi trạng thái cùng lúc.
-- Chống Lost Update:
-- - P_EXPECTED_VERSION là version người dùng đọc được trước khi sửa.
-- - Nếu VERSION_NO hiện tại khác P_EXPECTED_VERSION, nghĩa là đã có transaction khác sửa trước.
-- - Khi đó raise lỗi -20101 để backend báo người dùng tải lại dữ liệu.
-- Kiểm soát nghiệp vụ:
-- - Chỉ cho phép trạng thái đi từ FROM_STATUS sang TO_STATUS nếu role hợp lệ.
CREATE OR REPLACE PROCEDURE PRC_CHANGE_BATCH_STATUS(
    P_PRODUCT_ID IN NUMBER,
    P_NEW_STATUS IN VARCHAR2,
    P_USER_ID IN NUMBER,
    P_ROLE IN VARCHAR2,
    P_EXPECTED_VERSION IN NUMBER
)
IS
    V_OLD_STATUS PRODUCTS.STATUS%TYPE;
    V_VERSION PRODUCTS.VERSION_NO%TYPE;
    V_ALLOWED NUMBER;
BEGIN
    SELECT STATUS, VERSION_NO INTO V_OLD_STATUS, V_VERSION
    FROM PRODUCTS
    WHERE PRODUCT_ID = P_PRODUCT_ID
    FOR UPDATE;

    IF V_VERSION <> P_EXPECTED_VERSION THEN
        RAISE_APPLICATION_ERROR(-20101, 'Lost update detected: version is changed by another transaction.');
    END IF;

    SELECT COUNT(*) INTO V_ALLOWED
    FROM STATUS_TRANSITIONS
    WHERE NVL(FROM_STATUS, 'NULL') = NVL(V_OLD_STATUS, 'NULL')
      AND TO_STATUS = P_NEW_STATUS
      AND ALLOWED_ROLE = P_ROLE;

    IF V_ALLOWED = 0 THEN
        RAISE_APPLICATION_ERROR(-20102, 'Invalid status transition or role is not allowed.');
    END IF;

    UPDATE PRODUCTS
    SET STATUS = P_NEW_STATUS,
        VERSION_NO = VERSION_NO + 1
    WHERE PRODUCT_ID = P_PRODUCT_ID;

    INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE)
    VALUES(P_PRODUCT_ID, V_OLD_STATUS, P_NEW_STATUS, P_USER_ID, 'Changed by PRC_CHANGE_BATCH_STATUS');

    INSERT INTO AUDIT_LOGS(TABLE_NAME, RECORD_ID, ACTION_TYPE, OLD_DATA, NEW_DATA, PERFORMED_BY)
    VALUES('PRODUCTS', TO_CHAR(P_PRODUCT_ID), 'STATUS_CHANGE', V_OLD_STATUS, P_NEW_STATUS, 'USER#' || P_USER_ID);
END;
/

-- PRC_LOG_QR_SCAN
-- Chức năng:
-- - Ghi nhận mỗi lần khách hàng/cửa hàng quét QR.
-- - Tính mức rủi ro bằng FN_QR_RISK_LEVEL.
-- - Nếu risk HIGH/CRITICAL thì insert thêm QR_SECURITY_ALERTS.
-- Bảo mật QR:
-- - Token không tồn tại vẫn được ghi log INVALID_TOKEN để phát hiện QR giả.
-- - QR revoked/expired/sold/suspicious sẽ tạo cảnh báo cho admin.
-- Ý nghĩa:
-- - Scan log là nguồn dữ liệu để phát hiện QR bị copy, quét bất thường, quét sau khi đã bán/hết hạn.
CREATE OR REPLACE PROCEDURE PRC_LOG_QR_SCAN(
    P_QR_TOKEN IN VARCHAR2,
    P_SIGNATURE IN VARCHAR2,
    P_IP IN VARCHAR2,
    P_USER_AGENT IN VARCHAR2
)
IS
    V_PRODUCT_ID NUMBER;
    V_QR_STATUS QR_CODES.STATUS%TYPE;
    V_RISK VARCHAR2(20);
    V_MSG NVARCHAR2(1000);
BEGIN
    SELECT PRODUCT_ID, STATUS INTO V_PRODUCT_ID, V_QR_STATUS
    FROM QR_CODES
    WHERE QR_TOKEN = P_QR_TOKEN;

    V_RISK := FN_QR_RISK_LEVEL(P_QR_TOKEN);
    V_MSG := CASE WHEN V_RISK IN ('HIGH','CRITICAL') THEN 'QR has suspicious scan pattern or invalid state.' ELSE 'OK' END;

    INSERT INTO QR_SCAN_LOGS(QR_TOKEN, PRODUCT_ID, IP_ADDRESS, USER_AGENT, RESULT_STATUS, WARNING_LEVEL, WARNING_MESSAGE)
    VALUES(P_QR_TOKEN, V_PRODUCT_ID, P_IP, P_USER_AGENT,
           CASE WHEN V_QR_STATUS='REVOKED' THEN 'REVOKED_QR' WHEN V_QR_STATUS='EXPIRED' THEN 'EXPIRED_QR' ELSE 'SUCCESS' END,
           V_RISK, V_MSG);

    IF V_RISK IN ('HIGH','CRITICAL') THEN
        INSERT INTO QR_SECURITY_ALERTS(QR_TOKEN, PRODUCT_ID, ALERT_TYPE, ALERT_LEVEL, ALERT_MESSAGE)
        VALUES(P_QR_TOKEN, V_PRODUCT_ID, 'QR_SCAN_RISK', V_RISK, V_MSG);
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        INSERT INTO QR_SCAN_LOGS(QR_TOKEN, IP_ADDRESS, USER_AGENT, RESULT_STATUS, WARNING_LEVEL, WARNING_MESSAGE)
        VALUES(P_QR_TOKEN, P_IP, P_USER_AGENT, 'INVALID_TOKEN', 'CRITICAL', 'Token does not exist in HoneyBee Trace.');
END;
/

-- PRC_REVOKE_QR
-- Chức năng:
-- - Admin thu hồi QR khi phát hiện QR bị copy, lô hàng lỗi hoặc thông tin không đáng tin.
-- - Cập nhật QR_CODES.STATUS = REVOKED và ghi lý do thu hồi.
-- - Ghi AUDIT_LOGS để lưu người thao tác.
-- Ý nghĩa:
-- - Khách hàng quét QR đã revoke sẽ thấy cảnh báo nguy hiểm thay vì thông tin bình thường.
CREATE OR REPLACE PROCEDURE PRC_REVOKE_QR(
    P_QR_TOKEN IN VARCHAR2,
    P_REASON IN NVARCHAR2,
    P_ADMIN_ID IN NUMBER
)
IS
BEGIN
    UPDATE QR_CODES
    SET STATUS = 'REVOKED', REVOKED_AT = CURRENT_TIMESTAMP, REVOKED_REASON = P_REASON
    WHERE QR_TOKEN = P_QR_TOKEN;

    INSERT INTO AUDIT_LOGS(TABLE_NAME, RECORD_ID, ACTION_TYPE, NEW_DATA, PERFORMED_BY)
    VALUES('QR_CODES', P_QR_TOKEN, 'REVOKE', P_REASON, 'ADMIN#' || P_ADMIN_ID);
END;
/

-- PRC_MARK_QR_SALE_STATUS
-- Chức năng:
-- - Cửa hàng/POS đánh dấu QR là SOLD, RETURNED hoặc NOT_SOLD.
-- - Khi SOLD: QR_CODES.SALE_STATUS = SOLD, PRODUCTS.STATUS = SOLD_OUT.
-- - Khi RETURNED/NOT_SOLD: đưa sản phẩm về AVAILABLE_FOR_SALE.
-- Quản lý đồng thời:
-- - SELECT ... FOR UPDATE khóa dòng QR_CODES/PRODUCTS tương ứng.
-- - Tránh trường hợp hai máy POS cùng bán một QR trong cùng thời điểm.
-- Bảo mật QR:
-- - Nếu QR đã bán nhưng sau đó vẫn bị quét nhiều nơi, FN_QR_RISK_LEVEL trả HIGH.
CREATE OR REPLACE PROCEDURE PRC_MARK_QR_SALE_STATUS(
    P_QR_TOKEN IN VARCHAR2,
    P_STORE_ID IN NUMBER,
    P_USER_ID IN NUMBER,
    P_SALE_STATUS IN VARCHAR2,
    P_NOTE IN NVARCHAR2
)
IS
    V_PRODUCT_ID NUMBER;
    V_OLD_STATUS PRODUCTS.STATUS%TYPE;
BEGIN
    SELECT Q.PRODUCT_ID, P.STATUS INTO V_PRODUCT_ID, V_OLD_STATUS
    FROM QR_CODES Q JOIN PRODUCTS P ON Q.PRODUCT_ID = P.PRODUCT_ID
    WHERE Q.QR_TOKEN = P_QR_TOKEN
    FOR UPDATE;

    IF P_SALE_STATUS NOT IN ('NOT_SOLD','SOLD','RETURNED') THEN
        RAISE_APPLICATION_ERROR(-20201, 'Invalid QR sale status.');
    END IF;

    IF P_SALE_STATUS = 'SOLD' THEN
        UPDATE QR_CODES
        SET SALE_STATUS='SOLD', SOLD_AT=CURRENT_TIMESTAMP, SOLD_BY_STORE_ID=P_STORE_ID, SOLD_NOTE=P_NOTE
        WHERE QR_TOKEN=P_QR_TOKEN;

        UPDATE PRODUCTS SET STATUS='SOLD_OUT', VERSION_NO=VERSION_NO+1 WHERE PRODUCT_ID=V_PRODUCT_ID;

        INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE)
        VALUES(V_PRODUCT_ID, V_OLD_STATUS, 'SOLD_OUT', P_USER_ID, NVL(P_NOTE, 'POS/store marked QR as sold.'));
    ELSE
        UPDATE QR_CODES
        SET SALE_STATUS=P_SALE_STATUS, SOLD_AT=NULL, SOLD_BY_STORE_ID=P_STORE_ID, SOLD_NOTE=P_NOTE
        WHERE QR_TOKEN=P_QR_TOKEN;

        UPDATE PRODUCTS SET STATUS='AVAILABLE_FOR_SALE', VERSION_NO=VERSION_NO+1 WHERE PRODUCT_ID=V_PRODUCT_ID;

        INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_BY, NOTE)
        VALUES(V_PRODUCT_ID, V_OLD_STATUS, 'AVAILABLE_FOR_SALE', P_USER_ID, NVL(P_NOTE, 'Store reopened QR sale status.'));
    END IF;

    INSERT INTO AUDIT_LOGS(TABLE_NAME, RECORD_ID, ACTION_TYPE, OLD_DATA, NEW_DATA, PERFORMED_BY)
    VALUES('QR_CODES', P_QR_TOKEN, 'STATUS_CHANGE', V_OLD_STATUS, 'SALE_STATUS=' || P_SALE_STATUS, 'STORE#' || P_STORE_ID);
END;
/
