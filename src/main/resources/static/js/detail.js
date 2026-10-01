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
    return [...list.querySelectorAll('.section-card')].map((card) => ({
        id: card.dataset.id ? parseInt(card.dataset.id, 10) : null,
        name: card.querySelector('.section-name').value,
        content: card.querySelector('.section-body').value
    }));
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
        chordSheet: ChordEditor.getSheet()
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
    }).then((res) => res.json().catch(() => ({})).then((data) => {
        if (!res.ok) throw new Error(data.error || '保存に失敗しました。通信状況を確認してください。');
        return data;
    })).then(() => {
        // 新しく採番されたID等を反映するためリロード（リサイズ幅は localStorage で維持）
        ChordEditor.markSaved();
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
    let worksheet = null;
    // 未保存の変更があるか（ページを離れるときの確認に使う）
    let dirty = false;
    // 読み込み直後の行の高さ調整などを「変更」と数えないためのフラグ
    let loading = false;

    function markDirty() {
        if (!loading) dirty = true;
    }

    function showState() {
        const has = !!worksheet;
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
    function load(sheet, changed) {
        destroy();
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
        if (!worksheet) return null;
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
        return !worksheet || confirm('表示中のコード譜を置き換えます。よろしいですか？\n'
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
        fetch(BASE_URL + '/export?format=' + format, {
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

    return {
        getSheet: getSheet,
        markSaved: () => { dirty = false; }
    };
})();

function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;')
        .replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/* =========================================================
   7) Zen Mode（全画面）
   ========================================================= */
const musicKeyInput = document.getElementById('musicKey');
const bpmInput = document.getElementById('bpm');

// 全画面時に右下へ出す BPM / Key を更新
function updateZenInfo() {
    document.getElementById('zen-key').textContent = musicKeyInput.value.trim() || '—';
    document.getElementById('zen-bpm').textContent = bpmInput.value.trim() || '—';
}
musicKeyInput.addEventListener('input', updateZenInfo);
bpmInput.addEventListener('input', updateZenInfo);
updateZenInfo();

function enterZen(area) {
    const panel = document.querySelector('.panel[data-panel="' + area + '"]');
    if (!panel) return;
    document.body.classList.add('zen', 'zen-' + area);
    panel.classList.add('zen-active');
    updateZenInfo();
}

function exitZen() {
    document.querySelectorAll('.panel.zen-active').forEach((p) => p.classList.remove('zen-active'));
    document.body.classList.remove('zen', 'zen-lyric', 'zen-chord');
}

document.querySelectorAll('.zen-btn').forEach((btn) => {
    btn.addEventListener('click', () => enterZen(btn.getAttribute('data-area')));
});
document.getElementById('zen-close').addEventListener('click', exitZen);

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && document.body.classList.contains('zen')) {
        // テキスト編集中でも全画面解除を優先
        exitZen();
    }
});

/* =========================================================
   8) セクション構成テンプレート（保存・適用・名前変更・上書き・削除）
   ========================================================= */
const AREA_LABEL = { lyric: '歌詞' };

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
