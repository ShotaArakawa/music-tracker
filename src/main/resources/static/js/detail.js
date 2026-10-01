/* 作曲コア（スタジオ）画面 songs/detail.html のスクリプト */
const SAVE_URL = document.body.getAttribute('data-save-url');
const STORAGE_KEY = 'mt-split-sizes';

/* =========================================================
   1) セクションのドラッグ&ドロップ並び替え
   ========================================================= */
let dragEl = null;

function setupCard(card) {
    const handle = card.querySelector('.drag-handle');
    // ドラッグはハンドルからのみ開始（テキスト編集を邪魔しない）
    handle.addEventListener('mousedown', () => card.setAttribute('draggable', 'true'));
    handle.addEventListener('mouseup', () => card.removeAttribute('draggable'));
    card.addEventListener('dragstart', (e) => {
        dragEl = card;
        card.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
    });
    card.addEventListener('dragend', () => {
        card.classList.remove('dragging');
        card.removeAttribute('draggable');
        dragEl = null;
    });
    // 削除ボタン
    card.querySelector('.btn-del').addEventListener('click', () => {
        if (confirm('このセクションを削除しますか？（保存時に反映されます）')) {
            card.remove();
        }
    });
}

function getDragAfterElement(list, y) {
    const cards = [...list.querySelectorAll('.section-card:not(.dragging)')];
    return cards.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closest.offset) {
            return { offset: offset, element: child };
        }
        return closest;
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

document.querySelectorAll('.section-list').forEach((list) => {
    list.addEventListener('dragover', (e) => {
        // 同じエリア内でのみ並び替え可能
        if (!dragEl || dragEl.closest('.section-list') !== list) return;
        e.preventDefault();
        const after = getDragAfterElement(list, e.clientY);
        if (after == null) {
            list.appendChild(dragEl);
        } else {
            list.insertBefore(dragEl, after);
        }
    });
    list.addEventListener('drop', (e) => e.preventDefault());
});

// 既存カードを初期化
document.querySelectorAll('.section-card').forEach(setupCard);

/* =========================================================
   2) セクション追加／カード生成
   ========================================================= */
const template = document.getElementById('section-template');

// カードを1枚生成して返す。コードエリアではセクション個別 Key 入力も付与する。
// data = { name, content, sectionKey } で初期値を流し込める（テンプレ適用に使用）。
function buildCard(area, data) {
    data = data || {};
    const node = template.content.firstElementChild.cloneNode(true);
    const nameInput = node.querySelector('.section-name');
    const body = node.querySelector('.section-body');
    nameInput.value = data.name || '';
    body.value = data.content || '';
    if (area === 'chord') {
        body.classList.add('chord-area');
        body.placeholder = '例: F - G - Em - Am';
        // セクション個別 Key（転調用）の入力欄を名前の右に差し込む
        const keyInput = document.createElement('input');
        keyInput.type = 'text';
        keyInput.className = 'section-key';
        keyInput.placeholder = 'Key';
        keyInput.title = 'このセクションのKey（転調）。空なら曲全体のKeyを使用';
        keyInput.value = data.sectionKey || '';
        nameInput.insertAdjacentElement('afterend', keyInput);
    } else {
        body.placeholder = '歌詞を入力...';
    }
    setupCard(node);
    return node;
}

function getSectionList(area) {
    return document.querySelector('.section-list[data-area="' + area + '"]');
}

document.querySelectorAll('.add-section').forEach((btn) => {
    btn.addEventListener('click', () => {
        const area = btn.getAttribute('data-area');
        const node = buildCard(area, {});
        getSectionList(area).appendChild(node);
        node.querySelector('.section-name').focus();
    });
});

/* =========================================================
   3) 進捗（数値入力・スライダー・バーの連動）
   ========================================================= */
function clampPct(v) {
    if (isNaN(v)) return 0;
    return Math.max(0, Math.min(100, Math.round(v)));
}

function bindMetric(numId, barId) {
    const num = document.getElementById(numId);
    const bar = document.getElementById(barId);
    const range = document.querySelector('.pct-range[data-for="' + numId + '"]');

    function apply(value, source) {
        const v = clampPct(value);
        if (source !== 'num') num.value = v;
        if (source !== 'range') range.value = v;
        bar.style.width = v + '%';
        updateOverall();
    }
    num.addEventListener('input', () => apply(parseInt(num.value, 10), 'num'));
    num.addEventListener('blur', () => apply(parseInt(num.value, 10), 'blur'));
    range.addEventListener('input', () => apply(parseInt(range.value, 10), 'range'));
}

