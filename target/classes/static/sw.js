// キャッシュの名前
const CACHE_NAME = 'music-tracker-cache-v1';

// インストール時の処理（必須のおまじない）
self.addEventListener('install', (event) => {
  console.log('Service Worker installed');
});

// ネットワークリクエストの処理（通信をそのまま通す）
self.addEventListener('fetch', (event) => {
  event.respondWith(fetch(event.request));
});
