-- Read only transaction demo
-- Phù hợp khi xuất báo cáo audit/trace; đảm bảo không thay đổi dữ liệu trong transaction.
SET TRANSACTION READ ONLY;
SELECT * FROM VW_PUBLIC_TRACE WHERE PRODUCT_ID = 1;
SELECT * FROM AUDIT_LOGS WHERE RECORD_ID = '1';
COMMIT;
