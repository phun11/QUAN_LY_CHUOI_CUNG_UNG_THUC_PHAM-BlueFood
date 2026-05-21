-- Execution plan: count scans today
-- Kỳ vọng: dùng IDX_SCAN_TOKEN_TIME để đếm scan nhanh theo token + thời gian.
EXPLAIN PLAN FOR
SELECT COUNT(*)
FROM QR_SCAN_LOGS
WHERE QR_TOKEN = 'HB-QR-RAU-001-SAFE'
  AND SCANNED_AT >= TRUNC(SYSDATE);
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
