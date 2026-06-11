-- =============================================================================
-- MusicTracker UI確認用サンプルデータ シードスクリプト
-- 対象ユーザー: test / test@example.com / test1234
--
-- 実行方法（MySQL は docker-compose の music_tracker_db を想定）:
--   docker exec -i music_tracker_db \
--     mysql -utracker_user -ptracker_password --default-character-set=utf8mb4 \
--     music_tracker_db < scripts/seed-test-data.sql
--
-- このスクリプトは冪等です。再実行すると test ユーザーの曲データを
-- 一度すべて削除してから作り直します（test ユーザー自身は使い回します）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1) test ユーザー（無ければ作成。パスワードは BCrypt("test1234")）
-- -----------------------------------------------------------------------------
INSERT INTO users (username, password, email, role, created_at)
SELECT 'test',
       '$2a$10$DYSa4cyHBKBfFRlKY4lz3O6TBqQ5Naimuk8Bj4JQySdj4m5a0zmVa',
       'test@example.com',
       'USER',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'test');

SET @uid := (SELECT id FROM users WHERE username = 'test');

-- -----------------------------------------------------------------------------
-- 2) タグ（既定3つに加えてジャンルタグを追加。name は UNIQUE なので IGNORE）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO tags (name) VALUES
  ('ボカロ'), ('バンド'), ('コンペ'),
  ('アニソン'), ('劇伴'), ('弾き語り'), ('シティポップ');

-- -----------------------------------------------------------------------------
-- 3) 既存の test ユーザーの曲を一掃（再実行時の重複防止）
-- -----------------------------------------------------------------------------
DELETE FROM song_tags      WHERE song_id IN (SELECT id FROM songs WHERE user_id = @uid);
DELETE FROM lyric_sections WHERE song_id IN (SELECT id FROM songs WHERE user_id = @uid);
DELETE FROM chord_sections WHERE song_id IN (SELECT id FROM songs WHERE user_id = @uid);
DELETE FROM songs          WHERE user_id = @uid;

-- =============================================================================
-- 4) 楽曲データ
--    今日 = 2026-06-08 を基準に、納期を 超過 / 当日 / 直近 / 未来 / 無し に分散。
--    ステータスは全6種を網羅。BPM/キー/進捗率も多様に設定。
-- =============================================================================

-- ── 曲1: 作詞中 / 締切=直近(+2日) / ボカロ ───────────────────────────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, '夜明けのプロローグ',
   'サビのキャッチー感を最優先。歌い出しはローテンションから。',
   '6/10', 'LYRICS_WRITING', 128, 'C',
   '夜明け前の街。青白い光と、まだ眠っている世界。希望と少しの不安を同居させる。',
   45, 20, 0, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR),
   DATE_SUB(NOW(), INTERVAL 5 DAY), NOW());
SET @s1 := LAST_INSERT_ID();

-- ── 曲2: 編曲中 / 締切=超過(4/1) / バンド ────────────────────────────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, 'Neon Drive',
   '4つ打ち＋カッティングギター。間奏でギターソロを入れる。',
   '2026/4/1', 'ARRANGING', 140, 'Am',
   'ネオン街を駆け抜ける疾走感。80年代シンセウェイブの匂い。',
   80, 90, 55, 1, DATE_SUB(NOW(), INTERVAL 1 DAY),
   DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @s2 := LAST_INSERT_ID();

-- ── 曲3: メロディ作成中 / 締切=当日(6/8) / コンペ・シティポップ ───────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, 'Tokyo Midnight',
   '丸の内サディスティック進行でおしゃれに。Aメロは語りかけるように。',
   '6/8', 'MELODY_MAKING', 92, 'C',
   '深夜のオフィス街。タクシーのテールランプ。大人のけだるさと色気。',
   30, 60, 15, 2, DATE_SUB(NOW(), INTERVAL 6 HOUR),
   DATE_SUB(NOW(), INTERVAL 12 DAY), NOW());
SET @s3 := LAST_INSERT_ID();

