-- ============================================================
-- 10_cursor_reports.sql - Cursor/report demo for DBMS grading
-- ============================================================
-- File này dùng để minh họa CURSOR trong PL/SQL.
-- Cursor phù hợp khi cần duyệt nhiều dòng và xử lý từng dòng theo nghiệp vụ/report.
-- Trong hệ thống thật, report có thể thay bằng view/query; ở đồ án DBMS, cursor giúp chứng minh hiểu PL/SQL.
-- ============================================================

SET SERVEROUTPUT ON;

-- PRC_REPORT_QR_RISK_BY_CURSOR
-- Chức năng:
-- - Mở cursor lấy danh sách QR và sản phẩm tương ứng.
-- - Duyệt từng QR để tính risk bằng FN_QR_RISK_LEVEL.
-- - In ra báo cáo ngắn trên DBMS_OUTPUT.
-- Ý nghĩa:
-- - Admin có thể chạy để kiểm tra nhanh QR nào đang HIGH/CRITICAL.
-- - Minh họa cursor explicit: DECLARE cursor, FOR loop, gọi function trong từng vòng lặp.
CREATE OR REPLACE PROCEDURE PRC_REPORT_QR_RISK_BY_CURSOR
IS
    CURSOR C_QR IS
        SELECT Q.QR_TOKEN, Q.STATUS AS QR_STATUS, Q.SALE_STATUS, P.PRODUCT_ID, P.PRODUCT_NAME, P.STATUS AS PRODUCT_STATUS
        FROM QR_CODES Q
        JOIN PRODUCTS P ON P.PRODUCT_ID = Q.PRODUCT_ID
        ORDER BY P.PRODUCT_ID;
    V_RISK VARCHAR2(20);
BEGIN
    DBMS_OUTPUT.PUT_LINE('===== HONEYBEE QR RISK REPORT =====');

    FOR R IN C_QR LOOP
        V_RISK := FN_QR_RISK_LEVEL(R.QR_TOKEN);
        DBMS_OUTPUT.PUT_LINE(
            'PRODUCT_ID=' || R.PRODUCT_ID ||
            ' | PRODUCT=' || R.PRODUCT_NAME ||
            ' | QR=' || R.QR_TOKEN ||
            ' | QR_STATUS=' || R.QR_STATUS ||
            ' | SALE_STATUS=' || R.SALE_STATUS ||
            ' | PRODUCT_STATUS=' || R.PRODUCT_STATUS ||
            ' | RISK=' || V_RISK
        );
    END LOOP;
END;
/
