// Mob Armory admin: weapons (R6) and mobs (R4b). Talks only to /api/v1 on the same server; no libraries, no outside requests.
'use strict';

const STAT_LABELS = {
  attack_damage: '攻擊力', movement_speed: '移動速度', armor: '護甲',
  attack_speed: '攻擊速度', max_health: '最大生命', crit_chance: '暴擊率', dodge_chance: '迴避率',
};
const SLOT_LABELS = { mainhand: '主手', offhand: '副手', head: '頭盔', chest: '胸甲', legs: '護腿', feet: '靴子' };
const STATUS_LABELS = { active: '生效中', pending_restart: '待重開遊戲', file_missing: '檔案已刪除，重開後消失' };
const BODY_LABELS = { zombie: '殭屍', husk: '屍殼', skeleton: '骷髏', stray: '流髑' };
const BEHAVIOR_LABELS = { wander: '閒晃', attack_players: '攻擊玩家', neutral: '中立（被打才反擊）', leap: '衝刺（撲向目標）' };
const ATTACK_LABELS = { attack_players: '主動攻擊玩家', neutral: '中立：被打才反擊', none: '不攻擊' };

const state = { stats: [], looks: { items: [], textures: [] }, weapons: [], mobs: [], mobOptions: null, selected: null, selectedMob: null };
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
  const [stats, looks, list, mobs, mobOptions] = await Promise.all([
    api('GET', '/api/v1/stats'), api('GET', '/api/v1/looks'), api('GET', '/api/v1/weapons'), api('GET', '/api/v1/mobs'),
    api('GET', '/api/v1/mob-options')]);
  state.stats = stats;
  state.looks = looks;
  state.weapons = list.weapons;
  state.mobs = mobs.mobs;
  state.mobOptions = mobOptions;
  renderList();
  renderMobs();
  const still = keepSelection && state.selected && state.weapons.find((w) => w.id === state.selected);
  if (still) openEditor(still); else if (!keepSelection) closeEditor();
  const stillMob = keepSelection && state.selectedMob && state.mobs.find((m) => m.id === state.selectedMob);
  if (stillMob) openMobEditor(stillMob); else if (!keepSelection) closeMobEditor();
  renderCreator();
  renderMobCreator();
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
    const key = name.split(/[.[]/)[0]; // modifiers[1].add → modifiers, look.body → look, stats.max_health → stats
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

// ---------- mobs (R4b: list, edit, create) ----------
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
    return el('li', {}, el('button', {
      type: 'button',
      class: 'weapon-item' + (mob.id === state.selectedMob ? ' selected' : ''),
      'aria-label': `${mob.name}，${STATUS_LABELS[mob.status]}`,
      onclick: () => { state.selectedMob = mob.id; renderMobs(); openMobEditor(mob); },
    },
    el('div', {}, el('span', { class: 'name', text: mob.name }), el('span', { class: 'badge ' + mob.status, text: STATUS_LABELS[mob.status] })),
    el('div', { class: 'meta', text: `${mob.id} · ${BODY_LABELS[mob.look.body] || mob.look.body} ×${round(mob.look.scale)}` }),
    el('div', { class: 'meta', text: stats.length ? stats.join('、') : '數值全部用預設值' }),
    el('div', { class: 'meta', text: (equipment.length ? equipment.join('、') : '沒有裝備') + ' · '
      + (behaviors.length ? behaviors.join('、') : '沒有行為（站著不動）') })));
  }));
}