-- ── 曲4: デモ完成 / 締切=未来(7/15) / 劇伴 ───────────────────────────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, '風の記憶',
   'カノン進行ベースのバラード。ストリングスを後半で足す。仮歌録り済み。',
   '7/15', 'DEMO_DONE', 72, 'G',
   '田舎の夏。麦わら帽子と入道雲。ノスタルジックで切ない情景。',
   100, 100, 70, 3, DATE_SUB(NOW(), INTERVAL 3 DAY),
   DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));
SET @s4 := LAST_INSERT_ID();

-- ── 曲5: フルコーラス完成 / 締切=未来コンペ(9/30) / コンペ・ボカロ ─────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, 'Starlight Anthem',
   '高速BPMのボカロロック。コンペ提出用、2番以降の歌詞を要ブラッシュアップ。',
   '2026/9/30', 'FULL_CHORUS_DONE', 174, 'F#m',
   '満天の星空の下、走り出す衝動。疾走と祈り。',
   100, 100, 95, 4, DATE_SUB(NOW(), INTERVAL 7 DAY),
   DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY));
SET @s5 := LAST_INSERT_ID();

-- ── 曲6: リリース済み / 締切=超過(5/20) / 弾き語り ───────────────────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, '雨上がりの放課後',
   '配信リリース済み。アコギ1本の弾き語りアレンジ。',
   '5/20', 'RELEASED', 88, 'D',
   '雨上がりの教室。濡れたアスファルトの匂い。淡い青春の1ページ。',
   100, 100, 100, 5, DATE_SUB(NOW(), INTERVAL 14 DAY),
   DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY));
SET @s6 := LAST_INSERT_ID();

-- ── 曲7: 作詞中 / 締切=無し / タグ無し（最小データのUI確認用） ─────────────────
INSERT INTO songs
  (user_id, title, memo, deadline, status, bpm, music_key, world_view_memo,
   lyric_progress, melody_progress, arrangement_progress,
   list_order, last_opened_at, created_at, updated_at)
VALUES
  (@uid, '未完成スケッチ',
   '鼻歌メモだけ。テーマ未定。',
   NULL, 'LYRICS_WRITING', NULL, NULL, NULL,
   5, 0, 0, 6, NULL,
   DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @s7 := LAST_INSERT_ID();

-- =============================================================================
-- 5) タグの紐付け（song_tags）
-- =============================================================================
INSERT INTO song_tags (song_id, tag_id) VALUES
  (@s1, (SELECT id FROM tags WHERE name = 'ボカロ')),
  (@s2, (SELECT id FROM tags WHERE name = 'バンド')),
  (@s3, (SELECT id FROM tags WHERE name = 'コンペ')),
  (@s3, (SELECT id FROM tags WHERE name = 'シティポップ')),
  (@s4, (SELECT id FROM tags WHERE name = '劇伴')),
  (@s5, (SELECT id FROM tags WHERE name = 'コンペ')),
  (@s5, (SELECT id FROM tags WHERE name = 'ボカロ')),
  (@s6, (SELECT id FROM tags WHERE name = '弾き語り'));

