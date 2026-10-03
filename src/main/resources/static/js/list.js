/* 曲一覧（ホーム）画面 songs/list.html のスクリプト */

function esc(s) {
    return (s == null ? '' : String(s))
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function showToast(text, ok) {
    const toast = document.getElementById('inline-toast');
    toast.textContent = text;
    toast.className = 'alert inline-toast shadow ' + (ok ? 'alert-success' : 'alert-danger');
    toast.style.display = 'block';
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { toast.style.display = 'none'; }, 2000);
}

// JSON を送り、エラー時はサーバーのメッセージで例外にする
function postJson(url, payload, method) {
    return fetch(url, {
        method: method || 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payload === undefined ? undefined : JSON.stringify(payload)
    }).then((res) => res.json().catch(() => ({})).then((data) => {
        if (!res.ok) throw new Error(data.error || '保存に失敗しました');
        return data;
    }));
}

/* =========================================================
   1) インライン編集（曲名・ステータス・タグ・納期・完了チェック）
   ========================================================= */

// yyyy-MM-dd → yyyy/MM/dd
function formatDate(iso) {
    return iso ? iso.replace(/-/g, '/') : '';
}

// 納期の超過状態に合わせて行（PC）とカード（スマホ）の色を切り替える
function applyOverdue(songId, overdue) {
    document.querySelectorAll('[data-song-id="' + songId + '"]').forEach((el) => {
        el.classList.toggle(el.tagName === 'TR' ? 'table-danger' : 'overdue', !!overdue);
    });
}

// 編集後にセルの表示を描き直す
function renderCell(td, field, data) {
    if (field === 'title') {
        td.dataset.value = data.title;
        td.innerHTML = '<span class="cell-value">' + esc(data.title) + '</span>';
    } else if (field === 'deadline') {
        td.dataset.value = data.deadlineDate || '';
        if (data.deadline) {
            const label = data.deadlineDate ? formatDate(data.deadlineDate) : data.deadline;
            td.innerHTML = '<span class="cell-value small">' + esc(label)
                + (data.overdue ? ' <span class="badge bg-danger overdue-badge ms-1">'
                    + (-data.daysUntil) + '日超過</span>' : '')
                + '</span>';
        } else {
            td.innerHTML = '<span class="cell-value small"><span class="cell-empty">（未設定）</span></span>';
        }
    } else if (field === 'status') {
        td.dataset.value = data.statusName;
        td.innerHTML = '<span class="cell-value badge ' + esc(data.statusColorClass) + '">'
            + esc(data.statusLabel) + '</span>';
    } else if (field === 'tagId') {
        const tags = data.tags || [];
        td.dataset.value = tags.length ? tags[0].id : '';
        td.innerHTML = '<span class="cell-value">' + (tags.length
            ? tags.map((t) => '<span class="badge bg-light text-dark border me-1">' + esc(t.name) + '</span>').join('')
            : '<span class="cell-empty">（なし）</span>') + '</span>';
    }
}

// 1項目をサーバーに保存する。成功したら更新後の曲情報を返す
function saveField(songId, field, value) {
    return postJson('/songs/' + songId + '/field', { field: field, value: value })
        .then((data) => {
            if (movesSection(songId, data.deadlineDone)) {
                // 「完了」になった／外れた曲は、楽曲一覧とバックアップ一覧の間で移動させる
                sessionStorage.setItem('mt-list-toast', data.deadlineDone
                    ? '「' + data.title + '」を完了にして、バックアップ一覧へ移動しました'
                    : '「' + data.title + '」を楽曲一覧に戻しました');
                location.reload();
                return new Promise(() => {});
            }
            applyOverdue(songId, data.overdue);
            showToast('保存しました', true);
            return data;
        });
}

// 完了状態と、いま表示されているセクション（バックアップ一覧か）が食い違うか
function movesSection(songId, completed) {
    const el = document.querySelector('[data-song-id="' + songId + '"]');
    const section = el && el.closest('[data-archive]');
    return !!section && (section.dataset.archive === 'true') !== !!completed;
}

// 移動後（再読み込み後）のトースト
(function showPendingToast() {
    const message = sessionStorage.getItem('mt-list-toast');
    if (message) {
        sessionStorage.removeItem('mt-list-toast');
        showToast(message, true);
    }
})();

function commit(td, field, value, originalHTML) {
    const songId = td.closest('[data-song-id]').dataset.songId;
    saveField(songId, field, value)
        .then((data) => renderCell(td, field, data))
        .catch((e) => {
            td.innerHTML = originalHTML;
            showToast(e.message || '通信に失敗しました', false);
        });
}

