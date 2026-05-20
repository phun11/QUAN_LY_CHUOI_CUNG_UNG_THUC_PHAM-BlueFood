# Performance tuning notes

## Query 1: Trace by QR token
- Before index: QR_CODES có nguy cơ full table scan khi dữ liệu tăng.
- After `IDX_QR_TOKEN`: truy vấn token dùng unique scan, phù hợp mục tiêu < 2 giây.

## Query 2: Scan count by token/time
- Before composite index: đếm scan theo ngày dễ chậm khi QR_SCAN_LOGS lớn.
- After `IDX_SCAN_TOKEN_TIME`: giảm cost cho cảnh báo scan bất thường.

## Chỉ số báo cáo nên ghi
- Response time: thời gian API `/api/trace/token/{token}`.
- Throughput: số request scan/giây khi test bằng Postman/JMeter.
