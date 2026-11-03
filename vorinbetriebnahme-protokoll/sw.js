// Service Worker: Cache-first für die App-Dateien, zur Laufzeit werden auch geladene Bibliotheken (Barcode, Texterkennung) zwischengespeichert.
const CACHE = 'vibn-v3';
const CORE = ['./','./index.html','./manifest.webmanifest','./icon.svg','./icon-192.png','./icon-512.png'];
self.addEventListener('install', e=>{ e.waitUntil(caches.open(CACHE).then(c=>c.addAll(CORE)).then(()=>self.skipWaiting())); });
self.addEventListener('activate', e=>{ e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())); });
self.addEventListener('fetch', e=>{
  const req = e.request;
  if(req.method!=='GET') return;
  const url = new URL(req.url);
  if(!/^https?:$/.test(url.protocol)) return;
  if(url.hostname==='nominatim.openstreetmap.org') return;   // Adresssuche immer live
  e.respondWith(caches.match(req, {ignoreSearch:true}).then(hit=>{
    if(hit) return hit;
    return fetch(req).then(res=>{
      if(res && (res.ok || res.type==='opaque')){ const copy=res.clone(); caches.open(CACHE).then(c=>c.put(req,copy)); }
      return res;
    }).catch(()=> req.mode==='navigate' ? caches.match('./index.html') : Response.error());
  }));
});
