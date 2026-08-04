const CACHE = 'toolbox-v1';
const ASSETS = ['./', './index.html', './manifest.json', './icon.svg'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  // Nur die Hub-Startseite offline cachen; einzelne Tool-Unterseiten immer live laden,
  // damit neu hinzugefügte Ordner sofort sichtbar sind.
  const url = new URL(e.request.url);
  if(ASSETS.some(a => url.pathname.endsWith(a.replace('./','')))){
    e.respondWith(caches.match(e.request).then(c => c || fetch(e.request)));
  }
});
