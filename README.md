# 🎵 作曲トラッカー（Music Tracker）

作曲の進捗・歌詞・コード進行・デモ音源をひとつの場所で管理できる、作曲家向けWebアプリケーションです。

🔗 **URL**: [music-tracker-105d.onrender.com](https://music-tracker-105d.onrender.com)

---

## 🚀 主な機能

### 曲一覧・進捗管理

- ステータス管理（編曲中 / 作曲中 / フルコーラス完成 など）
- タグで絞り込み（コンベ / バンド / ボカロ など）
- 納期設定と期限アラート（3日前・当日に警告表示）
- ドラッグ＆ドロップで曲順を並び替え
- 曲名・ステータス・タグ・メモをその場でインライン編集（自動保存）

### スタジオ（詳細編集）

- 歌詞とコード進行を**セクション単位**（Aメロ / Bメロ / サビ など）で並列編集
- セクションのドラッグ＆ドロップ並び替え
- 歌詞・コード構成をテンプレートとして保存・再利用
- 全画面モード（Zen Mode）でそれぞれ集中編集

### コード進行支援

- セクションごとにキー（Key）を設定
- **ディグリーネーム解析**：入力したコードをスケール上の度数（I / IIm / IIIm...）に自動変換
- **五度圏サークル**でキー選択をビジュアルサポート
- ダイアトニックコード一覧をリアルタイム表示

### デモ音源・BPM・世界観管理

- デモ音源（最大50MB）のアップロード・再生・差し替え
- BPMの記録
- 世界観・コンセプトメモ

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
| インフラ       | Docker / Docker Compose                 |
| デプロイ       | Render（PaaS）                          |

---

## 🏗 設計の工夫

### セクションの抽象化（継承設計）

歌詞セクションとコードセクションは構造が似ているため、`AbstractSection` を親クラスとして共通フィールド（曲ID・セクション名・表示順）を持たせ、`LyricSection` と `ChordSection` で継承しています。これにより、ドラッグ＆ドロップの並び替えロジックを共通化できました。

```
AbstractSection
├── LyricSection  （歌詞テキスト）
└── ChordSection  （コード文字列 + キー情報）
```

### インライン編集と自動保存

曲一覧の各セルをクリックするとその場で編集できます。`contenteditable` と `blur` イベントを組み合わせ、編集完了時に Ajax（fetch）でサーバーに保存する仕組みにしました。画面遷移なしに更新できるためUXが向上しています。

### ディグリーネーム解析

コード名（例：`C#m7`）とキー情報からスケール上の度数を計算するロジックをサーバーサイドのJavaで実装しています。半音の対応テーブルを持ち、ノンダイアトニックコード（例：`Cdim` → `bVIIdim`）も判定します。

### スマホ対応

- 曲一覧：テーブルをスマホで非表示にし、カード型UIに切り替え
- スタジオ：3ペイン並列→タブ切替UIに変更
- カレンダー：`window.innerWidth < 768` で自動的にリスト表示に切替

---

## 📁 ディレクトリ構成

```
src/main/
├── java/com/portfolio/musictracker/
│   ├── config/
│   │   ├── DataInitializer.java       # 初期データ投入
│   │   └── WebConfig.java             # MVC設定
│   ├── controller/
│   │   ├── LandingController.java     # GET / → LP or リダイレクト
│   │   ├── SongController.java        # 曲一覧・インライン編集
│   │   ├── AuthController.java        # ログイン・新規登録
│   │   ├── CalendarController.java    # 制作カレンダー
│   │   ├── TemplateController.java    # 構成テンプレートCRUD
│   │   └── ProfileController.java     # プロフィール・パスワード変更
│   ├── dto/                           # フォーム・リクエストオブジェクト
│   ├── entity/
│   │   ├── Song.java                  # 曲エンティティ
│   │   ├── AbstractSection.java       # セクション共通（継承元）
│   │   ├── LyricSection.java          # 歌詞セクション
│   │   ├── ChordSection.java          # コードセクション
│   │   ├── SectionTemplate.java       # 構成テンプレート
│   │   └── User.java                  # ユーザー
│   ├── repository/                    # Spring Data JPA リポジトリ
│   ├── security/
│   │   ├── SecurityConfig.java        # 認証・認可設定
│   │   └── CustomUserDetailsService.java
│   └── service/
│       ├── SongService.java           # 曲ビジネスロジック
│       ├── AudioStorageService.java   # 音源ファイル管理
│       ├── ScheduleService.java       # 納期チェック
│       └── UserService.java
└── resources/
    ├── templates/
    │   ├── landing.html               # ランディングページ
    │   ├── fragments/head.html        # 共通headタグ
    │   ├── auth/                      # ログイン・新規登録
    │   └── songs/                     # 曲一覧・スタジオ・カレンダー
    └── static/                        # 静的リソース（アイコン・PWA）
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

### デフォルトのDB設定（application.yml）

| 項目       | 値               |
| ---------- | ---------------- |
| ホスト     | localhost:3307   |
| DB名       | music_tracker_db |
| ユーザー   | tracker_user     |
| パスワード | tracker_password |

---

## 👤 作者

**Shota Arakawa**

- GitHub: [@ShotaArakawa](https://github.com/ShotaArakawa)
