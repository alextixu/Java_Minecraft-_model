// Mob Armory weapon admin (R6). Talks only to /api/v1 on the same server; no libraries, no outside requests.
'use strict';

const STAT_LABELS = {
  attack_damage: '攻擊力', movement_speed: '移動速度', armor: '護甲',
  attack_speed: '攻擊速度', max_health: '最大生命', crit_chance: '暴擊率', dodge_chance: '迴避率',
};
const SLOT_LABELS = { mainhand: '主手', offhand: '副手', head: '頭盔', chest: '胸甲', legs: '護腿', feet: '靴子' };
const STATUS_LABELS = { active: '生效中', pending_restart: '待重開遊戲', file_missing: '檔案已刪除，重開後消失' };
const BODY_LABELS = { zombie: '殭屍', husk: '屍殼', skeleton: '骷髏', stray: '流髑' };
const BEHAVIOR_LABELS = { wander: '閒晃', attack_players: '攻擊玩家' };

const state = { stats: [], looks: { items: [], textures: [] }, weapons: [], mobs: [], selected: null };
const token = readToken();

function readToken() {
  const fromUrl = new URLSearchParams(location.search).get('token');
  try {
    if (fromUrl) sessionStorage.setItem('mobarmory-token', fromUrl);
    return fromUrl || sessionStorage.getItem('mobarmory-token') || '';
  } catch (e) {
    return fromUrl || '';
  }
}

async function api(method, path, body, contentType = 'application/json') {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  const init = { method, headers };
  if (body !== undefined) {
    headers['Content-Type'] = contentType;
    init.body = contentType === 'application/json' ? JSON.stringify(body) : body;
  }
  const response = await fetch(path, init);
  const type = response.headers.get('Content-Type') || '';
  const data = type.includes('json') ? await response.json() : await response.blob();
  if (!response.ok) {
    const error = new Error((data && data.message) || response.statusText);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

// ---------- small DOM helpers ----------
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (value !== undefined && value !== null && value !== false) node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) if (child) node.append(child);
  return node;
}
const $ = (selector, root = document) => root.querySelector(selector);
const statLabel = (id) => STAT_LABELS[id] || id;
const statById = (id) => state.stats.find((stat) => stat.id === id);
const round = (value) => Math.round(value * 1e6) / 1e6;
const textureLabel = (name) => ((state.looks.builtInTextures || []).includes(name) ? `${name}（內建）` : `${name}（上傳）`);

function notice(kind, message, items = []) {
  const box = $('#notice');
  box.className = 'notice ' + kind;
  const parts = [el('div', { text: message })];
  if (items.length) parts.push(el('ul', {}, items.map((item) => el('li', { text: item }))));
  box.replaceChildren(...parts);
}

function describeError(error) {
  if (error.status === 403) return '伺服器拒絕了這個請求（只接受從這個網頁送出的修改）。';
  if (error.status === 401) return '需要 token：請用遊戲聊天欄裡的連結開啟這個網頁。';
  if (error.status === undefined) return '連不到遊戲。請確認世界還開著（離開世界時網頁後台會關閉）。';
  return error.message;
}

// ---------- loading ----------
async function loadAll(keepSelection = true) {
  const [stats, looks, list, mobs] = await Promise.all([
    api('GET', '/api/v1/stats'), api('GET', '/api/v1/looks'), api('GET', '/api/v1/weapons'), api('GET', '/api/v1/mobs')]);
  state.stats = stats;
  state.looks = looks;
  state.weapons = list.weapons;
  state.mobs = mobs.mobs;
  renderList();
  renderMobs();
  const still = keepSelection && state.selected && state.weapons.find((w) => w.id === state.selected);
  if (still) openEditor(still); else if (!keepSelection) closeEditor();
  renderCreator();
}

