-- ============================================================
-- Simulated crash / rollback demo
-- ============================================================
-- Mục tiêu: chứng minh atomicity.
-- Nếu tạo lô hàng bị lỗi giữa chừng, ROLLBACK xóa toàn bộ dữ liệu dở dang.
-- ============================================================
DECLARE
    V_PRODUCT_ID NUMBER;
BEGIN
    INSERT INTO PRODUCTS(FARM_ID, BATCH_CODE, PRODUCT_NAME, CATEGORY, PRICE, QUANTITY, STATUS)
    VALUES(1, 'HB-CRASH-TEST', 'Lô crash test', 'Demo', 1, 1, 'CREATED')
    RETURNING PRODUCT_ID INTO V_PRODUCT_ID;

    INSERT INTO QR_CODES(PRODUCT_ID, QR_TOKEN, QR_SIGNATURE, QR_URL, STATUS)
    VALUES(V_PRODUCT_ID, 'HB-CRASH-TOKEN', 'SIG', 'demo-url', 'ACTIVE');

    -- Giả lập crash/lỗi trước khi ghi audit/status đầy đủ.
    RAISE_APPLICATION_ERROR(-20999, 'Simulated crash before commit');

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('Rollback done. No half-created batch should remain.');
END;
/
SELECT * FROM PRODUCTS WHERE BATCH_CODE = 'HB-CRASH-TEST';
SELECT * FROM QR_CODES WHERE QR_TOKEN = 'HB-CRASH-TOKEN';
