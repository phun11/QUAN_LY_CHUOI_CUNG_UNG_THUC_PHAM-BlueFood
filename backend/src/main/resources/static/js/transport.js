// ============================================================
// HoneyBee Transport Dashboard
// ------------------------------------------------------------
// Vai trò TRANSPORTER:
// - Nhận hàng: READY_FOR_TRANSPORT -> IN_TRANSIT.
// - Giao hàng: IN_TRANSIT -> DELIVERED_TO_STORE.
// - Xem lại lịch sử vận chuyển đã ghi trong DB.
// ============================================================
const transportUser = normalizeUser(currentUser());

function statusBadge(s) {
  s = String(s || '');
  if (s === 'READY_FOR_TRANSPORT') return '<span class="hb-badge warning">📦 Chờ nhận hàng</span>';
  if (s === 'IN_TRANSIT') return '<span class="hb-badge">🚚 Đang vận chuyển</span>';
  if (s === 'DELIVERED_TO_STORE') return '<span class="hb-badge safe">🏪 Đã đến cửa hàng</span>';
  return `<span class="hb-badge">${escapeHtml(s)}</span>`;
}

function defaultPayload(row) {
  return {
    transporterId: transportUser.transporterId || row.TRANSPORTER_ID || 1,
    transportCompany: row.TRANSPORTER_NAME || 'BeeTruck Logistics',
    fromLocation: row.FARM_ADDRESS || 'Nông trại/kho HoneyBee',
    toLocation: row.STORE_ADDRESS || 'Cửa hàng nhận hàng',
    storageTemperature: 8,
    userId: transportUser.userId,
    note: 'Cập nhật bởi transporter dashboard.'
  };
}

async function pickup(productId) {
  const row = window.shipmentCache.find(x => Number(x.PRODUCT_ID) === Number(productId));
  const payload = defaultPayload(row || {});
  payload.note = prompt('Ghi chú nhận hàng:', 'Đã nhận hàng từ farm/kho, seal nguyên vẹn.') || payload.note;
  await apiPost(`/system/transport/shipments/${productId}/pickup`, payload);
  await loadTransport();
}

async function deliver(productId) {
  const row = window.shipmentCache.find(x => Number(x.PRODUCT_ID) === Number(productId));
  const payload = defaultPayload(row || {});
  payload.fromLocation = prompt('Điểm đi:', row.FARM_ADDRESS || 'Kho HoneyBee') || payload.fromLocation;
  payload.toLocation = prompt('Điểm đến/cửa hàng:', row.STORE_ADDRESS || 'Honey Mart Quận 1') || payload.toLocation;
  payload.storageTemperature = Number(prompt('Nhiệt độ bảo quản (°C):', '8') || 8);
  payload.note = prompt('Ghi chú giao hàng:', 'Đã giao tới cửa hàng, hàng nguyên vẹn.') || payload.note;
  await apiPost(`/system/transport/shipments/${productId}/deliver`, payload);
  await loadTransport();
}

async function loadTransport() {
  const tid = transportUser.transporterId || 1;
  const rows = await apiGet(`/system/transport/shipments/pending?transporterId=${tid}`);
  window.shipmentCache = rows;
  const el = document.getElementById('shipments');
  if (!rows.length) {
    el.innerHTML = '<div class="hb-empty">Không có lô nào đang chờ vận chuyển.</div>';
  } else {
    el.innerHTML = rows.map(r => {
      const trace = r.QR_TOKEN ? `product-detail.html?token=${encodeURIComponent(r.QR_TOKEN)}&sig=${encodeURIComponent(r.QR_SIGNATURE || '')}` : '#';
      return `<article class="hb-item-card">
        <div class="hb-item-top">
          <div>${statusBadge(r.STATUS)} <span class="hb-badge">${escapeHtml(r.BATCH_CODE)}</span></div>
          <b>${escapeHtml(r.STORE_NAME || '')}</b>
        </div>
        <h3>${escapeHtml(r.PRODUCT_NAME)}</h3>
        <div class="hb-kv">
          <div><span>Farm</span><span>${escapeHtml(r.FARM_NAME)}</span></div>
          <div><span>Điểm đi</span><span>${escapeHtml(r.FARM_ADDRESS)}</span></div>
          <div><span>Điểm đến</span><span>${escapeHtml(r.STORE_ADDRESS)}</span></div>
          <div><span>QR token</span><span>${escapeHtml(r.QR_TOKEN || '')}</span></div>
        </div>
        <div class="hb-actions">
          ${r.STATUS === 'READY_FOR_TRANSPORT' ? `<button class="hb-btn" onclick="pickup(${r.PRODUCT_ID})">Nhận hàng → đang vận chuyển</button>` : ''}
          ${r.STATUS === 'IN_TRANSIT' ? `<button class="hb-btn" onclick="deliver(${r.PRODUCT_ID})">Xác nhận đã đến cửa hàng</button>` : ''}
          <a class="hb-btn ghost" href="${trace}">Xem trace</a>
        </div>
      </article>`;
    }).join('');
  }

  const history = await apiGet(`/system/transport/shipments/history?transporterId=${tid}`);
  document.getElementById('transport-history').innerHTML = history.length ? history.map(h => `<div class="hb-history-row">
    <b>${escapeHtml(h.PRODUCT_NAME)}</b><br>
    <span class="hb-badge">${escapeHtml(h.STATUS)}</span>
    <p>${escapeHtml(h.FROM_LOCATION)} → ${escapeHtml(h.TO_LOCATION)}</p>
    <small>${fmtDate(h.TRANSPORT_TIME)} · ${escapeHtml(h.NOTE || '')}</small>
  </div>`).join('') : '<div class="hb-empty">Chưa có lịch sử vận chuyển.</div>';
}

loadTransport().catch(e => document.getElementById('shipments').innerHTML = `<p style="color:red">${escapeHtml(e.message)}</p>`);
