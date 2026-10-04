/* 作曲コア（スタジオ）画面 songs/detail.html のスクリプト */
const SAVE_URL = document.body.getAttribute('data-save-url');

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

// 歌詞カードを1枚生成して返す。
// data = { name, content } で初期値を流し込める（テンプレ適用に使用）。
function buildCard(area, data) {
    data = data || {};
    const node = template.content.firstElementChild.cloneNode(true);
    node.querySelector('.section-name').value = data.name || '';
    const body = node.querySelector('.section-body');
    body.value = data.content || '';
    body.placeholder = '歌詞を入力...';
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
   3) 進捗（ヘッダーの横長ブロック。数値入力とスライダーの連動）
   ========================================================= */
function clampPct(v) {
    if (isNaN(v)) return 0;
    return Math.max(0, Math.min(100, Math.round(v)));
}

function bindMetric(numId) {
    const num = document.getElementById(numId);
    const range = document.querySelector('.pct-range[data-for="' + numId + '"]');

    function apply(value, source) {
        const v = clampPct(value);
        if (source !== 'num') num.value = v;
        if (source !== 'range') range.value = v;
        updateOverall();
    }
    num.addEventListener('input', () => apply(parseInt(num.value, 10), 'num'));
    num.addEventListener('blur', () => apply(parseInt(num.value, 10), 'blur'));
    range.addEventListener('input', () => apply(parseInt(range.value, 10), 'range'));
}

function updateOverall() {
    const l = clampPct(parseInt(document.getElementById('lyricProgress').value, 10));
    const a = clampPct(parseInt(document.getElementById('arrangementProgress').value, 10));
    const overall = Math.round((l + a) / 2);
    document.getElementById('overallVal').textContent = overall;
    document.getElementById('overallBar').style.width = overall + '%';
}

bindMetric('lyricProgress');
bindMetric('arrangementProgress');

/* =========================================================
   4) デモ音源（ヘッダー右上）：ファイルを選んだらすぐアップロード
   ========================================================= */
const audioFile = document.getElementById('audio-file');
document.getElementById('audio-pick').addEventListener('click', () => audioFile.click());
audioFile.addEventListener('change', () => {
    if (!audioFile.files.length) return;
    // アップロードは画面を再読み込みするため、未保存の変更があれば先に確認する
    if (!confirm('「' + audioFile.files[0].name + '」をアップロードします。\n'
        + '保存していない変更は失われます。先に「変更を保存」してください。よろしいですか？')) {
        audioFile.value = '';
        return;
    }
    ChordEditor.markSaved();
    MusicMeta.markSaved();
    document.getElementById('audio-form').submit();
});

/* =========================================================
   5) 一括保存（変更を保存）
   ========================================================= */
function collectSections(area) {
    const list = getSectionList(area);
    return [...list.querySelectorAll('.section-card')].map((card) => ({
        id: card.dataset.id ? parseInt(card.dataset.id, 10) : null,
        name: card.querySelector('.section-name').value,
        content: card.querySelector('.section-body').value
    }));
}

function gather() {
    return {
        musicKey: MusicMeta.key(),
        bpm: MusicMeta.bpm(),
        lyricProgress: clampPct(parseInt(document.getElementById('lyricProgress').value, 10)),
        arrangementProgress: clampPct(parseInt(document.getElementById('arrangementProgress').value, 10)),
        lyricSections: collectSections('lyric'),
        chordSheet: ChordEditor.getSheet()
    };
}

const saveBtn = document.getElementById('save-all');
const saveStatus = document.getElementById('save-status');