function renderList() {
  const list = $('#weapon-list');
  list.replaceChildren(...state.weapons.map((entry) => {
    const weapon = entry.weapon;
    const look = weapon.look.item ? weapon.look.item.replace('minecraft:', '') : '貼圖 ' + weapon.look.texture;
    return el('li', {}, el('button', {
      type: 'button',
      class: 'weapon-item' + (entry.id === state.selected ? ' selected' : ''),
      'aria-label': `${weapon.name}，${STATUS_LABELS[entry.status]}，${summary(weapon.modifiers)}`,
      onclick: () => { state.selected = entry.id; renderList(); openEditor(entry); },
    },
    el('div', {}, el('span', { class: 'name', text: weapon.name }), el('span', { class: 'badge ' + entry.status, text: STATUS_LABELS[entry.status] })),
    el('div', { class: 'meta', text: `${entry.id} · ${SLOT_LABELS[weapon.slot] || weapon.slot} · ${look}` }),
    el('div', { class: 'meta', text: summary(weapon.modifiers) })));
  }));
}

function summary(modifiers) {
  if (!modifiers.length) return '沒有數值修飾';
  return modifiers.map((m) => {
    const stat = statById(m.stat);
    if (m.percent !== undefined) return `${statLabel(m.stat)} ${m.percent >= 0 ? '+' : ''}${m.percent}%`;
    if (stat && stat.chance) return `${statLabel(m.stat)} ${m.add >= 0 ? '+' : ''}${round(m.add * 100)}%`;
    return `${statLabel(m.stat)} ${m.add >= 0 ? '+' : ''}${m.add}`;
  }).join('、');
}

// ---------- the weapon form (shared by edit and create) ----------
function buildForm(form, entry, mode) {
  const weapon = entry ? entry.weapon : { name: '', slot: 'mainhand', look: { item: 'minecraft:iron_sword' }, modifiers: [] };
  const editable = mode === 'create' || entry.status === 'active';
  const parts = [];

  if (mode === 'create') {
    parts.push(field('id', 'id（檔名，也是 /give 用的名稱）',
      el('input', { name: 'id', required: true, pattern: '[a-z][a-z0-9_]*', placeholder: '例如 halberd', maxlength: 40 }),
      '只能用小寫英文、數字、底線，開頭要是英文字母。'));
  } else {
    parts.push(el('div', { class: 'field' }, el('span', { class: 'legend', text: weapon.name }),
      el('p', { class: 'note', text: `id：${entry.id}　狀態：${STATUS_LABELS[entry.status]}` })));
  }
  parts.push(field('name', '名稱（遊戲裡顯示的名字）', el('input', { name: 'name', value: weapon.name, maxlength: 64, required: true })));

  const slot = el('select', { name: 'slot', disabled: mode !== 'create' },
    Object.entries(SLOT_LABELS).map(([value, label]) => el('option', { value, selected: value === weapon.slot, text: label })));
  parts.push(field('slot', '欄位', slot, mode === 'create' ? '武器放主手；盔甲放對應的部位。' : '欄位要重開遊戲才能改，這裡不能改。'));

  parts.push(lookField(weapon.look));
  parts.push(modifiersField(weapon.modifiers));

  const save = el('button', { type: 'submit', text: mode === 'create' ? '存成新武器（重開後生效）' : '儲存並套用' });
  const actions = el('div', { class: 'actions' }, save);
  if (mode !== 'create') actions.append(el('button', { type: 'button', class: 'ghost', text: '還原', onclick: () => openEditor(entry) }));
  if (!editable) {
    save.disabled = true;
    actions.append(el('span', { class: 'note', text: '這把武器還沒生效（或檔案已刪除），重開遊戲後才能編輯。' }));
  }
  parts.push(actions);
  form.replaceChildren(...parts);
}

function field(name, label, input, note) {
  input.dataset.field = name;
  input.id = input.id || 'f-' + name + '-' + Math.random().toString(36).slice(2, 7);
  return el('div', { class: 'field' }, el('label', { for: input.id, text: label }), input,
    note ? el('p', { class: 'note', text: note }) : null, el('div', { class: 'field-error', 'data-error-for': name }));
}

