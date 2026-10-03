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
            applyOverdue(songId, data.overdue);
            showToast('保存しました', true);
            return data;
        });
}

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
    pickMenu.querySelectorAll('button').forEach((btn) => {
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
    const first = pickMenu.querySelector('button.current') || pickMenu.querySelector('button');
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

/* ---- 完了チェック：期限超過の警告を止める ---- */
document.querySelectorAll('.done-check').forEach((box) => {
    box.addEventListener('change', () => {
        const songId = box.closest('[data-song-id]').dataset.songId;
        saveField(songId, 'deadlineDone', box.checked ? 'true' : 'false')
            .then(() => {
                // PC 表示とスマホ表示のチェックをそろえる
                document.querySelectorAll('[data-song-id="' + songId + '"] .done-check')
                    .forEach((b) => { b.checked = box.checked; });
                // 超過バッジは対応済みにしたら隠す
                document.querySelectorAll('[data-song-id="' + songId + '"] .overdue-badge')
                    .forEach((b) => { b.hidden = box.checked; });
            })
            .catch((e) => {
                box.checked = !box.checked;
                showToast(e.message, false);
            });
    });
});

/* =========================================================
   2) タグの追加・削除
   ========================================================= */
const tagManager = document.getElementById('tag-manager');
const tagToggle = document.getElementById('tag-manage-toggle');
tagToggle.addEventListener('click', () => {
    tagManager.hidden = !tagManager.hidden;
    tagToggle.setAttribute('aria-expanded', String(!tagManager.hidden));
    if (!tagManager.hidden) document.getElementById('tag-add-name').focus();
});

document.getElementById('tag-add-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const input = document.getElementById('tag-add-name');
    const name = input.value.trim();
    if (!name) return;
    postJson('/tags', { name: name })
        .then(() => location.reload())
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
    const rows = [...tbody.querySelectorAll('tr:not(.dragging)')];
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
    const ids = [...document.querySelectorAll('tbody tr')]
        .map((tr) => parseInt(tr.dataset.songId, 10));
    if (!ids.length) return;
    postJson('/songs/reorder', ids)
        .then(() => showToast('順序を保存しました', true))
        .catch(() => showToast('順序の保存に失敗しました', false));
}

const tbody = document.querySelector('tbody');
if (tbody) {
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
        if (!dragRow) return;
        e.preventDefault();
        const after = getRowAfter(tbody, e.clientY);
        if (after == null) {
            tbody.appendChild(dragRow);
        } else {
            tbody.insertBefore(dragRow, after);
        }
    });
    tbody.addEventListener('drop', (e) => e.preventDefault());
}
