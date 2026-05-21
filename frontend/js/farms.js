// ============================================================
// HoneyBee Farm Dashboard
// ------------------------------------------------------------
// Vai trò FARM:
// - Tạo sản phẩm/lô hàng mới.
// - Nhập thông tin nguồn gốc, ngày thu hoạch, quy trình canh tác.
// - Chuyển lô sang READY_FOR_TRANSPORT để bên vận chuyển nhận hàng.
// ============================================================
const farmUser = normalizeUser(currentUser());

function farmBadge(status) {
  const s = String(status || 'CREATED');
  if (s === 'READY_FOR_TRANSPORT') return '<span class="hb-badge warning">🚚 Chờ vận chuyển</span>';
  if (s === 'IN_TRANSIT') return '<span class="hb-badge">🚚 Đang vận chuyển</span>';
  if (s === 'AVAILABLE_FOR_SALE') return '<span class="hb-badge safe">✅ Đang bán</span>';
  return `<span class="hb-badge">📦 ${escapeHtml(s)}</span>`;
}

async function loadFarmProducts() {
  const el = document.getElementById('farm-products');
  const farmId = farmUser.role === 'FARMER' ? farmUser.farmId : '';
  const rows = await apiGet(`/system/farm/products${farmId ? `?farmId=${farmId}` : ''}`);
  if (!rows.length) {
    el.innerHTML = '<div class="hb-empty">Chưa có lô hàng nào. Hãy tạo lô mới ở form bên trái.</div>';
    return;
  }
  el.innerHTML = rows.map(r => {
    const trace = r.QR_TOKEN ? `product-detail.html?token=${encodeURIComponent(r.QR_TOKEN)}&sig=${encodeURIComponent(r.QR_SIGNATURE || '')}` : '#';
    const canReady = ['CREATED', 'FARM_CONFIRMED'].includes(String(r.STATUS));
    return `<article class="hb-item-card">
      <div class="hb-item-top">
        <div>${farmBadge(r.STATUS)} <span class="hb-badge">${escapeHtml(r.BATCH_CODE || 'Batch mới')}</span></div>
        <b>${escapeHtml(r.FARM_NAME)}</b>
      </div>
      <h3>${escapeHtml(r.PRODUCT_NAME)}</h3>
      <div class="hb-kv">
        <div><span>Loại</span><span>${escapeHtml(r.CATEGORY)}</span></div>
        <div><span>QR token</span><span>${escapeHtml(r.QR_TOKEN || 'Đang tạo')}</span></div>
        <div><span>Trạng thái</span><span>${escapeHtml(r.STATUS || 'CREATED')}</span></div>
      </div>
      <div class="hb-actions">
        <a class="hb-btn ghost" href="${trace}">Xem trace/QR</a>
        ${canReady ? `<button class="hb-btn" onclick="markReady(${r.PRODUCT_ID})">Chuyển sang chờ vận chuyển</button>` : ''}
      </div>
    </article>`;
  }).join('');
}

async function markReady(productId) {
  if (!confirm('Xác nhận lô này đã đủ thông tin và sẵn sàng bàn giao vận chuyển?')) return;
  await apiPost(`/system/farm/products/${productId}/ready-for-transport`, {
    userId: farmUser.userId,
    note: 'Farm xác nhận lô hàng sẵn sàng bàn giao cho vận chuyển.'
  });
  document.getElementById('farm-msg').textContent = 'Đã chuyển lô sang trạng thái chờ vận chuyển.';
  await loadFarmProducts();
}

async function createBatch(e) {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target).entries());
  data.farmId = farmUser.role === 'FARMER' ? farmUser.farmId : 1;
  data.price = Number(data.price || 0);
  Object.keys(data).forEach(k => { if (data[k] === '') data[k] = null; });
  const result = await apiPost('/system/farm/products', data);
  document.getElementById('farm-msg').textContent = `Đã tạo lô #${result.productId} và sinh QR truy xuất.`;
  e.target.reset();
  await loadFarmProducts();
}

document.getElementById('batch-form').addEventListener('submit', e => createBatch(e).catch(err => alert(err.message)));
loadFarmProducts().catch(e => document.getElementById('farm-products').innerHTML = `<p style="color:red">${escapeHtml(e.message)}</p>`);
