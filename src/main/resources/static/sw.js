const CACHE = 'pwa-v1';
const OFFLINE = '/pwa/offline.html';
const OFFLINE_CSS = '/css/theme.css';

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll([OFFLINE, OFFLINE_CSS])).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    if (event.request.mode === 'navigate') {
        event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE)));
        return;
    }
    if (new URL(event.request.url).pathname === OFFLINE_CSS) {
        event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE_CSS)));
    }
});