function lookField(look) {
  const kind = look.texture !== undefined ? 'texture' : 'item';
  const group = 'look-' + Math.random().toString(36).slice(2, 7);
  const items = el('select', { class: 'look-item', 'data-field': 'look.item', 'aria-label': '原版物品' },
    state.looks.items.map((id) => el('option', { value: id, selected: id === look.item, text: id.replace('minecraft:', '') })));
  if (look.item && !state.looks.items.includes(look.item)) items.prepend(el('option', { value: look.item, selected: true, text: look.item }));
  const textures = el('select', { class: 'look-texture', 'data-field': 'look.texture', 'aria-label': '貼圖' },
    state.looks.textures.map((name) => el('option', { value: name, selected: name === look.texture, text: textureLabel(name) })));
  const preview = el('img', { class: 'preview', alt: '貼圖預覽' });
  const showPreview = async () => {
    preview.removeAttribute('src');
    if (!textures.value) return;
    try {
      const blob = await api('GET', '/api/v1/textures/' + encodeURIComponent(textures.value));
      preview.src = URL.createObjectURL(blob);
    } catch (e) { /* no preview */ }
  };
  textures.addEventListener('change', showPreview);
  const radioItem = el('input', { type: 'radio', name: group, value: 'item', checked: kind === 'item', 'aria-label': '借用原版物品' });
  const radioTexture = el('input', { type: 'radio', name: group, value: 'texture', checked: kind === 'texture', 'aria-label': '貼圖' });
  const sync = () => {
    items.disabled = !radioItem.checked;
    textures.disabled = !radioTexture.checked;
    preview.classList.toggle('hidden', !radioTexture.checked);
  };
  radioItem.addEventListener('change', sync);
  radioTexture.addEventListener('change', sync);

  const file = el('input', { type: 'file', accept: 'image/png', 'aria-label': '選擇 PNG 檔' });
  const name = el('input', { placeholder: '貼圖名稱，例如 halberd_netherite', pattern: '[a-z0-9_]+', 'aria-label': '貼圖名稱' });
  file.addEventListener('change', () => {
    if (file.files[0] && !name.value) name.value = file.files[0].name.replace(/\.png$/i, '').toLowerCase().replace(/[^a-z0-9_]/g, '_');
  });
  const upload = el('button', {
    type: 'button', class: 'ghost', text: '上傳貼圖',
    onclick: async () => {
      if (!file.files[0] || !name.value) { notice('warn', '請先選 PNG 檔並填貼圖名稱。'); return; }
      try {
        await api('PUT', '/api/v1/textures/' + encodeURIComponent(name.value), await file.files[0].arrayBuffer(), 'image/png');
        const looks = await api('GET', '/api/v1/looks');
        state.looks = looks;
        textures.replaceChildren(...looks.textures.map((n) => el('option', { value: n, selected: n === name.value, text: textureLabel(n) })));
        radioTexture.checked = true;
        sync();
        showPreview();
        notice('ok', `貼圖 ${name.value}.png 已上傳。選好以後按「儲存」才會套用到武器。`);
      } catch (error) {
        notice('error', '上傳失敗：' + describeError(error));
      }
    },
  });

  sync();
  if (kind === 'texture') showPreview();
  return el('div', { class: 'field look' },
    el('span', { class: 'legend', text: '外觀' }),
    el('div', { class: 'look-row' }, el('label', {}, radioItem, '借用原版物品'), items),
    el('div', { class: 'look-row' }, el('label', {}, radioTexture, '貼圖（內建或上傳）'), textures, preview),
    el('div', { class: 'upload' }, file, name, upload),
    el('p', { class: 'note', text: '上傳的 PNG：正方形，16、32、64、128 或 256 像素，最大 256 KB，名稱不能和內建貼圖相同。換成原版物品或內建貼圖時，手上的武器馬上換；換成剛上傳的貼圖時，遊戲會自動重新載入資源（幾秒）。' }),
    el('div', { class: 'field-error', 'data-error-for': 'look' }));
}

function modifiersField(modifiers) {
  const rows = el('div', { class: 'modifiers' });
  const addRow = (modifier) => rows.append(modifierRow(modifier, rows));
  modifiers.forEach(addRow);
  return el('div', { class: 'field' },
    el('span', { class: 'legend', text: '數值修飾' }),
    rows,
    el('div', { class: 'actions' }, el('button', { type: 'button', class: 'ghost', text: '新增一行', onclick: () => addRow({ stat: state.stats[0].id, add: 0 }) })),
    el('p', { class: 'note', text: '「加固定值」直接加在數值上（攻擊力 +6）；「百分比」是乘上去（-30 代表 -30%）。暴擊率、迴避率用加固定值，單位是 %。' }),
    el('div', { class: 'field-error', 'data-error-for': 'modifiers' }));
}

