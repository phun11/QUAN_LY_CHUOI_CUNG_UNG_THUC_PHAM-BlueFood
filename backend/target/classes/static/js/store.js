// ============================================================
// Store Dashboard - QR sale status / POS-ready flow
// ------------------------------------------------------------
// Chức năng cốt lõi:
// - Cửa hàng đánh dấu QR/lô đã bán hay chưa.
// - Nếu QR đã SOLD mà sau này bị quét lại, Trace API sẽ cảnh báo khách hàng.
// - Trong tương lai, máy POS có thể gọi cùng API này sau khi thanh toán.
// ============================================================

const listEl = document.getElementById('store-list');
const formEl = document.getElementById('sale-form');
const msgEl = document.getElementById('sale-message');
const refreshBtn = document.getElementById('refresh-btn');

function saleBadge(status) {
  const s = String(status || 'NOT_SOLD');
  if (s === 'SOLD') return '<span class="hb-badge danger">🏷️ Đã bán</span>';
  if (s === 'RETURNED') return '<span class="hb-badge warning">↩️ Hoàn trả</span>';
  return '<span class="hb-badge safe">🛒 Chưa bán</span>';
}

function productStatusBadge(status) {
  const s = String(status || 'UNKNOWN');
  const cls = s === 'SOLD_OUT' || s === 'REVOKED' ? 'danger' : (s === 'IN_TRANSIT' ? 'warning' : 'safe');
  return `<span class="hb-badge ${cls}">📦 ${escapeHtml(s)}</span>`;
}

function fillToken(token) {
  formEl.qrToken.value = token;
  msgEl.textContent = 'Đã chọn QR: ' + token;
  formEl.qrToken.focus();
}

async function loadStoreProducts() {
  listEl.innerHTML = '<p>Đang tải dữ liệu cửa hàng...</p>';
  const rows = await apiGet('/system/store/products?storeId=1');
  if (!rows.length) {
    listEl.innerHTML = '<p>Chưa có lô hàng tại cửa hàng.</p>';
    return;
  }

  listEl.innerHTML = rows.map(r => `
    <article class="hb-card" style="box-shadow:none;margin:12px 0;background:#fffdf7">
      <div style="display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap">
        <div>
          ${saleBadge(r.SALE_STATUS)} ${productStatusBadge(r.PRODUCT_STATUS)}
          <h3 style="margin:10px 0 6px">${escapeHtml(r.PRODUCT_NAME)}</h3>
          <p style="margin:0;color:var(--hb-muted)">${escapeHtml(r.BATCH_CODE)} · ${escapeHtml(r.STORE_NAME || 'Chưa gắn cửa hàng')}</p>
          <p style="margin:8px 0 0;color:var(--hb-muted);font-size:.92rem">QR: <b>${escapeHtml(r.QR_TOKEN)}</b></p>
          ${r.SOLD_AT ? `<p style="margin:6px 0 0;color:var(--hb-muted);font-size:.92rem">Đã bán lúc: ${fmtDate(r.SOLD_AT)}</p>` : ''}
          ${r.SOLD_NOTE ? `<p style="margin:6px 0 0;color:var(--hb-muted);font-size:.92rem">Ghi chú: ${escapeHtml(r.SOLD_NOTE)}</p>` : ''}
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <button class="hb-btn ghost" onclick="fillToken('${escapeHtml(r.QR_TOKEN)}')">Chọn QR</button>
          <a class="hb-btn ghost" href="product-detail.html?token=${encodeURIComponent(r.QR_TOKEN)}">Xem trang khách</a>
        </div>
      </div>
    </article>`).join('');
}

formEl.addEventListener('submit', async (e) => {
  e.preventDefault();
  msgEl.textContent = 'Đang lưu...';
  const body = {
    qrToken: formEl.qrToken.value.trim(),
    storeId: Number(formEl.storeId.value || 1),
    userId: Number(formEl.userId.value || 4),
    saleStatus: formEl.saleStatus.value,
    note: formEl.note.value.trim()
  };
  try {
    await apiPost('/system/store/sale-status', body);
    msgEl.textContent = '✅ Đã cập nhật trạng thái bán của QR. Quét lại QR để kiểm tra cảnh báo.';
    await loadStoreProducts();
  } catch (err) {
    msgEl.textContent = '❌ ' + err.message;
  }
});

refreshBtn.addEventListener('click', loadStoreProducts);
loadStoreProducts().catch(e => listEl.innerHTML = `<p style="color:var(--hb-red)">${escapeHtml(e.message)}</p>`);