/** The mob form, shared by edit and create; reads and writes the mob file format (data/MobFileJson). */
function buildMobForm(form, entry, mode) {
  const mob = entry ? entry.mob : {
    name: '', look: { body: 'zombie', scale: 1 }, stats: {}, equipment: {},
    behaviors: [{ type: 'wander', speed: 1 }, { type: 'attack_players', speed: 1 }],
  };
  const options = state.mobOptions || { bodies: Object.keys(BODY_LABELS), maxSpeed: 3, minScale: 0.0625, maxScale: 16 };
  const editable = mode === 'create' || entry.status === 'active';
  const byType = Object.fromEntries(mob.behaviors.map((b) => [b.type, b]));
  const attack = byType.attack_players ? 'attack_players' : byType.neutral ? 'neutral' : 'none';
  const chase = (byType.attack_players || byType.neutral || { speed: 1 }).speed ?? 1;
  const parts = [];

  if (mode === 'create') {
    parts.push(field('id', 'id（檔名，也是 /summon 用的名稱）',
      el('input', { name: 'id', required: true, pattern: '[a-z][a-z0-9_]*', placeholder: '例如 goblin', maxlength: 40 }),
      '只能用小寫英文、數字、底線，開頭要是英文字母。'));
  } else {
    parts.push(el('div', { class: 'field' }, el('span', { class: 'legend', text: mob.name }),
      el('p', { class: 'note', text: `id：${entry.id}　狀態：${STATUS_LABELS[entry.status]}` })));
  }
  parts.push(field('name', '名稱（遊戲裡顯示的名字）', el('input', { name: 'name', value: mob.name, maxlength: 64, required: true })));

  const body = el('select', { name: 'body', disabled: mode !== 'create' },
    options.bodies.map((value) => el('option', { value, selected: value === mob.look.body, text: BODY_LABELS[value] || value })));
  const scale = el('input', { name: 'scale', type: 'number', step: 'any', min: options.minScale, max: options.maxScale, value: mob.look.scale });
  parts.push(el('div', { class: 'field' }, el('span', { class: 'legend', text: '外觀' }),
    el('label', { class: 'choice' }, '身體', body), el('label', { class: 'choice' }, '大小倍率', scale),
    el('p', { class: 'note', text: mode === 'create' ? '從原版的人形身體挑一種；大小 1 是原版大小。' : '身體要重開遊戲才能換，這裡不能改；大小立刻套用。' }),
    el('div', { class: 'field-error', 'data-error-for': 'look' })));
  body.dataset.field = 'look.body';
  scale.dataset.field = 'look.scale';

  const statInputs = state.stats.map((stat) => {
    const value = mob.stats[stat.id];
    const input = el('input', { type: 'number', step: 'any', min: stat.min, max: stat.max, value: value ?? '',
      placeholder: `預設 ${statValue(stat.id, stat.default)}` });
    input.dataset.field = 'stats.' + stat.id;
    input.dataset.stat = stat.id;
    return el('label', {}, el('span', { text: `${statLabel(stat.id)}（${stat.min}～${stat.max}）` }), input);
  });
  parts.push(el('div', { class: 'field' }, el('span', { class: 'legend', text: '數值' }), el('div', { class: 'stat-grid' }, statInputs),
    el('p', { class: 'note', text: '空白就用預設值。機率類（暴擊率、迴避率）填 0～1。' }), el('div', { class: 'field-error', 'data-error-for': 'stats' })));

  const slots = Object.keys(SLOT_LABELS).map((slot) => {
    const choices = state.weapons.filter((w) => w.status === 'active' && w.weapon.slot === slot);
    const select = el('select', {},
      el('option', { value: '', text: '無', selected: !mob.equipment[slot] }),
      choices.map((w) => el('option', { value: w.id, selected: mob.equipment[slot] === w.id, text: `${w.weapon.name}（${w.id}）` })));
    if (mob.equipment[slot] && !choices.some((w) => w.id === mob.equipment[slot])) {
      select.append(el('option', { value: mob.equipment[slot], selected: true, text: `${mob.equipment[slot]}（目前沒有這件或欄位不對，生成時不拿）` }));
    }
    select.dataset.field = 'equipment.' + slot;
    select.dataset.slot = slot;
    return el('label', {}, el('span', { text: SLOT_LABELS[slot] }), select);
  });
  parts.push(el('div', { class: 'field' }, el('span', { class: 'legend', text: '裝備' }), el('div', { class: 'stat-grid' }, slots),
    el('p', { class: 'note', text: '只列出在那個欄位生效的武器。武器本身在「武器」分頁改。' }), el('div', { class: 'field-error', 'data-error-for': 'equipment' })));

  const wander = el('input', { type: 'checkbox', name: 'wander', checked: !!byType.wander });
  wander.dataset.speed = byType.wander ? (byType.wander.speed ?? 1) : 1;
  const radios = Object.entries(ATTACK_LABELS).map(([value, label]) =>
    el('label', { class: 'choice' }, el('input', { type: 'radio', name: 'attack', value, checked: value === attack }), label));
  const speed = el('input', { name: 'chase', type: 'number', step: 'any', min: 0.1, max: options.maxSpeed, value: chase });
  const leap = el('input', { type: 'checkbox', name: 'leap', checked: !!byType.leap });
  parts.push(el('div', { class: 'field' }, el('span', { class: 'legend', text: '行為' }),
    el('label', { class: 'choice' }, wander, '閒晃（沒事時隨意走動）'),
    el('div', {}, radios),
    el('label', { class: 'choice' }, '追擊速度', speed),
    el('p', { class: 'note', text: `奔跑：1 是原本的速度，1.3 以上看起來像在跑，最多 ${options.maxSpeed}。追擊距離固定 16 格。` }),
    el('label', { class: 'choice' }, leap, '衝刺：靠近目標時往前撲（要搭配攻擊或中立）'),
    el('div', { class: 'field-error', 'data-error-for': 'behaviors' })));
  speed.dataset.field = 'behaviors';

  if (editable) parts.push(el('div', { class: 'actions' }, el('button', { type: 'submit', text: mode === 'create' ? '新增' : '儲存並套用' })));
  else parts.push(el('p', { class: 'note', text: '這種怪物要重開遊戲後才能改。' }));
  form.replaceChildren(...parts);
}