-- =============================================================================
-- 6) 歌詞・メモ（lyric_sections）
--    歌詞フレーズ / アイデアメモ / 機材セッティングメモ を混在させる。
-- =============================================================================
INSERT INTO lyric_sections (song_id, name, sort_order, content) VALUES
  -- 曲1: 夜明けのプロローグ
  (@s1, 'Aメロ', 0, '眠れない夜の隅で\n時計の針だけが進む\nまだ誰も知らない朝を\n僕はそっと待っている'),
  (@s1, 'サビ', 1, '夜明けよ来い この街に\n(※仮)光が差すその瞬間を\n声にして 叫びたいんだ'),
  (@s1, 'アイデアメモ', 2, '・サビ頭は最高音(hiA)で抜ける\n・2番Aメロは歌詞を反転させて対比\n・タイトル回収はラスサビ前'),
  -- 曲2: Neon Drive
  (@s2, 'Hook', 0, 'Drive, drive ネオンの海へ\n振り返らない 今夜だけは'),
  (@s2, '機材メモ', 1, '・Vo: SM7B → CL1B → 1176\n・Gt: Strat → Timmy → JC120 (マイクSM57オフ)\n・Syn: Serum "SuperSaw 7voice" / Sub +0\n・BPM140 / クリック裏拍うっすら'),
  -- 曲3: Tokyo Midnight
  (@s3, 'Aメロ', 0, '終電を見送って\nひとり残るホーム\nスマホの画面だけが\n青く光ってる'),
  (@s3, 'アレンジメモ', 1, '・エレピ(Rhodes)主体、リバーブ深め\n・ベースは指弾きで動かす\n・ブラシのドラムで都会の夜感'),
  -- 曲4: 風の記憶
  (@s4, '1番', 0, '麦わら帽子追いかけて\n君と走った あの坂道\n蝉の声 入道雲\nもう戻れない夏の日'),
  (@s4, 'サビ', 1, '風の記憶 胸の奥\nいつまでも色褪せないまま\n会いたいよ あの頃の君に'),
  (@s4, '機材メモ', 2, '・Strings: Spitfire BBCSO Core\n・Pf: Una Corda\n・後半2:30〜でStrings追加、ダイナミクスを上げる'),
  -- 曲5: Starlight Anthem
  (@s5, 'サビ', 0, '駆け出せ 星屑のレール\n息を切らして 願いを乗せて\nどこまでも どこまでも'),
  (@s5, 'Cメロ', 1, '(2番以降ブラッシュアップ要)\n立ち止まった夜さえ\n今は背中を押す光'),
  -- 曲6: 雨上がりの放課後
  (@s6, '全体', 0, '雨上がりの放課後\n窓の外 虹がかかる\n君の横顔 そっと見てた\n言えなかった「好き」を抱えて'),
  -- 曲7: 未完成スケッチ
  (@s7, 'メモ', 0, 'ふんふんふん〜 ♪（鼻歌だけ録音済み）\nテーマ: 未定。明るい曲にするか暗い曲にするか迷い中。');

-- =============================================================================
-- 7) コード進行（chord_sections）
--    ディグリーネーム自動表示の確認用に代表的な進行を投入。
--    コードは半角スペース／改行区切り。section_key を入れると転調表示も確認可。
-- =============================================================================
INSERT INTO chord_sections (song_id, name, sort_order, content, section_key) VALUES
  -- 曲1: 王道進行 (IV-V-IIIm-VIm) in C
  (@s1, 'サビ（王道進行）', 0, 'F G Em Am\nF G C C', NULL),
  (@s1, 'Aメロ', 1, 'C G Am Em\nF C Dm G', NULL),
  -- 曲2: 小室進行 (VIm-IV-V-I) in Am→C
  (@s2, 'サビ（小室進行）', 0, 'Am F G C\nAm F G C', NULL),
  -- 曲3: 丸の内サディスティック進行 in C（個別Keyを明示）
  (@s3, 'サビ（丸サ進行）', 0, 'FM7 E7 Am7 Gm7 C7\nFM7 E7 Am7 D7 G7', 'C'),
  (@s3, 'Aメロ', 1, 'FM7 G7 Em7 Am7\nDm7 G7 CM7 A7', 'C'),
  -- 曲4: カノン進行 in G
  (@s4, 'サビ（カノン進行）', 0, 'G D Em Bm\nC G C D', 'G'),
  -- 曲5: F#m の疾走系（転調確認: 大サビでAメジャー扱い）
  (@s5, 'サビ', 0, 'F#m D A E\nF#m D A E', 'F#m'),
  (@s5, '大サビ（転調）', 1, 'A E F#m D\nA E D E', 'A'),
  -- 曲6: 弾き語り D（カノン系）
  (@s6, '全体', 0, 'D A Bm F#m\nG D G A', 'D');