function updateOverall() {
    const l = clampPct(parseInt(document.getElementById('lyricProgress').value, 10));
    const m = clampPct(parseInt(document.getElementById('melodyProgress').value, 10));
    const a = clampPct(parseInt(document.getElementById('arrangementProgress').value, 10));
    const overall = Math.round((l + m + a) / 3);
    document.getElementById('overallVal').textContent = overall;
    document.getElementById('overallBar').style.width = overall + '%';
}

bindMetric('lyricProgress', 'lyricBar');
bindMetric('melodyProgress', 'melodyBar');
bindMetric('arrangementProgress', 'arrangementBar');

/* =========================================================
   4) 3エリアのリサイズ（スプリットビュー）
   ========================================================= */
const split = document.getElementById('split');
const panels = [...split.querySelectorAll('.panel')];

function saveSizes() {
    const sizes = panels.map((p) => p.style.flexBasis || '');
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sizes));
}

function restoreSizes() {
    try {
        const sizes = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
        if (Array.isArray(sizes) && sizes.length === panels.length) {
            panels.forEach((p, i) => { if (sizes[i]) p.style.flexBasis = sizes[i]; });
        }
    } catch (e) { /* 無視 */ }
}

document.querySelectorAll('.gutter').forEach((gutter) => {
    gutter.addEventListener('mousedown', (e) => {
        // 横並び（デスクトップ）以外は無効
        if (window.innerWidth <= 991.98) return;
        e.preventDefault();
        const left = gutter.previousElementSibling;
        const right = gutter.nextElementSibling;
        const startX = e.clientX;
        const leftStart = left.getBoundingClientRect().width;
        const rightStart = right.getBoundingClientRect().width;
        const MIN = 160;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';

        function onMove(ev) {
            let delta = ev.clientX - startX;
            if (leftStart + delta < MIN) delta = MIN - leftStart;
            if (rightStart - delta < MIN) delta = rightStart - MIN;
            left.style.flexBasis = (leftStart + delta) + 'px';
            right.style.flexBasis = (rightStart - delta) + 'px';
        }
        function onUp() {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
            saveSizes();
        }
        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
    });
});

restoreSizes();

/* =========================================================
   5) 一括保存（変更を保存）
   ========================================================= */
function numOrNull(id) {
    const v = document.getElementById(id).value.trim();
    return v === '' ? null : parseInt(v, 10);
}

function collectSections(area) {
    const list = getSectionList(area);
    return [...list.querySelectorAll('.section-card')].map((card) => {
        const keyEl = card.querySelector('.section-key');
        return {
            id: card.dataset.id ? parseInt(card.dataset.id, 10) : null,
            name: card.querySelector('.section-name').value,
            content: card.querySelector('.section-body').value,
            sectionKey: keyEl ? keyEl.value : null
        };
    });
}

function gather() {
    return {
        bpm: numOrNull('bpm'),
        musicKey: document.getElementById('musicKey').value,
        worldViewMemo: document.getElementById('worldViewMemo').value,
        lyricProgress: clampPct(parseInt(document.getElementById('lyricProgress').value, 10)),
        melodyProgress: clampPct(parseInt(document.getElementById('melodyProgress').value, 10)),
        arrangementProgress: clampPct(parseInt(document.getElementById('arrangementProgress').value, 10)),
        lyricSections: collectSections('lyric'),
        chordSections: collectSections('chord')
    };
}

const saveBtn = document.getElementById('save-all');
const saveStatus = document.getElementById('save-status');

saveBtn.addEventListener('click', () => {
    saveBtn.disabled = true;
    saveStatus.textContent = '保存中...';
    fetch(SAVE_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(gather())
    }).then((res) => {
        if (!res.ok) throw new Error('save failed');
        return res.json();
    }).then(() => {
        // 新しく採番されたID等を反映するためリロード（リサイズ幅は localStorage で維持）
        sessionStorage.setItem('mt-saved', '1');
        location.reload();
    }).catch(() => {
        saveBtn.disabled = false;
        saveStatus.textContent = '';
        alert('保存に失敗しました。通信状況を確認してください。');
    });
});

// Ctrl/Cmd + S でも保存
document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        saveBtn.click();
    }
});

// 保存後トースト
if (sessionStorage.getItem('mt-saved')) {
    sessionStorage.removeItem('mt-saved');
    const toast = document.getElementById('save-toast');
    toast.style.display = 'block';
    setTimeout(() => { toast.style.display = 'none'; }, 2200);
}

/* =========================================================
   6) 音楽理論エンジン（フロントエンドのみ・遅延なし）
   ========================================================= */