function modifierRow(modifier, rows) {
  const row = $('#modifier-row').content.firstElementChild.cloneNode(true);
  const statSelect = $('.m-stat', row);
  statSelect.append(...state.stats.map((stat) => el('option', { value: stat.id, selected: stat.id === modifier.stat, text: statLabel(stat.id) })));
  const op = $('.m-op', row);
  const amount = $('.m-amount', row);
  const unit = $('.m-unit', row);
  op.value = modifier.percent !== undefined ? 'percent' : 'add';
  const chance = () => { const stat = statById(statSelect.value); return stat && stat.chance && op.value === 'add'; };
  const raw = modifier.percent !== undefined ? modifier.percent : modifier.add;
  amount.value = chance() ? round(raw * 100) : raw;
  const updateUnit = () => { unit.textContent = op.value === 'percent' || chance() ? '%' : ''; };
  statSelect.addEventListener('change', updateUnit);
  op.addEventListener('change', updateUnit);
  updateUnit();
  $('.m-remove', row).addEventListener('click', () => row.remove());
  return row;
}

/** Reads a form back into the file format; numbers stay numbers so the server can check them. */
function readForm(form) {
  const weapon = {
    format: 1,
    name: $('[data-field="name"]', form).value,
    slot: $('[data-field="slot"]', form).value,
    look: {},
    modifiers: [],
  };
  const useTexture = $('.look input[value="texture"]', form).checked;
  if (useTexture) weapon.look.texture = $('.look-texture', form).value;
  else weapon.look.item = $('.look-item', form).value;
  form.querySelectorAll('.modifier').forEach((row, index) => {
    const statId = $('.m-stat', row).value;
    const op = $('.m-op', row).value;
    const text = $('.m-amount', row).value.trim();
    let value = text === '' ? NaN : Number(text);
    const stat = statById(statId);
    if (op === 'add' && stat && stat.chance && Number.isFinite(value)) value = round(value / 100);
    const modifier = { stat: statId };
    // NaN cannot be sent as JSON, so an empty or bad box is sent as text and the server names the box.
    modifier[op] = Number.isFinite(value) ? value : text || 'empty';
    $('.m-amount', row).dataset.field = `modifiers[${index}].${op}`;
    weapon.modifiers.push(modifier);
  });
  return weapon;
}

function clearErrors(form) {
  form.querySelectorAll('.invalid').forEach((node) => node.classList.remove('invalid'));
  form.querySelectorAll('.field-error').forEach((node) => { node.textContent = ''; });
}

function showFieldErrors(form, error) {
  const fields = (error.data && error.data.fields) || {};
  for (const [name, reason] of Object.entries(fields)) {
    const input = form.querySelector(`[data-field="${CSS.escape(name)}"]`);
    if (input) input.classList.add('invalid');
    const key = name.startsWith('modifiers') ? 'modifiers' : name.startsWith('look') ? 'look' : name;
    const slot = form.querySelector(`[data-error-for="${CSS.escape(key)}"]`);
    if (slot) slot.textContent = `${name}：${reason}`;
  }
}

// ---------- edit ----------
function openEditor(entry) {
  const form = $('#editor');
  $('#editor-empty').classList.add('hidden');
  form.classList.remove('hidden');
  buildForm(form, entry, 'edit');
  form.onsubmit = async (event) => {
    event.preventDefault();
    clearErrors(form);
    const body = readForm(form);
    try {
      const saved = await api('PUT', '/api/v1/weapons/' + encodeURIComponent(entry.id), body);
      notice('ok', `「${saved.weapon.name}」已套用：遊戲裡拿著它的玩家和生物已經是新數值，也已寫回 ${entry.id}.json。`);
      await loadAll();
    } catch (error) {
      showFieldErrors(form, error);
      notice('error', '沒有儲存：' + describeError(error));
    }
  };
}

function closeEditor() {
  $('#editor').classList.add('hidden');
  $('#editor-empty').classList.remove('hidden');
}