saveBtn.addEventListener('click', () => {
    if (!MusicMeta.validate()) {
        alert('BPM は 1〜999 の数値で入力してください。');
        document.getElementById('bpm-input').focus();
        return;
    }
    saveBtn.disabled = true;
    saveStatus.textContent = '保存中...';
    fetch(SAVE_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(gather())
    }).then((res) => res.json().catch(() => ({})).then((data) => {
        if (!res.ok) throw new Error(data.error || '保存に失敗しました。通信状況を確認してください。');
        return data;
    })).then(() => {
        // 新しく採番されたID等を反映するためリロード
        ChordEditor.markSaved();
        MusicMeta.markSaved();
        sessionStorage.setItem('mt-saved', '1');
        location.reload();
    }).catch((err) => {
        saveBtn.disabled = false;
        saveStatus.textContent = '';
        alert(err.message);
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
   6) コード譜エディタ（Excel テンプレートを表計算で直接編集）
   ・テンプレートから作成 / Excel・PDF のインポート → 画面の表に読み込む（保存は「変更を保存」）
   ・エクスポートは表示中の表をサーバーで Excel / PDF に変換してダウンロード
   ========================================================= */
const ChordEditor = (() => {
    const BASE_URL = document.body.getAttribute('data-chord-url');
    const container = document.getElementById('chord-sheet');
    const emptyEl = document.getElementById('chart-empty');
    const loadingEl = document.getElementById('chart-loading');
    const panel = container.closest('.panel');
    let worksheet = null;
    // タブが非表示の間に読み込んだ表（表示されたときに描画する）。描画前でも保存・出力に使う
    let pending = null;
    // 未保存の変更があるか（ページを離れるときの確認に使う）
    let dirty = false;
    // 読み込み直後の行の高さ調整などを「変更」と数えないためのフラグ
    let loading = false;

    function markDirty() {
        if (!loading) dirty = true;
    }

    function showState() {
        const has = !!worksheet || !!pending;
        emptyEl.hidden = has;
        container.hidden = !has;
        document.querySelectorAll('.chart-needs-sheet').forEach((b) => { b.disabled = !has; });
    }

    function destroy() {
        if (worksheet) {
            jspreadsheet.destroy(container);
            worksheet = null;
        }
        container.innerHTML = '';
    }

    // サーバーの表データ（ChordSheet）をエディタに読み込む。sheet が null なら空の状態にする。
    // コード譜タブが非表示のときは描画を後回しにする（非表示のまま描画すると幅の計算が崩れるため）。
    function load(sheet, changed) {
        destroy();
        pending = null;
        if (sheet && panel.hidden) {
            pending = sheet;
            dirty = !!changed;
            loadingEl.hidden = true;
            showState();
            return;
        }
        loading = true;
        if (sheet) {
            const spreadsheet = jspreadsheet(container, {
                toolbar: true,
                worksheets: [{
                    data: sheet.data,
                    style: sheet.style,
                    mergeCells: sheet.mergeCells,
                    columns: sheet.colWidths.map((w) => ({ type: 'text', width: w, align: 'left' })),
                    minDimensions: [sheet.colWidths.length, sheet.rowHeights.length],
                    tableOverflow: true,
                    tableWidth: '100%',
                    tableHeight: '70vh',
                    allowComments: false
                }],
                onchange: markDirty,
                oninsertrow: markDirty,
                ondeleterow: markDirty,
                oninsertcolumn: markDirty,
                ondeletecolumn: markDirty,
                onmoverow: markDirty,
                onmovecolumn: markDirty,
                onresizerow: markDirty,
                onresizecolumn: markDirty,
                onmerge: markDirty,
                onchangestyle: markDirty,
                onpaste: markDirty,
                onundo: markDirty,
                onredo: markDirty
            });
            worksheet = spreadsheet[0];
            // 行の高さは初期設定では反映されないため、読み込み後に1行ずつ設定する
            sheet.rowHeights.forEach((h, i) => worksheet.setHeight(i, h));
        }
        loading = false;
        dirty = !!changed;
        loadingEl.hidden = true;
        showState();
    }

    // エディタの内容をサーバーに送る形（ChordSheet）にする。コード譜がなければ null。
    function getSheet() {
        if (!worksheet) return pending;
        const data = worksheet.getData();
        const heights = data.map((_, i) => parseInt(worksheet.getHeight(i), 10) || 0);
        return {
            data: data,
            style: worksheet.getStyle(),
            mergeCells: worksheet.getMerge(),
            colWidths: worksheet.getWidth().map((w) => parseInt(w, 10) || 0),
            rowHeights: heights
        };
    }

    // JSON を返す API 呼び出し（エラー時はサーバーのメッセージで例外にする）
    function requestJson(url, options) {
        return fetch(url, options).then((res) => res.json().catch(() => ({})).then((body) => {
            if (!res.ok) throw new Error(body.error || '処理に失敗しました。');
            return body;
        }));
    }

    function confirmReplace() {
        return (!worksheet && !pending) || confirm('表示中のコード譜を置き換えます。よろしいですか？\n'
            + '※「変更を保存」を押すまで保存されません。');
    }

    function createFromTemplate(name, label) {
        if (!confirmReplace()) return;
        requestJson(BASE_URL + '/template?name=' + encodeURIComponent(name), { method: 'POST' })
            .then((sheet) => {
                load(sheet, true);
                showToast(label + 'で作成しました（「変更を保存」で保存）');
            })
            .catch((e) => alert(e.message));
    }

    function importFile(file) {
        if (!confirmReplace()) return;
        const form = new FormData();
        form.append('file', file);
        showToast('「' + file.name + '」を読み込んでいます...');
        requestJson(BASE_URL + '/import', { method: 'POST', body: form })
            .then((sheet) => {
                load(sheet, true);
                showToast('「' + file.name + '」を読み込みました（「変更を保存」で保存）');
            })
            .catch((e) => alert(e.message));
    }

    function fileNameFrom(disposition, format) {
        const m = (disposition || '').match(/filename\*=UTF-8''([^;]+)/i);
        return m ? decodeURIComponent(m[1]) : 'chord-chart.' + format;
    }

    function exportAs(format) {
        const sheet = getSheet();
        if (!sheet) return;
        // タイトル下の Key / BPM 欄には、画面で選んでいる値を入れて出力する
        const meta = '&key=' + encodeURIComponent(MusicMeta.key())
            + (MusicMeta.bpm() != null ? '&bpm=' + MusicMeta.bpm() : '');
        fetch(BASE_URL + '/export?format=' + format + meta, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(sheet)
        }).then((res) => {
            if (!res.ok) {
                return res.json().catch(() => ({})).then((b) => {
                    throw new Error(b.error || 'エクスポートに失敗しました。');
                });
            }
            const name = fileNameFrom(res.headers.get('Content-Disposition'), format);
            return res.blob().then((blob) => ({ blob: blob, name: name }));
        }).then(({ blob, name }) => {
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = name;
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(a.href), 1000);
        }).catch((e) => alert(e.message));
    }

    function clear() {
        if (!worksheet) return;
        if (!confirm('このコード譜を削除します。よろしいですか？\n※「変更を保存」を押すと確定します。')) return;
        destroy();
        dirty = true;
        showState();
    }

    // ---- ボタン・メニュー ----
    document.querySelectorAll('[data-menu]').forEach((btn) => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const menu = document.getElementById(btn.getAttribute('data-menu'));
            const open = !menu.classList.contains('show');
            document.querySelectorAll('.chart-menu.show').forEach((m) => m.classList.remove('show'));
            menu.classList.toggle('show', open);
        });
    });
    document.addEventListener('click', () => {
        document.querySelectorAll('.chart-menu.show').forEach((m) => m.classList.remove('show'));
    });
    document.querySelectorAll('.chart-new-btn').forEach((btn) => {
        btn.addEventListener('click', () => {
            const label = btn.textContent.replace(/で作成$/, '').trim();
            createFromTemplate(btn.getAttribute('data-template'), label);
        });
    });
    document.querySelectorAll('.chart-export-btn').forEach((btn) => {
        btn.addEventListener('click', () => exportAs(btn.getAttribute('data-format')));
    });
    const importInput = document.getElementById('chart-import-file');
    document.getElementById('chart-import-btn').addEventListener('click', () => importInput.click());
    importInput.addEventListener('change', () => {
        if (importInput.files.length) importFile(importInput.files[0]);
        importInput.value = '';
    });
    document.getElementById('chart-clear-btn').addEventListener('click', clear);

    window.addEventListener('beforeunload', (e) => {
        if (dirty) {
            e.preventDefault();
            e.returnValue = '';
        }
    });

    // 保存済みのコード譜を読み込む
    requestJson(BASE_URL, {})
        .then((body) => load(body.sheet, false))
        .catch((e) => {
            loadingEl.textContent = 'コード譜の読み込みに失敗しました: ' + e.message;
        });

    // コード譜タブが表示されたら、後回しにしていた表を描画する（未保存の変更状態は引き継ぐ）
    function onShow() {
        if (pending && !panel.hidden) {
            load(pending, dirty);
        }
    }

    // Key / BPM 欄（「Key：…」のセル）を画面の Key・BPM に合わせて書き換える
    const KEY_BPM_CELL = /^\s*key\s*[:：]/i;
    function updateKeyBpm(text) {
        const data = worksheet ? worksheet.getData() : (pending ? pending.data : null);
        if (!data) return;
        for (let r = 0; r < data.length; r++) {
            for (let c = 0; c < data[r].length; c++) {
                if (KEY_BPM_CELL.test(String(data[r][c] || ''))) {
                    if (data[r][c] === text) return;
                    if (worksheet) worksheet.setValueFromCoords(c, r, text);
                    else data[r][c] = text;
                    dirty = true;
                    return;
                }
            }
        }
    }

    return {
        getSheet: getSheet,
        onShow: onShow,
        updateKeyBpm: updateKeyBpm,
        markSaved: () => { dirty = false; }
    };
})();

