// Leonardo – Service Worker: speichert die App beim ersten Öffnen,
// danach läuft alles offline. Die Version setzt das Build-Skript.
const VERSION = '__VERSION__';
const CACHE = `leonardo-${VERSION}`;
// Die Aufnahmen liegen in einem eigenen Speicher, der App-Updates übersteht.
const AUDIO_CACHE = 'leonardo-aufnahmen';

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
    // Verzeichnis der Aufnahmen (falls vorhanden) – die Dateien selbst kommen danach im Hintergrund.
    await cache.add(new Request('audio/index.json', { cache: 'reload' })).catch(() => {});
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const name of await caches.keys()) {
      if (name.startsWith('leonardo-') && name !== CACHE && name !== AUDIO_CACHE) await caches.delete(name);
    }
    await self.clients.claim();
  })());
});

// Die App bittet nach dem Start darum, alle Aufnahmen offline zu speichern.
self.addEventListener('message', (event) => {
  if (event.data === 'audio-speichern') event.waitUntil(aufnahmenSpeichern());
});

let speichertGerade = null;
function aufnahmenSpeichern() {
  speichertGerade ??= (async () => {
    try {
      const appCache = await caches.open(CACHE);
      const verzeichnis = await appCache.match('audio/index.json');
      if (!verzeichnis) return;
      const dateien = [...new Set(Object.values((await verzeichnis.json()).dateien))];
      const audio = await caches.open(AUDIO_CACHE);
      const vorhanden = new Set((await audio.keys()).map((r) => new URL(r.url).pathname.split('/').pop()));
      // Alte Aufnahmen, die nicht mehr gebraucht werden, entfernen.
      for (const r of await audio.keys()) {
        if (!dateien.includes(new URL(r.url).pathname.split('/').pop())) await audio.delete(r);
      }
      const fehlend = dateien.filter((d) => !vorhanden.has(d));
      let fertig = dateien.length - fehlend.length;
      await melden(fertig, dateien.length);
      // Immer 4 Dateien gleichzeitig laden.
      const warteschlange = [...fehlend];
      await Promise.all(Array.from({ length: 4 }, async () => {
        while (warteschlange.length) {
          const datei = warteschlange.shift();
          try {
            const antwort = await fetch(`audio/${datei}`);
            if (antwort.ok) await audio.put(`audio/${datei}`, antwort);
          } catch {
            /* offline – beim nächsten Start weiter */
          }
          fertig++;
          if (fertig % 25 === 0) await melden(fertig, dateien.length);
        }
      }));
      await melden(dateien.length - (await fehlendeAnzahl(audio, dateien)), dateien.length);
    } finally {
      speichertGerade = null;
    }
  })();
  return speichertGerade;
}

async function fehlendeAnzahl(audio, dateien) {
  const vorhanden = new Set((await audio.keys()).map((r) => new URL(r.url).pathname.split('/').pop()));
  return dateien.filter((d) => !vorhanden.has(d)).length;
}

async function melden(fertig, gesamt) {
  for (const client of await self.clients.matchAll()) client.postMessage({ art: 'audio', fertig, gesamt });
}

self.addEventListener('fetch', (event) => {
  const anfrage = event.request;
  const url = new URL(anfrage.url);
  if (anfrage.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.endsWith('.mp3')) {
    event.respondWith(aufnahme(anfrage));
    return;
  }
  // Erst aus dem Speicher, sonst aus dem Netz (und dann merken).
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

// Aufnahmen: aus dem Aufnahmen-Speicher, sonst ganz laden und merken.
// Safari fragt Audio in Stücken an („Range“) – darauf antworten wir mit dem passenden Teil.
async function aufnahme(anfrage) {
  const audio = await caches.open(AUDIO_CACHE);
  let antwort = await audio.match(anfrage.url, { ignoreSearch: true });
  if (!antwort) {
    try {
      antwort = await fetch(anfrage.url);
      if (antwort.ok) await audio.put(anfrage.url, antwort.clone());
    } catch {
      return new Response('', { status: 503 });
    }
  }
  const bereich = anfrage.headers.get('range');
  if (!bereich || !antwort.ok) return antwort;
  const daten = await antwort.arrayBuffer();
  const [, von, bis] = /bytes=(\d*)-(\d*)/.exec(bereich) || [];
  const laenge = daten.byteLength;
  let start = von ? Number(von) : laenge - Number(bis || laenge);
  let ende = von && bis ? Math.min(Number(bis), laenge - 1) : laenge - 1;
  start = Math.max(0, Math.min(start, laenge - 1));
  ende = Math.max(start, ende);
  return new Response(daten.slice(start, ende + 1), {
    status: 206,
    headers: {
      'Content-Type': antwort.headers.get('Content-Type') || 'audio/mpeg',
      'Content-Range': `bytes ${start}-${ende}/${laenge}`,
      'Content-Length': String(ende - start + 1),
      'Accept-Ranges': 'bytes',
    },
  });
}
