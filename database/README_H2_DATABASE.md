# HoneyBee Trace - H2 Database Version

Bản H2 dùng để chạy demo nhanh bằng database in-memory. H2 schema nằm trong `backend/src/main/resources/h2` và được Spring Boot tự nạp khi start backend.

Lưu ý: H2 không phải Oracle PL/SQL thật, nên trigger/procedure/cursor/concurrency nâng cao được trình bày đầy đủ trong bản `ORACLE_VERSION`. Bản H2 tập trung chạy nhanh luồng web, API, QR, farm, transport, store và admin.