const SHARP_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
const FLAT_NAMES  = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B'];
const NAME_TO_PC = {
    'C': 0, 'B#': 0, 'C#': 1, 'DB': 1, 'D': 2, 'D#': 3, 'EB': 3, 'E': 4, 'FB': 4,
    'F': 5, 'E#': 5, 'F#': 6, 'GB': 6, 'G': 7, 'G#': 8, 'AB': 8, 'A': 9, 'A#': 10, 'BB': 10, 'B': 11, 'CB': 11
};
// メジャー／ナチュラルマイナーのスケール構成音（半音）と三和音の性質・度数表記
const MAJOR = {
    steps: [0, 2, 4, 5, 7, 9, 11],
    quality: ['', 'm', 'm', '', '', 'm', 'dim'],
    degree: ['I', 'IIm', 'IIIm', 'IV', 'V', 'VIm', 'VIIdim']
};
const MINOR = {
    steps: [0, 2, 3, 5, 7, 8, 10],
    quality: ['m', 'dim', '', 'm', 'm', '', ''],
    degree: ['Im', 'IIdim', 'III', 'IVm', 'Vm', 'VI', 'VII']
};
// Key 起点からの半音間隔 → ローマ数字（非ダイアトニックも表現）
const INTERVAL_ROMAN = ['I', 'bII', 'II', 'bIII', 'III', 'IV', '#IV', 'V', 'bVI', 'VI', 'bVII', 'VII'];

function normalizeRoot(letter, accidental) {
    const acc = accidental === '♯' ? '#' : accidental === '♭' ? 'b' : (accidental || '');
    return letter.toUpperCase() + acc;
}