function readMobForm(form) {
  const number = (input) => (input.value.trim() === '' ? undefined : Number(input.value));
  const stats = {};
  form.querySelectorAll('[data-stat]').forEach((input) => {
    const value = number(input);
    if (value !== undefined) stats[input.dataset.stat] = value;
  });
  const equipment = {};
  form.querySelectorAll('[data-slot]').forEach((select) => { if (select.value) equipment[select.dataset.slot] = select.value; });
  const behaviors = [];
  const wander = $('[name="wander"]', form);
  if (wander.checked) behaviors.push({ type: 'wander', speed: Number(wander.dataset.speed) });
  const attack = $('[name="attack"]:checked', form).value;
  const chase = number($('[name="chase"]', form)) ?? 1;
  if (attack !== 'none') behaviors.push({ type: attack, speed: chase });
  if ($('[name="leap"]', form).checked) behaviors.push({ type: 'leap' });
  return {
    format: 1,
    name: $('[name="name"]', form).value,
    look: { body: $('[name="body"]', form).value, scale: number($('[name="scale"]', form)) ?? 1 },
    stats,
    equipment,
    behaviors,
  };
}

function openMobEditor(entry) {
  const form = $('#mob-editor');
  $('#mob-editor-empty').classList.add('hidden');
  form.classList.remove('hidden');
  buildMobForm(form, entry, 'edit');
  form.onsubmit = async (event) => {
    event.preventDefault();
    clearErrors(form);
    try {
      const saved = await api('PUT', '/api/v1/mobs/' + encodeURIComponent(entry.id), readMobForm(form));
      notice('ok', `「${saved.name}」已存成 ${entry.id}.json。名稱、數值、大小已套用到世界上的${saved.name}；行為和裝備等下次生成才生效；身體要重開遊戲。`);
      await loadAll();
    } catch (error) {
      showFieldErrors(form, error);
      notice('error', '沒有儲存：' + describeError(error));
    }
  };
}

function closeMobEditor() {
  $('#mob-editor').classList.add('hidden');
  $('#mob-editor-empty').classList.remove('hidden');
}

function renderMobCreator() {
  const form = $('#mob-creator');
  if (form.dataset.ready) return;
  form.dataset.ready = 'yes';
  buildMobForm(form, null, 'create');
  form.onsubmit = async (event) => {
    event.preventDefault();
    clearErrors(form);
    const body = Object.assign({ id: $('[data-field="id"]', form).value.trim() }, readMobForm(form));
    try {
      const created = await api('POST', '/api/v1/mobs', body);
      notice('ok', `已存成 config/mobarmory/mobs/${created.id}.json。重開遊戲後就能用生怪蛋或 /summon mobarmory:${created.id} 生出來。`);
      delete form.dataset.ready;
      await loadAll();
    } catch (error) {
      showFieldErrors(form, error);
      notice('error', '沒有儲存：' + describeError(error));
    }
  };
}

// ---------- header, tabs ----------
/** One button, two kinds of files (R4b O12): weapons, then mobs; each is all or nothing on its own. */
async function reloadFromFiles() {
  const lines = [];
  const problems = [];
  try {
    const report = await api('POST', '/api/v1/reload', {}); // a JSON body: the server only takes changes as JSON (B13)
    if (report.changed.length) lines.push('武器已套用：' + report.changed.join('、'));
    if (report.pendingRestart.length) lines.push('新武器，重開後生效：' + report.pendingRestart.join('、'));
    if (report.fileMissing.length) lines.push('武器檔不見了，維持到重開（重開後會從世界消失）：' + report.fileMissing.join('、'));
    if (report.slotWaits.length) lines.push('武器欄位改了，重開後生效：' + report.slotWaits.join('、'));
  } catch (error) {
    const found = (error.data && error.data.problems) || [];
    problems.push(...(found.length ? found.map((p) => '武器：' + p) : ['武器重新載入失敗：' + describeError(error)]));
  }
  try {
    const report = await api('POST', '/api/v1/mobs/reload', {});
    if (report.changed.length) lines.push('怪物已套用：' + report.changed.join('、'));
    if (report.pendingRestart.length) lines.push('新怪物，重開後生效：' + report.pendingRestart.join('、'));
    if (report.fileMissing.length) lines.push('怪物檔不見了，維持到重開（重開後那種怪物會從世界消失）：' + report.fileMissing.join('、'));
    if (report.bodyWaits.length) lines.push('怪物身體改了，重開後生效：' + report.bodyWaits.join('、'));
  } catch (error) {
    const found = (error.data && error.data.problems) || [];
    problems.push(...(found.length ? found.map((p) => '怪物：' + p) : ['怪物重新載入失敗：' + describeError(error)]));
  }
  if (problems.length) notice('error', '有問題的那一種沒有任何變動。請先修好這些檔案：', problems.concat(lines));
  else notice('ok', lines.length ? '已從檔案重新載入。' : '已從檔案重新載入，沒有變動。', lines);
  await loadAll().catch(() => {});
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
