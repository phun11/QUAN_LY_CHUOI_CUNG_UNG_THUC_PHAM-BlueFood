// ============================================================
// HoneyBee Trace PWA bootstrap
// ------------------------------------------------------------
// Đăng ký service worker để web có thể cài như app trên laptop/điện thoại.
// Lưu ý: service worker hoạt động tốt nhất trên HTTPS hoặc localhost.
// ============================================================
(function registerHoneyBeePwa() {
  if (!('serviceWorker' in navigator)) return;
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./service-worker.js')
      .catch(err => console.warn('Không đăng ký được service worker:', err));
  });
})();