// "C Major" / "Am" / "F#m" / "D Minor" などを {root, minor} に解析
function parseKey(str) {
    if (!str) return null;
    const m = str.trim().match(/^([A-Ga-g])([#b♯♭]?)\s*(.*)$/);
    if (!m) return null;
    const root = normalizeRoot(m[1], m[2]);
    if (!(root.toUpperCase() in NAME_TO_PC)) return null;
    const rest = m[3].toLowerCase().replace(/\s+/g, '');
    let minor = false;
    if (rest === '' || rest.startsWith('maj')) minor = false;
    else if (rest.startsWith('min') || rest === 'm' || rest.startsWith('m')) minor = true;
    return { root: root, minor: minor };
}

function pcOf(root) {
    return NAME_TO_PC[root.toUpperCase()];
}

function noteName(pc, useFlat) {
    return (useFlat ? FLAT_NAMES : SHARP_NAMES)[((pc % 12) + 12) % 12];
}

// Key のダイアトニックコード一覧を返す [{deg, chord}]
function diatonicChords(key) {
    const pc = pcOf(key.root);
    const flatKeys = key.minor
        ? ['D', 'G', 'C', 'F', 'Bb', 'Eb', 'Ab']
        : ['F', 'Bb', 'Eb', 'Ab', 'Db', 'Gb'];
    const useFlat = key.root.includes('b') || flatKeys.includes(key.root);
    const t = key.minor ? MINOR : MAJOR;
    return t.steps.map((s, i) => ({
        deg: t.degree[i],
        chord: noteName(pc + s, useFlat) + t.quality[i]
    }));
}

// テキストからコードトークンを抽出
function extractChords(text) {
    if (!text) return [];
    const re = /[A-Ga-g][#b♯♭]?(?:maj7|maj9|maj|min|m7|m9|m6|m|dim7|dim|aug|sus2|sus4|sus|add\d+|°|\+|6|7|9|11|13|\/[A-Ga-g][#b]?)*/g;
    return (text.match(re) || []).map((s) => s.trim()).filter(Boolean);
}

// 1つのコードを度数に解析
function analyzeChord(token, key) {
    const m = token.match(/^([A-Ga-g])([#b♯♭]?)(.*)$/);
    if (!m) return null;
    const root = normalizeRoot(m[1], m[2]);
    if (!(root.toUpperCase() in NAME_TO_PC)) return null;
    let rest = m[3];
    // スラッシュコードの分母は除去して三和音性質だけ見る
    rest = rest.split('/')[0];
    const lower = rest.toLowerCase();
    let suffix = '';
    if (lower.startsWith('maj')) suffix = '';
    else if (lower.startsWith('dim') || rest.includes('°')) suffix = 'dim';
    else if (lower.startsWith('aug') || rest.includes('+')) suffix = 'aug';
    else if (lower.startsWith('m') && !lower.startsWith('maj')) suffix = 'm';

    const interval = ((pcOf(root) - pcOf(key.root)) % 12 + 12) % 12;
    const roman = INTERVAL_ROMAN[interval];
    const diatonic = !roman.includes('b') && !roman.includes('#');
    return { chord: token, degree: roman + suffix, diatonic: diatonic };
}

function renderDiatonicInto(el, key) {
    if (!key) {
        el.innerHTML = '<span class="diatonic-empty">Key を設定すると表示されます。</span>';
        return;
    }
    el.innerHTML = diatonicChords(key).map((d) =>
        '<span class="diatonic-chip"><span class="deg">' + d.deg
        + '</span><span class="chord">' + d.chord + '</span></span>'
    ).join('');
}

// 1コードを度数カードHTMLに変換（sectionKey が null のときは「Key未設定」表示）
function chordCardHtml(token, sectionKey) {
    if (!sectionKey) {
        return '<span class="degree-card"><span class="chord">' + escapeHtml(token)
            + '</span><span class="deg">Key未設定</span></span>';
    }
    const a = analyzeChord(token, sectionKey);
    if (!a) return '';
    return '<span class="degree-card ' + (a.diatonic ? '' : 'nondiatonic') + '">'
        + '<span class="chord">' + escapeHtml(a.chord) + '</span>'
        + '<span class="deg">' + a.degree + '</span></span>';
}

// コード解析パネルの再描画。
// 各セクションは「個別 Key（転調）」があればそれを優先し、無ければ曲全体の Key を使う。
// 左のコード入力欄と「行数・改行位置」を一致させるため、1行ずつ対応させて出力する。
function renderAnalysis() {
    const body = document.getElementById('analysis-body');
    const songKey = parseKey(musicKeyInput.value);
    const sections = [...document.querySelectorAll('.section-list[data-area="chord"] .section-card')];
    let html = '';
    sections.forEach((card) => {
        const name = card.querySelector('.section-name').value || '無題';
        const text = card.querySelector('.section-body').value || '';
        // セクションにコードが1つも無ければ表示しない
        if (!extractChords(text).length) return;
        const keyEl = card.querySelector('.section-key');
        const secKeyStr = keyEl ? keyEl.value.trim() : '';
        const sectionKey = parseKey(secKeyStr) || songKey;
        // 個別 Key が設定されている場合はバッジで明示（転調が一目で分かる）
        const keyBadge = secKeyStr
            ? '<span class="sec-key-badge">🎵 ' + escapeHtml(secKeyStr) + '</span>'
            : '';
        // 入力欄の改行で分割し、1行 = 1つの degree-line として描画
        const linesHtml = text.split('\n').map((line) => {
            const chords = extractChords(line);
            if (!chords.length) {
                // 空行（またはコードなし行）も1行ぶんの高さを確保して位置を合わせる
                return '<div class="degree-line is-blank">&nbsp;</div>';
            }
            const cards = chords.map((c) => chordCardHtml(c, sectionKey)).join('');
            return '<div class="degree-line">' + cards + '</div>';
        }).join('');
        html += '<div class="analysis-section"><h4>' + escapeHtml(name) + keyBadge + '</h4>'
            + '<div class="degree-lines">' + linesHtml + '</div></div>';
    });
    body.innerHTML = html || '<p class="diatonic-empty">コードを入力すると度数が表示されます。</p>';
}

function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;')
        .replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// Key/BPM 変更時にすべての理論表示を更新
const musicKeyInput = document.getElementById('musicKey');
const bpmInput = document.getElementById('bpm');
const cofCurrent = document.getElementById('cof-current');

function updateTheory() {
    const key = parseKey(musicKeyInput.value);
    renderDiatonicInto(document.getElementById('diatonic-panel'), key);
    renderDiatonicInto(document.getElementById('zen-diatonic'), key);
    const label = musicKeyInput.value.trim();
    document.getElementById('zen-key').textContent = label || '—';
    document.getElementById('zen-bpm').textContent = bpmInput.value.trim() || '—';
    if (cofCurrent) cofCurrent.textContent = label || '未選択';
    highlightCof();
    if (document.getElementById('chord-analysis').classList.contains('open')) {
        renderAnalysis();
    }
}

/* ---- サークル・オブ・フィフス（五度圏）UI ---- */
// 時計回りの配置（上＝C）。外周＝メジャー、内周＝相対マイナー。
const COF_MAJOR = ['C', 'G', 'D', 'A', 'E', 'B', 'Gb', 'Db', 'Ab', 'Eb', 'Bb', 'F'];
const COF_MINOR = ['Am', 'Em', 'Bm', 'F#m', 'C#m', 'G#m', 'Ebm', 'Bbm', 'Fm', 'Cm', 'Gm', 'Dm'];
const cofEl = document.getElementById('cof');
const cofButtons = [];

function buildCircleOfFifths() {
    if (!cofEl) return;
    const size = 240, c = size / 2, rMajor = 96, rMinor = 56;
    const frag = document.createDocumentFragment();

    function place(label, value, radius, isMinor, i) {
        const angle = (i * 30) * Math.PI / 180; // 0=上、時計回り
        const x = c + radius * Math.sin(angle);
        const y = c - radius * Math.cos(angle);
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'cof-key' + (isMinor ? ' minor' : '');
        btn.textContent = label;
        btn.dataset.key = value;
        btn.title = value + (isMinor ? '（マイナー）' : '（メジャー）');
        btn.style.left = x + 'px';
        btn.style.top = y + 'px';
        btn.addEventListener('click', () => selectKey(value));
        frag.appendChild(btn);
        cofButtons.push(btn);
    }

    COF_MAJOR.forEach((k, i) => place(k, k, rMajor, false, i));
    COF_MINOR.forEach((k, i) => place(k, k, rMinor, true, i));

    const center = document.createElement('div');
    center.className = 'cof-center';
    center.textContent = '五度圏';
    cofEl.appendChild(frag);
    cofEl.appendChild(center);
}

// ボタンクリックでKeyを選択（同じKeyの再クリックで解除）
function selectKey(value) {
    musicKeyInput.value = (musicKeyInput.value.trim() === value) ? '' : value;
    updateTheory();
}

// 現在のKeyに一致するボタンを選択表示（異名同音も一致させる）
function highlightCof() {
    const cur = parseKey(musicKeyInput.value);
    cofButtons.forEach((btn) => {
        const bk = parseKey(btn.dataset.key);
        const match = cur && bk && cur.minor === bk.minor && pcOf(cur.root) === pcOf(bk.root);
        btn.classList.toggle('selected', !!match);
    });
}

buildCircleOfFifths();
const cofClear = document.getElementById('cof-clear');
if (cofClear) {
    cofClear.addEventListener('click', () => { musicKeyInput.value = ''; updateTheory(); });
}
bpmInput.addEventListener('input', updateTheory);

const chordEdit = document.querySelector('.chord-edit');

// コード入力・セクション名・セクション個別 Key のリアルタイム解析
// （動的追加カードにも効くようイベント委譲）
chordEdit.addEventListener('input', (e) => {
    if (e.target.classList.contains('section-body')
        || e.target.classList.contains('section-name')
        || e.target.classList.contains('section-key')) {
        if (document.getElementById('chord-analysis').classList.contains('open')) {
            renderAnalysis();
        }
    }
});

/* ---- コード入力のハイフン自動補完（"F" → space → "F - "）----
   快適性優先：直前がコード文字のときだけ " - " を挟む。
   バックスペースや既存の区切り（- / 空白）は一切妨げない。 */
chordEdit.addEventListener('keydown', (e) => {
    if (e.key !== ' ' && e.key !== 'Spacebar') return;
    const ta = e.target;
    if (!ta.classList || !ta.classList.contains('section-body')) return;
    // 範囲選択中・IME変換中は介入しない
    if (ta.selectionStart !== ta.selectionEnd) return;
    if (e.isComposing) return;
    const pos = ta.selectionStart;
    const before = ta.value.slice(0, pos);
    // 直前がコードを構成しうる文字（英数・#・b・♯・♭・)）のときだけ補完
    if (!/[A-Za-z0-9#b♯♭)]$/.test(before)) return;
    e.preventDefault();
    const after = ta.value.slice(pos);
    const insert = ' - ';
    ta.value = before + insert + after;
    const caret = pos + insert.length;
    ta.selectionStart = ta.selectionEnd = caret;
    // value をスクリプトで書き換えると input が発火しないため明示的に再解析
    if (document.getElementById('chord-analysis').classList.contains('open')) {
        renderAnalysis();
    }
});

updateTheory();

/* =========================================================
   7) Zen Mode（全画面）
   ========================================================= */
const analysisPane = document.getElementById('chord-analysis');
const analysisReopen = document.getElementById('analysis-reopen');
const chordGutter = document.getElementById('chord-gutter');

function enterZen(area) {
    const panel = document.querySelector('.panel[data-panel="' + area + '"]');
    if (!panel) return;
    document.body.classList.add('zen', 'zen-' + area);
    panel.classList.add('zen-active');
    if (area === 'chord') {
        openAnalysis();
    }
    updateTheory();
}

function exitZen() {
    document.querySelectorAll('.panel.zen-active').forEach((p) => p.classList.remove('zen-active'));
    document.body.classList.remove('zen', 'zen-lyric', 'zen-chord');
    analysisReopen.style.display = 'none';
    chordGutter.classList.remove('show');
    // リサイズで付けたインライン幅をリセットし、次回はデフォルト比率に戻す
    chordEdit.style.flexBasis = '';
    chordEdit.style.flexGrow = '';
    analysisPane.style.flexBasis = '';
    analysisPane.style.flexGrow = '';
}

function openAnalysis() {
    analysisPane.classList.add('open');
    analysisReopen.style.display = 'none';
    // コード Zen 中のみリサイズ用ガターを表示
    if (document.body.classList.contains('zen-chord')) {
        chordGutter.classList.add('show');
    }
    renderAnalysis();
}

function closeAnalysis() {
    analysisPane.classList.remove('open');
    chordGutter.classList.remove('show');
    // コード Zen 中だけ「再表示」ボタンを出す
    if (document.body.classList.contains('zen-chord')) {
        analysisReopen.style.display = 'inline-flex';
    }
}

document.querySelectorAll('.zen-btn').forEach((btn) => {
    btn.addEventListener('click', () => enterZen(btn.getAttribute('data-area')));
});
document.getElementById('zen-close').addEventListener('click', exitZen);
document.getElementById('analysis-close').addEventListener('click', closeAnalysis);
analysisReopen.addEventListener('click', openAnalysis);

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && document.body.classList.contains('zen')) {
        // テキスト編集中でも全画面解除を優先
        exitZen();
    }
});

/* ---- コード全画面の 2画面リサイズ（入力 ⇄ ディグリー解析） ---- */
chordGutter.addEventListener('mousedown', (e) => {
    if (!analysisPane.classList.contains('open')) return;
    e.preventDefault();
    const startX = e.clientX;
    const editStart = chordEdit.getBoundingClientRect().width;
    const anaStart = analysisPane.getBoundingClientRect().width;
    const MIN = 180;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    function onMove(ev) {
        let delta = ev.clientX - startX;
        if (editStart + delta < MIN) delta = MIN - editStart;
        if (anaStart - delta < MIN) delta = anaStart - MIN;
        chordEdit.style.flexGrow = '0';
        chordEdit.style.flexBasis = (editStart + delta) + 'px';
        analysisPane.style.flexGrow = '0';
        analysisPane.style.flexBasis = (anaStart - delta) + 'px';
    }
    function onUp() {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
    }
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
});

/* =========================================================
   8) セクション構成テンプレート（保存・適用・名前変更・上書き・削除）
   ========================================================= */
const AREA_LABEL = { lyric: '歌詞', chord: 'コード' };

// テンプレ一覧ポップオーバー（1つを使い回す）
const tplMenu = document.createElement('div');
tplMenu.className = 'template-menu';
document.body.appendChild(tplMenu);

// 現在開いているメニューの対象（再描画に使う）
let tplMenuArea = null;
let tplMenuAnchor = null;

function hideTplMenu() {
    tplMenu.classList.remove('show');
    tplMenuArea = null;
    tplMenuAnchor = null;
}

// メニュー外クリックで閉じる（適用ボタン自身のクリックは除く）
document.addEventListener('click', (e) => {
    if (!tplMenu.classList.contains('show')) return;
    if (tplMenu.contains(e.target)) return;
    if (e.target.closest && e.target.closest('.tpl-apply-btn')) return;
    hideTplMenu();
});

// 「構成を保存」：現在のそのエリアのブロック構成を新規テンプレートとして保存
document.querySelectorAll('.tpl-save-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
        const area = btn.getAttribute('data-area');
        const sections = collectSections(area);
        if (!sections.length) {
            alert('保存できるブロックがありません。');
            return;
        }
        const name = prompt(AREA_LABEL[area] + '構成のテンプレート名を入力してください\n（例: 王道ポップス構成）');
        if (name === null) return;
        if (!name.trim()) {
            alert('テンプレート名を入力してください。');
            return;
        }
        postJson('/templates', {
            name: name.trim(),
            type: area.toUpperCase(),
            sections: sections
        }).then((data) => {
            if (data && data.error) { alert(data.error); return; }
            showToast('テンプレート「' + (data.name || name.trim()) + '」を保存しました');
        }).catch(() => alert('テンプレートの保存に失敗しました。'));
    });
});

// 「テンプレ適用」：保存済みテンプレ一覧をポップオーバーで表示
document.querySelectorAll('.tpl-apply-btn').forEach((btn) => {
    btn.addEventListener('click', () => openTplMenu(btn, btn.getAttribute('data-area')));
});

// 一覧を取得してメニューを描画（rename/overwrite/delete 後の再描画にも使う）
function openTplMenu(anchorBtn, area) {
    tplMenuArea = area;
    tplMenuAnchor = anchorBtn;
    fetch('/templates?type=' + area.toUpperCase())
        .then((res) => {
            if (!res.ok) throw new Error('list failed');
            return res.json();
        })
        .then((list) => renderTplMenu(list))
        .catch(() => alert('テンプレートの取得に失敗しました。'));
}

function renderTplMenu(list) {
    const area = tplMenuArea;
    let html = '<div class="tm-title">' + AREA_LABEL[area] + '構成テンプレート</div>';
    if (!list.length) {
        html += '<div class="tm-empty">保存済みテンプレートはありません。<br>「構成を保存」から作成できます。</div>';
    } else {
        html += list.map((t) =>
            '<div class="tm-row" data-id="' + t.id + '" data-name="' + escapeHtml(t.name) + '">'
            + '<button type="button" class="tm-item" data-act="apply">'
            + '<span>' + escapeHtml(t.name) + '</span>'
            + '<span class="tm-count">' + (t.shared ? '共有・' : '') + t.count + 'ブロック</span></button>'
            // 共有テンプレートは閲覧・適用のみ（名前変更・上書き・削除はできない）
            + (t.shared ? '' : '<span class="tm-actions">'
            + '<button type="button" class="tm-act" data-act="rename" title="名前を変更">✏️</button>'
            + '<button type="button" class="tm-act" data-act="overwrite" title="現在の構成で上書き保存">⬆️</button>'
            + '<button type="button" class="tm-act" data-act="delete" title="削除">🗑️</button>'
            + '</span>')
            + '</div>'
        ).join('');
    }
    tplMenu.innerHTML = html;
    // アンカー（適用ボタン）の真下に配置。画面右端からはみ出さないよう調整
    const rect = tplMenuAnchor.getBoundingClientRect();
    tplMenu.classList.add('show');
    const menuWidth = tplMenu.offsetWidth;
    let left = rect.left;
    if (left + menuWidth > window.innerWidth - 8) {
        left = Math.max(8, window.innerWidth - menuWidth - 8);
    }
    tplMenu.style.left = left + 'px';
    tplMenu.style.top = (rect.bottom + 6) + 'px';
}

// メニュー内のクリックをまとめて処理（イベント委譲）
tplMenu.addEventListener('click', (e) => {
    const actBtn = e.target.closest('[data-act]');
    if (!actBtn) return;
    const row = actBtn.closest('.tm-row');
    if (!row) return;
    const id = row.getAttribute('data-id');
    const name = row.getAttribute('data-name');
    const act = actBtn.getAttribute('data-act');
    const area = tplMenuArea;

    if (act === 'apply') {
        hideTplMenu();
        applyTemplateById(area, id);
    } else if (act === 'rename') {
        const newName = prompt('テンプレート名を変更します', name);
        if (newName === null || !newName.trim()) return;
        postJson('/templates/' + id + '/rename', { name: newName.trim() })
            .then((data) => {
                if (data && data.error) { alert(data.error); return; }
                showToast('名前を変更しました');
                openTplMenu(tplMenuAnchor, area);
            }).catch(() => alert('名前の変更に失敗しました。'));
    } else if (act === 'overwrite') {
        const sections = collectSections(area);
        if (!sections.length) { alert('保存できるブロックがありません。'); return; }
        if (!confirm('テンプレート「' + name + '」を、現在の' + AREA_LABEL[area]
            + '構成（' + sections.length + 'ブロック）で上書きします。よろしいですか？')) return;
        postJson('/templates/' + id + '/overwrite', {
            type: area.toUpperCase(),
            sections: sections
        }).then((data) => {
            if (data && data.error) { alert(data.error); return; }
            showToast('テンプレート「' + name + '」を上書きしました');
            openTplMenu(tplMenuAnchor, area);
        }).catch(() => alert('上書きに失敗しました。'));
    } else if (act === 'delete') {
        if (!confirm('テンプレート「' + name + '」を削除します。よろしいですか？')) return;
        fetch('/templates/' + id, { method: 'DELETE' })
            .then((res) => {
                if (!res.ok) throw new Error('delete failed');
                showToast('テンプレートを削除しました');
                openTplMenu(tplMenuAnchor, area);
            }).catch(() => alert('削除に失敗しました。'));
    }
});

function applyTemplateById(area, id) {
    fetch('/templates/' + id)
        .then((res) => {
            if (!res.ok) throw new Error('load failed');
            return res.json();
        })
        .then((data) => {
            const sections = data.sections || [];
            if (!sections.length) {
                alert('このテンプレートには適用できるブロックがありません。');
                return;
            }
            const existing = getSectionList(area).querySelectorAll('.section-card').length;
            if (existing > 0 &&
                !confirm('現在の' + AREA_LABEL[area] + 'ブロック（' + existing
                    + '件）をテンプレートの構成に置き換えます。よろしいですか？\n'
                    + '※「変更を保存」を押すまでデータベースには反映されません。')) {
                return;
            }
            const list = getSectionList(area);
            list.innerHTML = '';
            sections.forEach((s) => list.appendChild(buildCard(area, s)));
            if (area === 'chord'
                && document.getElementById('chord-analysis').classList.contains('open')) {
                renderAnalysis();
            }
            showToast('テンプレート「' + (data.name || '') + '」を適用しました');
        })
        .catch(() => alert('テンプレートの適用に失敗しました。'));
}

// JSON POST 共通処理（成功・業務エラーともに本文を返す）
function postJson(url, payload) {
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).then((res) => res.json().catch(() => ({})));
}

// 汎用トースト（保存トーストを使い回す）
function showToast(message) {
    const toast = document.getElementById('save-toast');
    toast.textContent = message;
    toast.style.display = 'block';
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => { toast.style.display = 'none'; }, 2200);
}

/* =========================================================
   9) セクションの最小化（アコーディオン）
   各ブロックのヘッダー右端の ▼/▲ で本文を折りたたむ。
   ========================================================= */
function addMinToggle(headerEl, bodyEls) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'min-btn';
    btn.textContent = '▼';
    btn.title = '最小化 / 展開';
    btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const collapsed = headerEl.classList.toggle('section-collapsed');
        bodyEls.forEach((el) => { el.style.display = collapsed ? 'none' : ''; });
        btn.textContent = collapsed ? '▲' : '▼';
    });
    headerEl.appendChild(btn);
}

