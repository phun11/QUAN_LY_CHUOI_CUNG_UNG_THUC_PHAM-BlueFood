// ============================================================
// Product detail / Public QR verification page
// ------------------------------------------------------------
// UX chính: khách quét bằng điện thoại cần biết nhanh QR có đáng tin không.
// Vì vậy Trust Card luôn được render đầu tiên, sau đó mới tới thông tin chi tiết.
// ============================================================

const loadingEl = document.getElementById('loading');
const contentEl = document.getElementById('content');

function trustClass(level) {
  if (level === 'SAFE') return 'safe';
  if (level === 'WARNING') return 'warning';
  return 'danger';
}

function trustIcon(level) {
  if (level === 'SAFE') return '✅';
  if (level === 'WARNING') return '⚠️';
  return '🚨';
}

function certBadge(status) {
  const s = String(status || 'UNKNOWN');
  const cls = s === 'VALID' ? 'safe' : (s === 'EXPIRED' || s === 'REVOKED' ? 'danger' : 'warning');
  return `<span class="hb-badge ${cls}">🏅 ${escapeHtml(s)}</span>`;
}

function renderKv(label, value) {
  return `<div><span>${label}</span><span>${escapeHtml(value || 'Chưa có')}</span></div>`;
}

function renderTrace(data) {
  const info = data.info || {};
  const trust = data.trustStatus || { level: 'UNKNOWN', title: 'Không xác định', message: 'Không có dữ liệu.' };
  const rec = data.recommendation || {};
  const certificates = data.certificates || [];
  const journey = data.journey || [];
  const alerts = data.securityAlerts || [];

  const html = `
    <section class="hb-card hb-trust ${trustClass(trust.level)}">
      <span class="hb-badge ${trustClass(trust.level)}">${trustIcon(trust.level)} ${escapeHtml(trust.level)}</span>
      <h2 style="margin-top:12px">${escapeHtml(trust.title)}</h2>
      <p>${escapeHtml(trust.message)}</p>
      <div class="hb-kv">
        ${renderKv('Lượt quét hôm nay', trust.todayScanCount ?? 0)}
        ${renderKv('Tổng lượt quét', trust.totalScanCount ?? 0)}
        ${renderKv('Trạng thái bán QR', trust.qrSaleStatus || info.QR_SALE_STATUS || 'NOT_SOLD')}
        ${renderKv('Thời điểm bán', fmtDate(trust.soldAt || info.SOLD_AT))}
        ${renderKv('Khuyến nghị', rec.message || 'Chưa có')}
      </div>
    </section>

    <section class="hb-card">
      <div style="display:flex; gap:14px; align-items:center">
        <img src="${escapeHtml(info.IMAGE_URL || 'assets/default.svg')}" alt="product" style="width:82px;height:82px;border-radius:20px;background:#fff3c4;padding:10px" onerror="this.src='assets/default.svg'">
        <div>
          <span class="hb-badge">📦 ${escapeHtml(info.BATCH_CODE || 'Lô sản phẩm')}</span>
          <h2 style="margin:8px 0 4px">${escapeHtml(info.PRODUCT_NAME || 'Không có tên sản phẩm')}</h2>
          <p style="margin:0;color:var(--hb-muted)">${escapeHtml(info.QUALITY_SUMMARY || info.DESCRIPTION || '')}</p>
        </div>
      </div>
      <div class="hb-kv">
        ${renderKv('Trạng thái lô', info.PRODUCT_STATUS)}
        ${renderKv('Nông trại', info.FARM_NAME)}
        ${renderKv('Nơi trồng', info.CULTIVATION_PLACE)}
        ${renderKv('Ngày thu hoạch', fmtDate(info.HARVEST_DATE))}
        ${renderKv('Hạn sử dụng', fmtDate(info.EXPIRED_DATE))}
        ${renderKv('Độ tươi', info.FRESHNESS_STATUS)}
      </div>
    </section>

    <section class="hb-card">
      <h3>🐝 Hành trình sản phẩm</h3>
      <div class="hb-timeline">
        ${journey.length ? journey.map(j => `
          <div class="hb-step">
            <div class="hb-step-icon">${escapeHtml(j.icon || '•')}</div>
            <div><h4>${escapeHtml(j.title)}</h4><p>${escapeHtml(j.description)}</p><time>${fmtDate(j.time)}</time></div>
          </div>`).join('') : '<p>Chưa có dữ liệu hành trình.</p>'}
      </div>
    </section>

    <section class="hb-card">
      <h3>🏅 Chứng chỉ</h3>
      ${certificates.length ? certificates.map(c => `
        <div style="padding:12px 0;border-bottom:1px dashed var(--hb-border)">
          ${certBadge(c.STATUS)}
          <strong style="display:block;margin-top:8px">${escapeHtml(c.CERTIFICATE_NAME)}</strong>
          <small>${escapeHtml(c.ISSUED_BY || '')} · Hết hạn: ${fmtDate(c.EXPIRED_DATE)}</small>
        </div>`).join('') : '<p>Chưa có chứng chỉ công khai.</p>'}
    </section>

    <section class="hb-card">
      <h3>🛡️ Thông tin an toàn QR</h3>
      ${alerts.length ? alerts.map(a => `<p><span class="hb-badge warning">${escapeHtml(a.ALERT_LEVEL)}</span> ${escapeHtml(a.ALERT_MESSAGE)}</p>`).join('') : '<p>Không có cảnh báo bảo mật đang mở.</p>'}
      <p style="color:var(--hb-muted);font-size:.92rem">Lưu ý: QR tĩnh có thể bị chụp/in lại. Hệ thống kiểm tra thêm trạng thái đã bán/chưa bán để phát hiện trường hợp QR đã bán nhưng bị dán lại lên sản phẩm khác.</p>
    </section>
  `;
  contentEl.innerHTML = html;
}

async function init() {
  try {
    const token = getParam('token');
    const sig = getParam('sig');
    const id = getParam('id');
    let data;
    if (token) {
      data = await apiGet(`/trace/token/${encodeURIComponent(token)}${sig ? `?sig=${encodeURIComponent(sig)}` : ''}`);
    } else if (id) {
      data = await apiGet(`/trace/product/${encodeURIComponent(id)}`);
    } else {
      throw new Error('Thiếu token QR hoặc id sản phẩm.');
    }
    renderTrace(data);
  } catch (err) {
    contentEl.innerHTML = `<section class="hb-card hb-trust danger"><span class="hb-badge danger">❌ ERROR</span><h2>Không thể xác thực</h2><p>${escapeHtml(err.message)}</p><a class="hb-btn" href="scanner.html" style="margin-top:14px">Quét lại</a></section>`;
  } finally {
    loadingEl.style.display = 'none';
    contentEl.style.display = 'block';
  }
}

init();