/* ---- ステータス・タグ：ワンクリックで選択ポップアップを開く ---- */
const pickMenu = document.createElement('div');
pickMenu.className = 'pick-menu';
pickMenu.hidden = true;
pickMenu.setAttribute('role', 'listbox');
document.body.appendChild(pickMenu);
let pickTarget = null;

function closePickMenu() {
    pickMenu.hidden = true;
    if (pickTarget) pickTarget.classList.remove('editing');
    pickTarget = null;
}

function openPickMenu(td) {
    const field = td.dataset.field;
    const tpl = document.getElementById(field === 'status' ? 'tpl-status' : 'tpl-tag');
    pickMenu.innerHTML = '';
    pickMenu.appendChild(tpl.content.cloneNode(true));
    const current = td.dataset.value || '';
    // 「ステータスを追加・削除…」はステータス管理の欄を開く
    pickMenu.querySelectorAll('.pick-manage').forEach((btn) => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            closePickMenu();
            openManager('status');
        });
    });
    pickMenu.querySelectorAll('button[data-value]').forEach((btn) => {
        btn.classList.toggle('current', btn.dataset.value === String(current));
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const value = btn.dataset.value;
            closePickMenu();
            if (value !== String(current)) {
                commit(td, field, value, td.innerHTML);
            }
        });
    });
    pickTarget = td;
    td.classList.add('editing');
    // セルの真下に表示（画面右端からはみ出さないよう調整）
    const rect = td.getBoundingClientRect();
    pickMenu.hidden = false;
    const left = Math.min(rect.left + window.scrollX,
        window.scrollX + document.documentElement.clientWidth - pickMenu.offsetWidth - 8);
    pickMenu.style.left = Math.max(8, left) + 'px';
    pickMenu.style.top = (rect.bottom + window.scrollY + 4) + 'px';
    const first = pickMenu.querySelector('button.current') || pickMenu.querySelector('button[data-value]');
    if (first) first.focus();
}

document.addEventListener('click', (e) => {
    if (!pickMenu.hidden && !pickMenu.contains(e.target)) closePickMenu();
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !pickMenu.hidden) closePickMenu();
});
window.addEventListener('resize', closePickMenu);

/* ---- 曲名：テキスト入力 ---- */
function editTitle(td) {
    const originalHTML = td.innerHTML;
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'form-control form-control-sm';
    input.value = td.dataset.value || '';
    td.classList.add('editing');
    td.innerHTML = '';
    td.appendChild(input);
    input.focus();
    input.select();
    let done = false;
    function finish(save) {
        if (done) return;
        done = true;
        td.classList.remove('editing');
        if (save && input.value.trim() !== (td.dataset.value || '')) {
            commit(td, 'title', input.value, originalHTML);
        } else {
            td.innerHTML = originalHTML;
        }
    }
    input.addEventListener('blur', () => finish(true));
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') { e.preventDefault(); finish(true); }
        else if (e.key === 'Escape') { e.preventDefault(); finish(false); }
    });
}

/* ---- 納期：カレンダーから日付を選ぶ（クリアボタンで納期なし） ---- */
function editDeadline(td) {
    const originalHTML = td.innerHTML;
    const box = document.createElement('div');
    box.className = 'deadline-editor';
    box.innerHTML = '<input type="date" class="form-control form-control-sm">'
        + '<button type="button" class="btn btn-sm btn-outline-secondary text-nowrap">クリア</button>';
    const input = box.querySelector('input');
    const clearBtn = box.querySelector('button');
    input.value = td.dataset.value || '';
    td.classList.add('editing');
    td.innerHTML = '';
    td.appendChild(box);
    input.focus();
    // 1クリックでカレンダーが開くようにする（対応ブラウザのみ）
    try { input.showPicker(); } catch (e) { /* 未対応なら入力欄から選ぶ */ }

    let done = false;
    function finish(value) {
        if (done) return;
        done = true;
        td.classList.remove('editing');
        if (value === null || value === (td.dataset.value || '')) {
            td.innerHTML = originalHTML;
        } else {
            commit(td, 'deadline', value, originalHTML);
        }
    }
    input.addEventListener('change', () => { if (input.value) finish(input.value); });
    // クリアは blur より先に処理する
    clearBtn.addEventListener('mousedown', (e) => e.preventDefault());
    clearBtn.addEventListener('click', () => finish(''));
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') { e.preventDefault(); finish(input.value); }
        else if (e.key === 'Escape') { e.preventDefault(); finish(null); }
    });
    // 欄の外をクリックしたら取り消し（カレンダー操作中の一時的な blur は無視）
    input.addEventListener('blur', () => setTimeout(() => {
        if (!box.contains(document.activeElement)) finish(null);
    }, 200));
}

