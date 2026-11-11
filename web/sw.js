// Leonardo – Service Worker: speichert die App beim ersten Öffnen,
// danach läuft alles offline. Die Version setzt das Build-Skript.
const VERSION = '__VERSION__';
const CACHE = `leonardo-${VERSION}`;

const APP_DATEIEN = [
  './',
  'index.html',
  'manifest.webmanifest',
  'css/app.css',
  'js/app.js',
  'js/logik.js',
  'fonts/atkinson_regular.ttf',
  'fonts/atkinson_bold.ttf',
  'icons/apple-touch-icon.png',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png',
  'content/index.json',
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await cache.addAll(APP_DATEIEN.map((url) => new Request(url, { cache: 'reload' })));
    // Alle Kapitel-Dateien aus der Inhaltsliste mitspeichern.
    const liste = await (await cache.match('content/index.json')).json();
    await cache.addAll(liste.map((name) => new Request(`content/${name}`, { cache: 'reload' })));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const name of await caches.keys()) {
      if (name.startsWith('leonardo-') && name !== CACHE) await caches.delete(name);
    }
    await self.clients.claim();
  })());
});

// Erst aus dem Speicher, sonst aus dem Netz (und dann merken).
self.addEventListener('fetch', (event) => {
  const anfrage = event.request;
  if (anfrage.method !== 'GET' || new URL(anfrage.url).origin !== self.location.origin) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const gespeichert = await cache.match(anfrage, { ignoreSearch: true });
    if (gespeichert) return gespeichert;
    if (anfrage.mode === 'navigate') {
      const start = await cache.match('index.html');
      if (start) return start;
    }
    try {
      const antwort = await fetch(anfrage);
      if (antwort.ok) cache.put(anfrage, antwort.clone());
      return antwort;
    } catch {
      return new Response('Offline – diese Datei ist nicht gespeichert.', { status: 503, headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
    }
  })());
});