/* =========================================================
   7) コード譜 / 歌詞の切り替えタブ（画面を開いたときはコード譜）
   ========================================================= */
const studioTabs = [...document.querySelectorAll('.studio-tab')];

function activateTab(area) {
    studioTabs.forEach((btn) => {
        const active = btn.dataset.tab === area;
        btn.classList.toggle('active', active);
        btn.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    document.querySelectorAll('#studio .panel').forEach((panel) => {
        panel.hidden = panel.dataset.panel !== area;
    });
    if (area === 'chord') {
        // 非表示の間は表を描画しないため、開いたときに描画する
        ChordEditor.onShow();
    }
}

studioTabs.forEach((btn) => btn.addEventListener('click', () => activateTab(btn.dataset.tab)));
activateTab('chord');

// モバイル固定保存ボタンはデスクトップ側の save-all に委譲
document.getElementById('save-all-mobile')
    .addEventListener('click', () => document.getElementById('save-all').click());

/* =========================================================
   8) 汎用トースト（保存トーストを使い回す）
   ========================================================= */
function showToast(message) {
    const toast = document.getElementById('save-toast');
    toast.textContent = message;
    toast.style.display = 'block';
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => { toast.style.display = 'none'; }, 2200);
}

/* =========================================================
   9) 歌詞の書き出し（Word / テキスト / PDF）
   画面に表示中の歌詞（未保存の編集も含む）をサーバーでファイルにしてダウンロードする。
   ========================================================= */
const LYRICS_EXPORT_URL = SAVE_URL.replace(/\/save$/, '/lyrics/export');

function exportLyrics(format) {
    fetch(LYRICS_EXPORT_URL + '?format=' + format, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            sections: collectSections('lyric'),
            musicKey: MusicMeta.key(),
            bpm: MusicMeta.bpm()
        })
    }).then((res) => {
        if (!res.ok) {
            return res.json().catch(() => ({})).then((b) => {
                throw new Error(b.error || '書き出しに失敗しました。');
            });
        }
        const m = (res.headers.get('Content-Disposition') || '').match(/filename\*=UTF-8''([^;]+)/i);
        const name = m ? decodeURIComponent(m[1]) : 'lyrics.' + format;
        return res.blob().then((blob) => ({ blob: blob, name: name }));
    }).then(({ blob, name }) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    }).catch((e) => alert(e.message));
}