// 情報パネルの各カード（デモ音源 / BPM / Key / 世界観 / 進捗率）
document.querySelectorAll('.panel[data-panel="info"] .card').forEach((card) => {
    const header = card.querySelector('.card-header');
    const body = card.querySelector('.card-body');
    if (!header || !body) return;
    header.classList.add('collapsible-header');
    addMinToggle(header, [body]);
});

// 歌詞 / コードのエリア（ヘッダー以降のコンテンツをまとめて折りたたむ）
document.querySelectorAll('.panel[data-panel="lyric"], .panel[data-panel="chord"]').forEach((panel) => {
    const header = panel.querySelector('.area-header');
    if (!header) return;
    const bodyEls = [...panel.children].filter((el) => el !== header);
    addMinToggle(header, bodyEls);
});

/* =========================================================
   10) モバイルタブ切り替え
   ========================================================= */
(function () {
    const tabNav = document.getElementById('mobile-tab-nav');
    if (!tabNav) return;

    function activateTab(area) {
        tabNav.querySelectorAll('[data-tab]').forEach((b) => b.classList.remove('active'));
        const btn = tabNav.querySelector('[data-tab="' + area + '"]');
        if (btn) btn.classList.add('active');
        document.querySelectorAll('#split .panel').forEach((p) => p.classList.remove('tab-active'));
        const panel = document.querySelector('.panel[data-panel="' + area + '"]');
        if (panel) panel.classList.add('tab-active');
    }

    tabNav.querySelectorAll('[data-tab]').forEach((btn) => {
        btn.addEventListener('click', () => activateTab(btn.dataset.tab));
    });

    function initTabs() {
        if (window.innerWidth < 768) activateTab('lyric');
    }
    initTabs();

    window.addEventListener('resize', () => {
        if (window.innerWidth >= 768) {
            document.querySelectorAll('#split .panel').forEach((p) => p.classList.remove('tab-active'));
        } else {
            const active = tabNav.querySelector('.nav-link.active');
            if (active) activateTab(active.dataset.tab);
        }
    });

    // モバイル固定保存ボタンはデスクトップ側の save-all に委譲
    const mobileBtn = document.getElementById('save-all-mobile');
    if (mobileBtn) {
        mobileBtn.addEventListener('click', () => document.getElementById('save-all').click());
    }
})();