document.querySelectorAll('td.editable').forEach((td) => {
    td.addEventListener('click', (e) => {
        if (td.classList.contains('editing')) return;
        e.stopPropagation();
        const field = td.dataset.field;
        if (field === 'status' || field === 'tagId') openPickMenu(td);
        else if (field === 'deadline') editDeadline(td);
        else if (field === 'title') editTitle(td);
    });
});

/* ---- 完了チェック：ステータス「完了」と連動し、バックアップ一覧へ移動する ---- */
document.querySelectorAll('.done-check').forEach((box) => {
    box.addEventListener('change', () => {
        const songId = box.closest('[data-song-id]').dataset.songId;
        saveField(songId, 'deadlineDone', box.checked ? 'true' : 'false')
            .catch((e) => {
                box.checked = !box.checked;
                showToast(e.message, false);
            });
    });
});

/* =========================================================
   2) タグ・ステータスの追加・削除
   ========================================================= */
const managers = {
    tag: { panel: document.getElementById('tag-manager'), toggle: document.getElementById('tag-manage-toggle'),
        input: 'tag-add-name' },
    status: { panel: document.getElementById('status-manager'),
        toggle: document.getElementById('status-manage-toggle'), input: 'status-add-name' }
};

// 管理欄を開く（もう一方は閉じる）。open を省略すると開閉を切り替える
function openManager(name, open) {
    Object.keys(managers).forEach((key) => {
        const m = managers[key];
        const show = key === name ? (open === undefined ? m.panel.hidden : open) : false;
        m.panel.hidden = !show;
        m.toggle.setAttribute('aria-expanded', String(show));
        if (show && key === name) {
            m.panel.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            document.getElementById(m.input).focus();
        }
    });
}
Object.keys(managers).forEach((key) => {
    managers[key].toggle.addEventListener('click', () => openManager(key));
});

// ステータスの追加
const statusColor = document.getElementById('status-add-color');
const statusPreview = document.getElementById('status-add-preview');
function updateStatusPreview() {
    const name = document.getElementById('status-add-name').value.trim();
    statusPreview.className = 'badge align-self-center '
        + statusColor.options[statusColor.selectedIndex].dataset.class;
    statusPreview.textContent = name || 'プレビュー';
}
statusColor.addEventListener('change', updateStatusPreview);
document.getElementById('status-add-name').addEventListener('input', updateStatusPreview);
updateStatusPreview();

document.getElementById('status-add-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const name = document.getElementById('status-add-name').value.trim();
    if (!name) return;
    postJson('/statuses', { name: name, color: statusColor.value })
        .then(() => {
            sessionStorage.setItem('mt-list-toast', 'ステータス「' + name + '」を追加しました');
            sessionStorage.setItem('mt-open-manager', 'status');
            location.reload();
        })
        .catch((err) => showToast(err.message, false));
});

document.querySelectorAll('.status-delete').forEach((btn) => {
    btn.addEventListener('click', () => {
        if (!confirm('ステータス「' + btn.dataset.name + '」を削除します。\n'
            + 'このステータスの曲は、選ぶ前のステータスに戻ります。よろしいですか？')) return;
        postJson('/statuses/' + btn.dataset.id, undefined, 'DELETE')
            .then(() => {
                sessionStorage.setItem('mt-open-manager', 'status');
                location.reload();
            })
            .catch((err) => showToast(err.message, false));
    });
});

// 追加・削除して再読み込みした後は、管理欄を開いたままにする
(function reopenManager() {
    const name = sessionStorage.getItem('mt-open-manager');
    if (name && managers[name]) {
        sessionStorage.removeItem('mt-open-manager');
        openManager(name, true);
    }
})();

document.getElementById('tag-add-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const input = document.getElementById('tag-add-name');
    const name = input.value.trim();
    if (!name) return;
    postJson('/tags', { name: name })
        .then(() => {
            sessionStorage.setItem('mt-open-manager', 'tag');
            location.reload();
        })
        .catch((err) => showToast(err.message, false));
});

document.querySelectorAll('.tag-delete').forEach((btn) => {
    btn.addEventListener('click', () => {
        const name = btn.dataset.name;
        if (!confirm('タグ「' + name + '」を削除します。\nこのタグが付いている曲からも外れます。よろしいですか？')) return;
        postJson('/tags/' + btn.dataset.id, undefined, 'DELETE')
            .then(() => {
                // 削除したタグで絞り込み中なら全件表示に戻す
                const selected = new URLSearchParams(location.search).get('tagId');
                if (selected === btn.dataset.id) location.href = '/songs';
                else location.reload();
            })
            .catch((err) => showToast(err.message, false));
    });
});

/* =========================================================
   3) 行のドラッグ&ドロップ並び替え
   ========================================================= */
let dragRow = null;