// ---------- create ----------
function renderCreator() {
  const form = $('#creator');
  if (form.dataset.ready) return;
  form.dataset.ready = 'yes';
  buildForm(form, null, 'create');
  form.onsubmit = async (event) => {
    event.preventDefault();
    clearErrors(form);
    const body = Object.assign({ id: $('[data-field="id"]', form).value.trim() }, readForm(form));
    try {
      const created = await api('POST', '/api/v1/weapons', body);
      notice('ok', `已存成 ${created.id}.json。重開遊戲後就能用 /give @p mobarmory:${created.id} 拿到。`);
      delete form.dataset.ready;
      await loadAll();
    } catch (error) {
      showFieldErrors(form, error);
      notice('error', '沒有儲存：' + describeError(error));
    }
  };
}

// ---------- mobs (read only) ----------
function statValue(id, value) {
  const stat = statById(id);
  return stat && stat.chance ? `${round(value * 100)}%` : String(round(value));
}

function weaponLabel(id) {
  const entry = state.weapons.find((w) => w.id === id);
  return entry ? `${entry.weapon.name}（${id}）` : id;
}

function renderMobs() {
  const list = $('#mob-list');
  if (!state.mobs.length) {
    list.replaceChildren(el('li', { class: 'empty', text: '伺服器上沒有自訂怪物。' }));
    return;
  }
  list.replaceChildren(...state.mobs.map((mob) => {
    const stats = Object.entries(mob.stats).map(([id, value]) => `${statLabel(id)} ${statValue(id, value)}`);
    const equipment = Object.entries(mob.equipment).map(([slot, id]) => `${SLOT_LABELS[slot] || slot}：${weaponLabel(id)}`);
    const behaviors = mob.behaviors.map((name) => BEHAVIOR_LABELS[name] || name);
    const row = (label, text) => el('div', { class: 'mob-row' }, el('span', { class: 'mob-label', text: label }), el('span', { text }));
    return el('li', { class: 'mob-card' },
      el('div', { class: 'name', text: mob.id }),
      row('外觀', `${BODY_LABELS[mob.look.body] || mob.look.body}的身體，大小 ×${round(mob.look.scale)}`),
      row('數值', stats.length ? stats.join('、') + '（沒列出的用預設值）' : '全部用預設值'),
      row('裝備', equipment.length ? equipment.join('、') : '沒有'),
      row('行為', behaviors.length ? behaviors.join('、') : '沒有（站著不動）'));
  }));
}

// ---------- header, tabs ----------
async function reloadFromFiles() {
  try {
    const report = await api('POST', '/api/v1/reload');
    const lines = [];
    if (report.changed.length) lines.push('已套用：' + report.changed.join('、'));
    if (report.pendingRestart.length) lines.push('新武器，重開後生效：' + report.pendingRestart.join('、'));
    if (report.fileMissing.length) lines.push('檔案不見了，維持到重開（重開後會從世界消失）：' + report.fileMissing.join('、'));
    if (report.slotWaits.length) lines.push('欄位改了，重開後生效：' + report.slotWaits.join('、'));
    notice('ok', lines.length ? '已從檔案重新載入。' : '已從檔案重新載入，沒有變動。', lines);
    await loadAll();
  } catch (error) {
    const problems = (error.data && error.data.problems) || [];
    notice('error', problems.length ? '沒有任何變動。請先修好這些檔案：' : '重新載入失敗：' + describeError(error), problems);
  }
}

function switchTab(name) {
  document.querySelectorAll('.tab').forEach((tab) => {
    const active = tab.dataset.tab === name;
    tab.classList.toggle('active', active);
    tab.setAttribute('aria-selected', String(active));
  });
  document.querySelectorAll('.panel').forEach((panel) => panel.classList.toggle('hidden', panel.id !== 'tab-' + name));
}

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', () => switchTab(tab.dataset.tab)));
  $('#refresh').addEventListener('click', () => loadAll().then(() => notice('ok', '已載入最新的數值。')).catch((e) => notice('error', describeError(e))));
  $('#reload').addEventListener('click', reloadFromFiles);
  loadAll(false).catch((error) => notice('error', describeError(error)));
});
