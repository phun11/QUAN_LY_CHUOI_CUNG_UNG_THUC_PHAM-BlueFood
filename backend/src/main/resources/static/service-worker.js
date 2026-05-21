// ============================================================
// HoneyBee Trace Service Worker
// ------------------------------------------------------------
// Cache giao diện tĩnh để PWA mở nhanh hơn.
// Không cache API vì dữ liệu QR, trạng thái đã bán và cảnh báo phải luôn mới.
// ============================================================
const CACHE_NAME = 'honeybee-trace-v20260517-roleflow-fix';
const STATIC_ASSETS = [
  './', './index.html', './login.html', './scanner.html', './product-detail.html', './products.html', './admin.html', './farm-management.html', './transport.html', './store.html',
  './css/honeybee.css',
  './js/api.js', './js/pwa.js', './js/scanner.js', './js/detail.js', './js/login.js', './js/products.js', './js/admin.js', './js/farms.js', './js/transport.js', './js/store.js',
  './assets/default.svg', './assets/veg.svg', './assets/mango.svg', './assets/tomato.svg',
  './offline.html'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(STATIC_ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.pathname.includes('/api/') || url.pathname.includes('/h2-console')) return;
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request).then(cached => cached || caches.match('./offline.html')))
  );
});