function getRowAfter(tbody, y) {
    const rows = [...tbody.querySelectorAll('tr[data-song-id]:not(.dragging)')];
    return rows.reduce((closest, row) => {
        const box = row.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closest.offset) {
            return { offset: offset, element: row };
        }
        return closest;
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

function saveOrder() {
    const ids = [...document.querySelectorAll('.song-body tr[data-song-id]')]
        .map((tr) => parseInt(tr.dataset.songId, 10));
    if (!ids.length) return;
    postJson('/songs/reorder', ids)
        .then(() => showToast('順序を保存しました', true))
        .catch(() => showToast('順序の保存に失敗しました', false));
}

// 楽曲一覧・バックアップ一覧それぞれの表の中で並び替える（表をまたいだ移動はしない）
document.querySelectorAll('.song-body').forEach((tbody) => {
    tbody.querySelectorAll('tr').forEach((tr) => {
        const handle = tr.querySelector('.row-handle');
        if (!handle) return;
        // ハンドルからのみドラッグ開始（セルのインライン編集を妨げない）
        handle.addEventListener('mousedown', () => tr.setAttribute('draggable', 'true'));
        handle.addEventListener('mouseup', () => tr.removeAttribute('draggable'));
        tr.addEventListener('dragstart', (e) => {
            dragRow = tr;
            tr.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'move';
        });
        tr.addEventListener('dragend', () => {
            tr.classList.remove('dragging');
            tr.removeAttribute('draggable');
            dragRow = null;
            saveOrder();
        });
    });
    tbody.addEventListener('dragover', (e) => {
        if (!dragRow || dragRow.parentElement !== tbody) return;
        e.preventDefault();
        const after = getRowAfter(tbody, e.clientY);
        if (after == null) {
            tbody.appendChild(dragRow);
        } else {
            tbody.insertBefore(dragRow, after);
        }
    });
    tbody.addEventListener('drop', (e) => e.preventDefault());
});

/* =========================================================
   4) 新規追加の行（楽曲一覧の1行目。スマホは一覧の上の入力欄）
   ========================================================= */
function addSong(form) {
    const title = form.querySelector('.add-title');
    const name = title.value.trim();
    if (!name) {
        title.focus();
        showToast('曲名を入力してください', false);
        return;
    }
    const pick = (selector) => {
        const el = form.querySelector(selector);
        return el ? el.value : '';
    };
    const buttons = form.querySelectorAll('button');
    buttons.forEach((b) => { b.disabled = true; });
    postJson('/songs', {
        title: name,
        status: pick('.add-status'),
        tagId: pick('.add-tag'),
        deadline: pick('.add-deadline')
    }).then(() => {
        sessionStorage.setItem('mt-list-toast', '「' + name + '」を追加しました');
        sessionStorage.setItem('mt-focus-add', '1');
        location.reload();
    }).catch((err) => {
        buttons.forEach((b) => { b.disabled = false; });
        showToast(err.message, false);
    });
}

document.querySelectorAll('.add-row').forEach((row) => {
    row.querySelector('.add-submit').addEventListener('click', () => addSong(row));
    row.querySelectorAll('input').forEach((input) => {
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.isComposing) {
                e.preventDefault();
                addSong(row);
            }
        });
    });
});
document.querySelectorAll('.mobile-add').forEach((form) => {
    form.addEventListener('submit', (e) => {
        e.preventDefault();
        addSong(form);
    });
});

// 続けて追加できるよう、追加後は曲名の欄にカーソルを置く
if (sessionStorage.getItem('mt-focus-add')) {
    sessionStorage.removeItem('mt-focus-add');
    const input = [...document.querySelectorAll('.add-title')].find((el) => el.offsetParent !== null);
    if (input) input.focus();
}

/* =========================================================
   5) 楽曲一覧・バックアップ一覧の折り畳み（開閉の状態はこのブラウザに記憶）
   ========================================================= */
function setCollapsed(section, collapsed) {
    section.classList.toggle('collapsed', collapsed);
    section.querySelector('.section-content').hidden = collapsed;
    section.querySelector('.section-toggle').setAttribute('aria-expanded', String(!collapsed));
}

document.querySelectorAll('.song-section').forEach((section) => {
    const key = 'mt-collapsed-' + (section.dataset.archive === 'true' ? 'archive' : 'active');
    let saved = null;
    try { saved = localStorage.getItem(key); } catch (e) { /* 保存できない環境では毎回開いた状態 */ }
    setCollapsed(section, saved === '1');
    section.querySelector('.section-toggle').addEventListener('click', () => {
        const collapsed = !section.classList.contains('collapsed');
        setCollapsed(section, collapsed);
        try { localStorage.setItem(key, collapsed ? '1' : '0'); } catch (e) { /* 無視 */ }
    });
});