document.querySelectorAll('.lyrics-export-btn').forEach((btn) => {
    btn.addEventListener('click', () => exportLyrics(btn.getAttribute('data-format')));
});

/* =========================================================
   10) Key（五度圏のポップアップから選ぶ）と BPM（数値入力）
   「変更を保存」で保存する。コード譜の Key / BPM 欄も合わせて書き換える。
   ========================================================= */
const MusicMeta = (() => {
    // 五度圏（上の C から時計回り）。外側がメジャー、内側が平行調のマイナー
    const MAJORS = ['C', 'G', 'D', 'A', 'E', 'B', 'F#', 'Db', 'Ab', 'Eb', 'Bb', 'F'];
    const MINORS = ['Am', 'Em', 'Bm', 'F#m', 'C#m', 'G#m', 'Ebm', 'Bbm', 'Fm', 'Cm', 'Gm', 'Dm'];
    const SVG_NS = 'http://www.w3.org/2000/svg';
    const C = 150;
    const RING = { outer: 146, middle: 101, inner: 58 };

    const keyBtn = document.getElementById('key-btn');
    const keyValue = document.getElementById('key-value');
    const popover = document.getElementById('key-popover');
    const svg = document.getElementById('circle-of-fifths');
    const bpmInput = document.getElementById('bpm-input');

    const saved = { key: keyValue.textContent.trim() || 'C', bpm: bpmInput.value };
    let currentKey = saved.key;

    function point(r, deg) {
        const rad = deg * Math.PI / 180;
        return (C + r * Math.cos(rad)).toFixed(2) + ' ' + (C + r * Math.sin(rad)).toFixed(2);
    }

    // 外径 ro・内径 ri の扇形（中心角 center ±15°）
    function sector(ro, ri, center) {
        const a0 = center - 15;
        const a1 = center + 15;
        return 'M ' + point(ro, a0) + ' A ' + ro + ' ' + ro + ' 0 0 1 ' + point(ro, a1)
            + ' L ' + point(ri, a1) + ' A ' + ri + ' ' + ri + ' 0 0 0 ' + point(ri, a0) + ' Z';
    }

    function el(name, attrs, text) {
        const node = document.createElementNS(SVG_NS, name);
        Object.keys(attrs).forEach((k) => node.setAttribute(k, attrs[k]));
        if (text != null) node.textContent = text;
        return node;
    }

    function build() {
        const rings = [
            { keys: MAJORS, cls: 'major', ro: RING.outer, ri: RING.middle, kind: 'メジャー' },
            { keys: MINORS, cls: 'minor', ro: RING.middle, ri: RING.inner, kind: 'マイナー' }
        ];
        rings.forEach((ring) => {
            ring.keys.forEach((key, i) => {
                const center = -90 + i * 30;
                const seg = el('path', {
                    d: sector(ring.ro, ring.ri, center),
                    class: 'cof-seg ' + ring.cls,
                    tabindex: '0',
                    role: 'button',
                    'data-key': key,
                    'aria-label': key + '（' + ring.kind + '）'
                });
                const labelR = (ring.ro + ring.ri) / 2;
                const [x, y] = point(labelR, center).split(' ');
                const label = el('text', { x: x, y: y, class: 'cof-text ' + ring.cls, 'data-key': key }, key);
                seg.addEventListener('click', () => choose(key));
                seg.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        choose(key);
                    }
                });
                svg.appendChild(seg);
                svg.appendChild(label);
            });
        });
        svg.appendChild(el('circle', { cx: C, cy: C, r: RING.inner - 4, class: 'cof-center' }));
        svg.appendChild(el('text', { x: C, y: C - 9, class: 'cof-center-text' }, '選択中'));
        const now = el('text', { x: C, y: C + 10, class: 'cof-text major', id: 'cof-now' }, currentKey);
        svg.appendChild(now);
    }

    function highlight() {
        svg.querySelectorAll('[data-key]').forEach((node) => {
            node.classList.toggle('selected', node.getAttribute('data-key') === currentKey);
        });
        const now = document.getElementById('cof-now');
        if (now) now.textContent = currentKey;
    }

    function open() {
        popover.hidden = false;
        keyBtn.setAttribute('aria-expanded', 'true');
        highlight();
        const selected = svg.querySelector('.cof-seg.selected') || svg.querySelector('.cof-seg');
        if (selected) selected.focus();
    }

    function close(focusButton) {
        if (popover.hidden) return;
        popover.hidden = true;
        keyBtn.setAttribute('aria-expanded', 'false');
        if (focusButton) keyBtn.focus();
    }

    function choose(key) {
        currentKey = key;
        keyValue.textContent = key;
        highlight();
        close(true);
        syncChart();
    }

    function bpm() {
        const v = bpmInput.value.trim();
        if (v === '') return null;
        const n = Number(v);
        return Number.isInteger(n) ? n : null;
    }

    function validate() {
        const n = bpm();
        const ok = n != null && n >= 1 && n <= 999;
        bpmInput.classList.toggle('invalid', !ok);
        return ok;
    }

    // コード譜の Key / BPM 欄を画面の値に合わせる（BPM が正しいときだけ）
    function syncChart() {
        if (validate()) {
            ChordEditor.updateKeyBpm('Key：' + currentKey + '　BPM：' + bpm());
        }
    }

    build();
    keyBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (popover.hidden) open();
        else close(false);
    });
    popover.addEventListener('click', (e) => e.stopPropagation());
    document.addEventListener('click', () => close(false));
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') close(true);
    });

    // BPM はキーボードで入力。数字以外は受け付けない
    bpmInput.addEventListener('keydown', (e) => {
        if (['e', 'E', '+', '-', '.'].includes(e.key)) e.preventDefault();
        if (e.key === 'Enter') bpmInput.blur();
    });
    bpmInput.addEventListener('input', () => {
        bpmInput.classList.remove('invalid');
    });
    bpmInput.addEventListener('change', syncChart);
    bpmInput.addEventListener('blur', validate);

    // 未保存の Key / BPM の変更があれば、ページを離れるときに確認する
    let saving = false;
    window.addEventListener('beforeunload', (e) => {
        if (!saving && (currentKey !== saved.key || bpmInput.value.trim() !== String(saved.bpm))) {
            e.preventDefault();
            e.returnValue = '';
        }
    });

    return {
        key: () => currentKey,
        bpm: bpm,
        validate: validate,
        markSaved: () => { saving = true; }
    };
})();
