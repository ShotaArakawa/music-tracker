# 🎵 Music-Tracker

作曲の進捗・歌詞・コード譜・デモ音源をひとつの場所で管理できる、作曲家向けWebアプリケーションです。

🔗 **URL**: [music-tracker-105d.onrender.com](https://music-tracker-105d.onrender.com)

---

## 🚀 主な機能

### 曲一覧・進捗管理

- ステータス管理（作詞中 / 作曲中 / 編曲中 / デモ完成 / フルコーラス完成 / 完了）。セルをクリックするとすぐ選択ポップアップが開く
- タグで絞り込み（コンペ / バンド / ボカロ など）。タグはユーザーごとに追加・削除できる
- 納期はカレンダーから選択。納期を過ぎた曲は行が赤くなる
- 「完了」チェックとステータス「完了」は連動。完了した曲は下の**バックアップ一覧**へ移動し、チェックを外すと完了前のステータスで楽曲一覧に戻る
- ドラッグ＆ドロップで曲順を並び替え
- 曲名・ステータス・タグ・納期をその場でインライン編集（自動保存）。編集できる場所にカーソルを合わせると、半透明の吹き出しで操作のヒントを表示

### スタジオ（詳細編集）

- 「🎼 コード譜」「📝 歌詞」をタブで切り替えて、作業ごとに画面いっぱいで編集（開いたときはコード譜）
- ヘッダーに**進捗率**（作詞・編曲・全体の横長ブロック）と**デモ音源**を並べて常に表示し、確認しながら作業できる
- 歌詞を**セクション単位**（Aメロ / Bメロ / サビ など）で編集・ドラッグ＆ドロップで並び替え
- 歌詞を **Word（.docx）/ テキスト（.txt）/ PDF** で書き出し（表示中の内容をそのまま。未保存の編集も含む）

### コード譜（Excel テンプレートをそのまま編集）

- コード譜テンプレート（① 1小節2マス / ② 1小節4マス）を**表計算エディタで直接編集**（罫線・色・結合・列幅も Excel と同じ見た目）
- テンプレートから作ると、曲名（と登録済みの Key・BPM）を見出しに自動で記入
- **インポート**：Excel（.xlsx / .xls）は書式ごとそのまま反映。PDF は文字データから表に並べて取り込み
- **エクスポート**：Excel（.xlsx）と PDF（A4・1ページ幅に自動縮小）。書き出した PDF はインポートすると書式ごと完全に復元
- 書き出した Excel は Microsoft Excel でも列幅・罫線・色が画面と同じになる（フォント M PLUS Rounded 1c が入っていない PC では代わりのフォントで表示）

### デモ音源

- デモ音源（最大50MB）のアップロード・再生・差し替え（スタジオのヘッダー、進捗率の横）

### 制作カレンダー

- 納期を月次カレンダーで可視化
- 色分け表示（赤：納期超過 / 橙：3日以内 / 灰：予定）
- スマホではリスト表示に自動切替

---

## 🛠 技術スタック

| カテゴリ       | 技術                                    |
| -------------- | --------------------------------------- |
| バックエンド   | Java 21 / Spring Boot 3.4.5             |
| Web層          | Spring MVC / Thymeleaf                  |
| 認証           | Spring Security（BCrypt + Remember-Me） |
| DB操作         | Spring Data JPA / Hibernate             |
| データベース   | MySQL 8.0                               |
| フロントエンド | Bootstrap 5.3 / Vanilla JS              |
| コード譜       | Jspreadsheet CE（表計算エディタ）/ Apache POI（Excel）/ Apache PDFBox（PDF） |
| インフラ       | Docker / Docker Compose                 |
| デプロイ       | Render（PaaS）                          |

---

## 🏗 設計の工夫

### コード譜：画面・Excel・PDF を1つのデータ形式でつなぐ

コード譜は、画面の表計算エディタ（Jspreadsheet）が扱う形（セルの値・セルごとの CSS・結合・列幅・行の高さ）をそのまま `ChordSheet` として JSON で保存しています。Excel / PDF との変換はサーバー側でこの形を起点に行います。

```
             ┌─ ChordSheetExcelConverter（Apache POI）── .xlsx / .xls
ChordSheet ──┼─ ChordSheetPdfRenderer   （PDFBox）──── PDF（元の .xlsx を添付）
（画面と同じ形）└─ ChordSheetPdfImporter   （PDFBox）──── 添付 .xlsx から完全復元 / 文字の位置から表を推定
```

- 書式は `CellStyleCss` で「Excel の書式 ⇄ CSS」を相互変換（色・太字・文字サイズ・配置・罫線）
- PDF はテンプレートと同じ M PLUS Rounded 1c で描画し、収録外の文字は IPAex ゴシックで補う
- スキャン画像・手書きの PDF は文字データがないため取り込めません（エラーで案内）
- 旧仕様の「コード進行」データは、起動時に一度だけコード譜へ変換して旧テーブルを削除します（`LegacyChordMigration`）

### インライン編集と自動保存

曲一覧の各セルをクリックするとその場で編集できます。`contenteditable` と `blur` イベントを組み合わせ、編集完了時に Ajax（fetch）でサーバーに保存する仕組みにしました。画面遷移なしに更新できるためUXが向上しています。

### 権限と安全性

- 曲・デモ音源・タグは作成したユーザーだけが参照・変更できます（サービス層で所有者を確認）
- デモ音源は静的公開せず、所有者確認つきのエンドポイント（`GET /songs/{id}/audio`）から配信します
- CSRF 対策を有効にしており、フォームは hidden の `_csrf`、Ajax（fetch）はヘッダーでトークンを送ります

### デモ音源の保存先の切り替え

`AudioStorage` インターフェースで保存先を抽象化し、環境変数で切り替えます。

- `local`：サーバーのディスク（ローカル開発用）
- `s3`：Cloudflare R2 などの S3 互換ストレージ。再生時は短時間だけ有効な署名付き URL にリダイレクトし、ストレージから直接配信します

### スマホ対応

- 曲一覧：テーブルをスマホで非表示にし、カード型UIに切り替え
- スタジオ：歌詞・コード譜のタブ切り替え。ヘッダーの進捗率の下にデモ音源を配置
- カレンダー：`window.innerWidth < 768` で自動的にリスト表示に切替

---

## 📁 ディレクトリ構成

```
src/main/
├── java/com/portfolio/musictracker/
│   ├── chordchart/                    # コード譜
│   │   ├── ChordSheet.java            # 表データ（画面・保存・変換の共通形式）
│   │   ├── CellStyleCss.java          # Excel の書式 ⇄ CSS
│   │   ├── ChordSheetExcelConverter.java # Excel 読み書き
│   │   ├── ChordSheetPdfRenderer.java # PDF 出力
│   │   ├── ChordSheetPdfImporter.java # PDF 読み取り
│   │   ├── ChordChartController.java  # 作成・インポート・エクスポート API
│   │   └── LegacyChordMigration.java  # 旧コード進行データの移行
│   ├── pdf/
│   │   └── PdfFonts.java              # PDF 用の日本語フォント（コード譜・歌詞で共通）
│   ├── config/
│   │   ├── DataInitializer.java       # 初期データ投入
│   │   ├── TagOwnershipMigration.java # 共通タグ → ユーザーごとのタグへの移行
│   │   └── CompletionSync.java        # 完了チェックとステータス「完了」の整合
│   ├── controller/
│   │   ├── LandingController.java     # GET / → LP or リダイレクト
│   │   ├── SongController.java        # 曲一覧・インライン編集
│   │   ├── AuthController.java        # ログイン・新規登録
│   │   ├── CalendarController.java    # 制作カレンダー
│   │   ├── TagController.java         # タグの追加・削除
│   │   ├── LyricsController.java      # 歌詞の書き出し API
│   │   └── ProfileController.java     # プロフィール・パスワード変更
│   ├── dto/                           # フォーム・リクエストオブジェクト
│   ├── entity/
│   │   ├── Song.java                  # 曲エンティティ
│   │   ├── AbstractSection.java       # セクション共通（継承元）
│   │   ├── LyricSection.java          # 歌詞セクション
│   │   └── User.java                  # ユーザー
│   ├── repository/                    # Spring Data JPA リポジトリ
│   ├── security/
│   │   ├── SecurityConfig.java        # 認証・認可設定
│   │   └── CustomUserDetailsService.java
│   ├── service/
│   │   ├── SongService.java           # 曲ビジネスロジック
│   │   ├── ScheduleService.java       # 納期チェック
│   │   ├── TagService.java            # ユーザーごとのタグ
│   │   ├── LyricsExportService.java   # 歌詞の Word / テキスト / PDF 出力
│   │   └── UserService.java
│   └── storage/
│       ├── AudioStorage.java          # 音源の保存先（インターフェース）
│       ├── LocalAudioStorage.java     # ディスク保存
│       └── S3AudioStorage.java        # Cloudflare R2 / S3 互換ストレージ
└── resources/
    ├── templates/
    │   ├── landing.html               # ランディングページ
    │   ├── fragments/head.html        # 共通headタグ
    │   ├── auth/                      # ログイン・新規登録
    │   └── songs/                     # 曲一覧・スタジオ・カレンダー
    ├── chord-templates/               # コード譜の白紙テンプレート（Excel）
    ├── fonts/                         # PDF 用フォント（M PLUS Rounded 1c / IPAex ゴシック）とライセンス
    └── static/                        # 静的リソース（CSS・JS・アイコン・PWA）
```

---

## 🐳 ローカル環境構築

### 前提条件

- Java 21
- Maven 3.9+
- Docker / Docker Compose

### 手順

```bash
# 1. リポジトリをクローン
git clone https://github.com/ShotaArakawa/music-tracker.git
cd music-tracker

# 2. DBをDockerで起動（MySQL 8.0 / port 3307）
docker compose up -d

# 3. ビルド
~/.local/maven/apache-maven-3.9.16/bin/mvn -DskipTests clean package

# 4. 起動
java -jar target/music-tracker-0.0.1-SNAPSHOT.jar
```

起動後、[http://localhost:8080](http://localhost:8080) にアクセスするとランディングページが表示されます。
ローカルではユーザーが1人もいない場合に初期ユーザー `demo` / `demo1234` が作成されます（本番では作成しません）。

### テスト

```bash
~/.local/maven/apache-maven-3.9.16/bin/mvn test
```

テストはインメモリ DB（H2）で動くため、MySQL の起動は不要です。

### デフォルトのDB設定（application.yml）

| 項目       | 値               |
| ---------- | ---------------- |
| ホスト     | localhost:3307   |
| DB名       | music_tracker_db |
| ユーザー   | tracker_user     |
| パスワード | tracker_password |

---

## ☁️ 本番環境（Render）の環境変数

| 変数名                                      | 必須 | 内容                                                         |
| ------------------------------------------- | ---- | ------------------------------------------------------------ |
| `DB_HOST` / `DB_PORT` / `DB_NAME`           | ○    | MySQL（Aiven）の接続先                                        |
| `DB_USERNAME` / `DB_PASSWORD`               | ○    | MySQL の認証情報                                              |
| `REMEMBER_ME_KEY`                           | ○    | ログイン保持 Cookie の署名鍵（長いランダム文字列）。未設定だと起動しません |
| `AUDIO_STORAGE`                             |      | `s3` で R2 に保存。未設定なら `local`（再デプロイで音源が消える） |
| `R2_ENDPOINT`                               | s3時 | `https://<アカウントID>.r2.cloudflarestorage.com`              |
| `R2_BUCKET`                                 | s3時 | バケット名                                                    |
| `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | s3時 | R2 の API トークン（オブジェクトの読み書き権限）                |

---

## 👤 作者

**Shota Arakawa**

- GitHub: [@ShotaArakawa](https://github.com/ShotaArakawa)
