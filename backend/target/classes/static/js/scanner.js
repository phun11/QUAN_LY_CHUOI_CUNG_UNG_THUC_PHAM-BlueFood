// ============================================================
// Scanner page
// ------------------------------------------------------------
// Mobile-first: demo ưu tiên chọn ảnh QR hoặc token mẫu.
// Nếu QR chứa URL product-detail.html thì chuyển trực tiếp.
// Nếu QR chỉ chứa token thì gọi trace endpoint theo token.
// ============================================================
const fileInput = document.getElementById('qr-file');
const msg = document.getElementById('scan-message');

document.getElementById('mock-safe').onclick = () => {
  location.href = 'product-detail.html?token=HB-QR-RAU-001-SAFE&sig=DEMO_SIGNATURE_SAFE';
};
document.getElementById('mock-warn').onclick = () => {
  location.href = 'product-detail.html?token=HB-QR-XOAI-002-WARN&sig=DEMO_SIGNATURE_WARN';
};
document.getElementById('manual-go').onclick = () => {
  const token = document.getElementById('manual-token').value.trim();
  if (!token) return msg.textContent = 'Vui lòng nhập token.';
  location.href = `product-detail.html?token=${encodeURIComponent(token)}`;
};

fileInput?.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  msg.textContent = 'Đang đọc ảnh QR...';
  const img = new Image();
  img.onload = () => {
    const canvas = document.createElement('canvas');
    canvas.width = img.width; canvas.height = img.height;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(img, 0, 0);
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    if (typeof jsQR !== 'function') {
      msg.textContent = 'Không tải được thư viện đọc QR (jsQR). Bạn vẫn có thể dùng nút demo hoặc nhập token thủ công.';
      return;
    }
    const code = jsQR(imageData.data, imageData.width, imageData.height);
    if (!code) {
      msg.textContent = 'Không đọc được QR. Hãy chụp rõ hơn, tránh lóa sáng.';
      return;
    }
    const value = code.data;
    if (value.includes('product-detail.html') || value.startsWith('http')) {
      // QR tạo trên laptop đôi khi chứa localhost/127.0.0.1.
      // Khi quét bằng điện thoại, cần tự đổi sang host hiện tại để truy cập đúng server.
      try {
        const u = new URL(value, location.href);
        if (u.hostname === 'localhost' || u.hostname === '127.0.0.1') {
          u.hostname = location.hostname;
        }
        location.href = u.toString();
      } catch (_) {
        location.href = value;
      }
    } else {
      location.href = `product-detail.html?token=${encodeURIComponent(value)}`;
    }
  };
  img.src = URL.createObjectURL(file);
});
