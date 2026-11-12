// Leonardo – Oberfläche der Web-App (PWA).
import * as L from './logik.js';

const SPEICHER = 'leonardo-lernstand';
const app = document.getElementById('app');

let inhalt = null;
let stand = ladeStand();
let screen = { name: 'start' };
let italienischVorne = true;
let zuletztAnimiert = -1;
const balkenBreiten = new Map();
const istIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const istInstalliert = () => navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
const wenigBewegung = matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------------------------------------------------------------------------
// Speichern (lokal im Gerät)
// ---------------------------------------------------------------------------

function ladeStand() {
  try {
    const text = localStorage.getItem(SPEICHER);
    return text ? L.standAusJson(text) : L.neuerStand();
  } catch {
    return L.neuerStand();
  }
}

function aendern(aenderung) {
  stand = aenderung(stand);
  try {
    localStorage.setItem(SPEICHER, L.standAlsJson(stand));
  } catch {
    /* Speicher voll oder privat – App läuft trotzdem weiter */
  }
}

// ---------------------------------------------------------------------------
// Vorlesen (Sprachausgabe des Geräts, offline)
// ---------------------------------------------------------------------------

const sprache = { verfuegbar: 'speechSynthesis' in window, stimme: null };

// Beste italienische Stimme: it-IT vor anderen it-Varianten, „Premium/Erweitert“ vor einfachen,
// Stimmen auf dem Gerät (offline) vor Online-Stimmen.
function stimmenWert(s) {
  return (s.lang.replace('_', '-').toLowerCase() === 'it-it' ? 4 : 0) +
    (/premium|enhanced|erweitert|verbessert/i.test(s.name) ? 2 : 0) + (s.localService ? 1 : 0);
}

function stimmeSuchen() {
  const italienisch = speechSynthesis.getVoices().filter((s) => s.lang.toLowerCase().startsWith('it'));
  const vorher = sprache.verfuegbar;
  sprache.stimme = italienisch.sort((a, b) => stimmenWert(b) - stimmenWert(a))[0] || null;
  // Safari liefert die Stimmen oft erst später (oder nie) – ohne Liste versuchen wir es trotzdem mit „it-IT“.
  sprache.verfuegbar = speechSynthesis.getVoices().length === 0 || !!sprache.stimme;
  if (vorher !== sprache.verfuegbar && inhalt) render();
}

// iOS spielt Sprache erst nach einer Berührung ab: beim ersten Tippen einmal „leer“ sprechen.
function spracheFreischalten() {
  try {
    const leer = new SpeechSynthesisUtterance(' ');
    leer.volume = 0;
    speechSynthesis.speak(leer);
  } catch {
    /* egal */
  }
}

// Aufnahmen (Google-Stimme, audio/index.json): klingen viel natürlicher als die Gerätestimme.
// Der Service Worker speichert sie im Hintergrund für offline.
let aufnahmen = null; // { "ciao, anna!": "ciao-anna.mp3", … }
const spieler = new Audio();
spieler.preload = 'auto';

const vorlesbar = () => !!aufnahmen || sprache.verfuegbar;

// Klammern, Schrägstriche und Lücken stören beim Vorlesen (gleich wie tools/make-audio.ps1).
const bereinige = (text) => text.replace(/\(.*?\)/g, ' ').replace(/\//g, ', ').replace(/…/g, ' ').replace(/_+/g, ' ').replace(/\s+/g, ' ').trim();

function stillAlles() {
  spieler.pause();
  if (sprache.verfuegbar) speechSynthesis.cancel();
}

function sprich(text) {
  if (!text) return;
  const sauber = bereinige(text);
  const datei = aufnahmen?.[sauber.toLowerCase()];
  if (datei) {
    stillAlles();
    spieler.src = `audio/${datei}`;
    spieler.play().catch(() => sprichMitGeraet(sauber));
    return;
  }
  sprichMitGeraet(sauber);
}

function sprichMitGeraet(text) {
  if (!sprache.verfuegbar || !text) return;
  try {
    // Nach dem Wechsel in den Hintergrund hängt Safari manchmal auf „pausiert“.
    speechSynthesis.resume();
    if (speechSynthesis.speaking || speechSynthesis.pending) speechSynthesis.cancel();
    const satz = new SpeechSynthesisUtterance(text);
    satz.lang = 'it-IT';
    if (sprache.stimme) satz.voice = sprache.stimme;
    satz.rate = 0.9;
    speechSynthesis.speak(satz);
  } catch {
    /* ohne Ton weiter */
  }
}

// ---------------------------------------------------------------------------
// Kleine Helfer
// ---------------------------------------------------------------------------

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const heute = () => L.tagesZahl();
const sterneText = (n) => '⭐'.repeat(n) + '☆'.repeat(Math.max(0, 3 - n));
const prozent = (a) => `${Math.round(a * 100)} %`;
const vibriere = (muster) => { try { navigator.vibrate?.(muster); } catch { /* iPhone kann das nicht */ } };
const anzahlFragen = () => (stand.einstellungen.kurzeRunden ? 10 : 18);
const anzahlKarten = () => (stand.einstellungen.kurzeRunden ? 10 : 20);

function titelTeile(titel) {
  const i = titel.indexOf(' – ');
  return i < 0 ? [null, titel] : [titel.slice(0, i), titel.slice(i + 3)];
}

const topbar = (titel, zurueck = true) => `
  <header class="topbar">
    ${zurueck ? '<button class="zurueck" data-a="zurueck" aria-label="Zurück">‹</button>' : ''}
    <h1>${esc(titel)}</h1>
  </header>`;

function ring(anteil, px, innen = '', dicke = 8) {
  const r = (px - dicke) / 2;
  const u = 2 * Math.PI * r;
  const versatz = u * (1 - Math.min(1, Math.max(0, anteil)));
  const m = px / 2;
  return `<div class="ring" style="width:${px}px;height:${px}px">
    <svg viewBox="0 0 ${px} ${px}" aria-hidden="true">
      <circle class="spur" cx="${m}" cy="${m}" r="${r}" stroke-width="${dicke}"/>
      <circle class="wert" cx="${m}" cy="${m}" r="${r}" stroke-width="${dicke}" stroke-dasharray="${u}"
        stroke-dashoffset="${versatz}" style="--u:${u}" transform="rotate(-90 ${m} ${m})"/>
    </svg><div class="ring-innen">${innen}</div></div>`;
}

const balken = (anteil, schluessel) =>
  `<div class="balken"><i data-key="${esc(schluessel)}" style="width:${Math.round(Math.min(1, Math.max(0, anteil)) * 100)}%"></i></div>`;

const emojiKreis = (emoji, klasse = '') => `<span class="emoji-kreis ${klasse}">${emoji}</span>`;
const etikett = (text, klasse = '') => `<span class="etikett ${klasse}">${esc(text)}</span>`;
const lautsprecher = (text) =>
  vorlesbar() && text ? `<button class="laut" data-a="sprich" data-v="${esc(text)}" aria-label="Anhören">🔊</button>` : '';

function genusEtikett(v) {
  if (!v.genus) return v.wortart ? etikett(v.wortart) : '';
  const plural = v.genus.includes('Plural') ? ' · Mehrzahl' : '';
  if (v.genus.startsWith('m/f')) return etikett('männlich / weiblich' + plural, 'beide');
  if (v.genus.startsWith('m')) return etikett('männlich' + plural, 'maennlich');
  return etikett('weiblich' + plural, 'weiblich');
}

function konfetti() {
  if (wenigBewegung) return;
  const schicht = document.createElement('div');
  schicht.className = 'konfetti';
  const farben = ['var(--primaer)', 'var(--akzent)', '#E9B949', 'var(--primaer-hell)'];
  for (let i = 0; i < 32; i++) {
    const t = document.createElement('i');
    t.style.cssText = `left:${Math.random() * 100}%;--d:${(Math.random() * 0.5).toFixed(2)}s;--t:${(1.6 + Math.random()).toFixed(2)}s;` +
      `--s:${(Math.random() * 60 - 30).toFixed(0)}px;background:${farben[i % 4]};width:${8 + Math.random() * 10}px`;
    schicht.appendChild(t);
  }
  document.body.appendChild(schicht);
  setTimeout(() => schicht.remove(), 3000);
}

// ---------------------------------------------------------------------------
// Bildschirme
// ---------------------------------------------------------------------------

function viewStart() {
  const h = L.heuteAntworten(stand, heute());
  const ziel = stand.einstellungen.tagesziel;
  const tage = L.serie(stand, heute());
  const naechste = L.naechsteEinheit(inhalt, stand);
  const wackelig = L.anzahlZumWiederholen(stand);
  const spruch = h >= ziel ? 'Tagesziel geschafft. Stark! 🎯' : tage > 1 ? `🔥 ${tage} Tage in Folge` : h > 0 ? 'Guter Start. Weiter so!' : 'Ein paar Minuten reichen.';

  let weiter = '';
  if (naechste) {
    const k = inhalt.kapitelVon(naechste);
    const gelesen = L.einheitStand(stand, naechste.id).erklaerung;
    weiter = aktionsKarte('weiterLernen', '▶️', 'Weiter lernen',
      `${naechste.emoji} ${esc(naechste.titel)}<br>${esc(titelTeile(k.titel)[0] || k.titel)} · ${gelesen ? 'üben' : 'erst erklären, dann üben'}`, true);
  }

  return `<main class="seite">
    <section class="begruessung">
      <div><h2>Ciao! 👋</h2><p class="leise">${spruch}</p></div>
      ${ring(h / ziel, 84, `<b>${h}</b><small>von ${ziel}</small>`)}
    </section>
    ${istIOS && !istInstalliert() && !localStorage.getItem('leonardo-hinweis-weg') ? `
      <div class="hinweis-karte">
        <p>📲 <b>Als App installieren:</b> Tippe unten auf <b>Teilen</b> (Quadrat mit Pfeil) und dann auf <b>„Zum Home-Bildschirm“</b>.</p>
        <button class="schliessen" data-a="hinweisWeg" aria-label="Hinweis schließen">✕</button>
      </div>` : ''}
    ${weiter}
    ${wackelig > 0 ? aktionsKarte('wiederholen', '🔁', 'Wiederholen', `${wackelig} Wörter und Aufgaben sind noch wackelig.`, false) : ''}
    <h3 class="abschnitt">Dein Lernpfad</h3>
    ${inhalt.kapitel.map((k) => {
      const [ober, titel] = titelTeile(k.titel);
      const anteil = L.fortschrittKapitel(inhalt, k, stand);
      const fertig = k.einheiten.filter((e) => L.einheitStand(stand, e.id).sterne > 0).length;
      const sterne = k.einheiten.reduce((s, e) => s + L.einheitStand(stand, e.id).sterne, 0);
      return `<button class="karte zeile" data-a="kapitel" data-v="${esc(k.id)}">
        ${emojiKreis(k.emoji)}
        <span class="wachsen"><small class="leise">${esc(ober || k.untertitel)}</small><b>${esc(titel)}</b>
          <small class="leise">${fertig} von ${k.einheiten.length} Einheiten${sterne ? ` · ⭐ ${sterne}` : ''}</small></span>
        ${ring(anteil, 46, `<small>${Math.round(anteil * 100)}%</small>`, 5)}
      </button>`;
    }).join('')}
    <button class="knopf dezent" data-a="einstellungen">⚙️ Einstellungen</button>
  </main>`;
}

const aktionsKarte = (aktion, emoji, titel, text, primaer) => `
  <button class="aktion ${primaer ? 'primaer' : ''}" data-a="${aktion}">
    ${emojiKreis(emoji, 'gross')}<span><b>${titel}</b><span>${text}</span></span>
  </button>`;

function viewKapitel(id) {
  const k = inhalt.kapitelNach(id);
  const [ober, titel] = titelTeile(k.titel);
  return `${topbar(ober || k.titel)}<main class="seite">
    <section class="kopf"><h2>${k.emoji} ${esc(titel)}</h2><p class="leise">${esc(k.untertitel)}</p></section>
    ${k.einheiten.map((e, i) => {
      const es = L.einheitStand(stand, e.id);
      const anteil = L.fortschrittEinheit(inhalt, e, stand);
      return `<button class="karte zeile ${es.sterne ? 'geschafft' : ''}" data-a="einheit" data-v="${esc(e.id)}">
        <span class="nummer">${es.sterne ? '✓' : i + 1}</span>
        <span class="wachsen"><b>${e.emoji} ${esc(e.titel)}</b>${balken(anteil, 'e-' + e.id)}
          <small class="leise">${es.sterne ? sterneText(es.sterne) : anteil > 0 ? prozent(anteil) + ' geübt' : 'Noch nicht begonnen'}</small></span>
      </button>`;
    }).join('')}
  </main>`;
}

function viewEinheit(id) {
  const e = inhalt.einheit(id);
  const k = inhalt.kapitelVon(e);
  const es = L.einheitStand(stand, e.id);
  const anteil = L.fortschrittEinheit(inhalt, e, stand);
  const gewusst = e.vokabeln.filter((v) => (stand.vokabeln[v.it] ?? 0) >= 1).length;
  const ueben = ['Gemischte Übungen'];
  if (e.uebungen.length) ueben.push(`${e.uebungen.length} Kursaufgaben`);
  if (e.verben.length) ueben.push(`Verben: ${e.verben.join(', ')}`);
  if (e.mix) ueben.push('mit allem aus dem Kapitel');

  return `${topbar(titelTeile(k.titel)[0] || 'Einheit')}<main class="seite">
    <section class="einheit-kopf">
      ${ring(anteil, 76, `<span class="gross-emoji">${e.emoji}</span>`)}
      <div><h2>${esc(e.titel)}</h2><p class="leise">${es.sterne ? sterneText(es.sterne) : prozent(anteil) + ' geübt'}</p></div>
    </section>
    <p class="ziel">🎯 ${esc(e.ziel)}</p>
    <button class="knopf" data-a="losgehts">${es.erklaerung ? 'Jetzt üben' : 'Los geht’s: erst verstehen, dann üben'}</button>
    ${schritt('erklaerung', '📖', '1 · Erklärung', `${e.erklaerung.length === 1 ? '1 kurze Karte' : `${e.erklaerung.length} kurze Karten`} mit Beispielen${es.erklaerung ? ' · ✓ gelesen' : ''}`)}
    ${e.vokabeln.length ? schritt('karten', '🃏', '2 · Wörter ansehen', `${gewusst} von ${e.vokabeln.length} Wörtern gewusst`, `
      <div class="chips"><span>Zuerst:</span>
        <button class="chip ${italienischVorne ? 'an' : ''}" data-a="richtung" data-v="it">Italienisch</button>
        <button class="chip ${italienischVorne ? '' : 'an'}" data-a="richtung" data-v="de">Deutsch</button></div>`) : ''}
    ${schritt('ueben', '✏️', `${e.vokabeln.length ? 3 : 2} · Üben`, ueben.join(' · '))}
    <p class="leise klein">💾 Dein Fortschritt wird nach jeder Antwort automatisch auf diesem Gerät gespeichert.</p>
  </main>`;
}

const schritt = (aktion, emoji, titel, text, zusatz = '') => `
  <div class="karte schritt">
    <button class="schritt-haupt" data-a="${aktion}">
      ${emojiKreis(emoji)}<span class="wachsen"><b>${titel}</b><small class="leise">${esc(text)}</small></span><span class="pfeil">›</span>
    </button>${zusatz}
  </div>`;

function erklaerKarte(karte) {
  return `<article class="karte erklaer">
    <h2>${esc(karte.titel)}</h2>
    ${karte.punkte.length ? `<ul>${karte.punkte.map((p) => `<li>${esc(p)}</li>`).join('')}</ul>` : ''}
    ${karte.tabelle.length ? `<div class="tabelle-rahmen"><table>${karte.tabelle.map((z, i) =>
      `<tr>${z.map((zelle) => (i === 0 ? `<th>${esc(zelle)}</th>` : `<td>${esc(zelle)}</td>`)).join('')}</tr>`).join('')}</table></div>` : ''}
    ${karte.beispiele.length ? `<h3>Beispiele</h3>${karte.beispiele.map((b) => `
      <div class="beispiel"><span class="wachsen"><b>${esc(b.it)}</b><small class="leise">${esc(b.de)}</small></span>${lautsprecher(b.it)}</div>`).join('')}` : ''}
    ${karte.merke ? `<p class="merke">💡 ${esc(karte.merke)}</p>` : ''}
  </article>`;
}

function viewErklaerung(r) {
  const karte = r.einheit.erklaerung[r.karte];
  const letzte = r.karte === r.anzahl - 1 && (r.imCheck || !r.check);
  let inhaltHtml;
  if (!r.imCheck) {
    inhaltHtml = `<p class="leise">Karte ${r.karte + 1} von ${r.anzahl}</p>${erklaerKarte(karte)}`;
  } else {
    const u = r.check.uebung;
    const beantwortet = r.gewaehlt != null;
    inhaltHtml = `<p class="markiert">✅ Kurz-Check</p><h2 class="aufgabe">${esc(u.aufgabe)}</h2>
      ${r.optionen().map((o, i) => `<button class="option ${beantwortet && o === u.loesung ? 'richtig' : ''} ${beantwortet && o === r.gewaehlt && o !== u.loesung ? 'falsch' : ''}"
        data-a="erkWaehle" data-v="${i}" ${beantwortet ? 'disabled' : ''}>${esc(o)}</button>`).join('')}
      ${beantwortet ? `<p class="titel-text pop">${r.gewaehlt === u.loesung ? 'Richtig! 🎉' : `Fast – richtig ist: ${esc(u.loesung)}`}</p>` : ''}`;
  }
  const knopfText = letzte ? 'Verstanden – jetzt üben' : !r.imCheck && r.check ? 'Kurz prüfen' : 'Weiter';
  return `${topbar('📖 ' + r.einheit.titel)}<main class="seite mit-fuss">
    ${balken((r.karte + (r.imCheck ? 0.5 : 0)) / Math.max(1, r.anzahl), 'erklaerung')}
    <div class="${animKlasse(r.karte * 2 + (r.imCheck ? 1 : 0))}">${inhaltHtml}</div>
  </main>
  <footer class="fuss">
    ${r.karte > 0 || r.imCheck ? '<button class="knopf dezent" data-a="erkZurueck">Zurück</button>' : ''}
    <button class="knopf breit" data-a="erkWeiter" ${r.imCheck && r.gewaehlt == null ? 'disabled' : ''}>${knopfText}</button>
  </footer>`;
}

function viewKarten(s) {
  const r = s.runde;
  const karte = r.aktuelle;
  if (!karte) {
    return `${topbar('🃏 ' + s.titel)}<main class="seite mitte">
      ${emojiKreis('🎉', 'riesig')}<h2>Geschafft!</h2>
      <p class="leise mittig">${r.gewusstBeimErstenMal} von ${r.anzahl} Wörtern gleich gewusst.<br>💾 Gespeichert.</p>
      <button class="knopf" data-a="zurueck">Weiter zur Einheit</button></main>`;
  }
  const v = karte.vokabel;
  const vorne = karte.italienischVorne ? v.it : v.de;
  const hinten = karte.italienischVorne ? v.de : v.it;
  return `${topbar('🃏 ' + s.titel)}<main class="seite karten-seite">
    ${balken(r.anteil, 'karten')}
    <div class="flip ${r.umgedreht ? 'umgedreht' : ''}" data-a="umdrehen" role="button" aria-label="Karte umdrehen">
      <div class="flip-innen">
        <div class="flip-seite vorne">${karte.italienischVorne ? lautsprecher(v.it) : ''}<p class="karte-wort">${esc(vorne)}</p></div>
        <div class="flip-seite hinten">${karte.italienischVorne ? '' : lautsprecher(v.it)}
          <p class="leise">${esc(vorne)}</p><p class="karte-wort">${esc(hinten)}</p>${genusEtikett(v)}</div>
      </div>
    </div>
    <div id="kartenKnoepfe">${kartenKnoepfe(r)}</div>
  </main>`;
}

const kartenKnoepfe = (r) => (r.umgedreht
  ? `<div class="reihe"><button class="knopf dezent" data-a="nochNicht">🤔 Noch nicht</button><button class="knopf" data-a="gewusst">✅ Gewusst</button></div>`
  : '<button class="knopf" data-a="umdrehen">Umdrehen</button>');

function animKlasse(schluessel) {
  if (schluessel === zuletztAnimiert || wenigBewegung) return '';
  zuletztAnimiert = schluessel;
  return 'reinrutschen';
}

function viewUeben(s) {
  const r = s.runde;
  let teil;
  if (r.fertig) teil = viewErgebnis(r);
  else if (r.aktuelle.art === 'paare') teil = viewPaare(r);
  else teil = viewAufgabe(r);
  return `${topbar('✏️ ' + r.titel)}<main class="seite">
    ${balken(r.index / Math.max(1, r.fragen.length), 'ueben')}
    <div class="${animKlasse(1000 + r.index)}">${teil}</div>
  </main>`;
}

function kopfzeile(r, typ) {
  return `<div class="etiketten">${etikett(typ, 'typ')}${r.istZweiterVersuch() ? etikett('Zweiter Versuch', 'gelb') : ''}</div>`;
}

function viewAufgabe(r) {
  const f = r.aktuelle;
  const u = f.uebung;
  const mc = L.istMC(u);
  const beantwortet = r.bewertung != null;
  let antwort;

  if (mc) {
    antwort = r.optionen().map((o, i) => {
      const k = beantwortet && o === u.loesung ? 'richtig' : beantwortet && o === r.gewaehlt ? 'falsch' : '';
      return `<button class="option ${k}" data-a="waehle" data-v="${i}" ${beantwortet ? 'disabled' : ''}>${k === 'richtig' ? '✓ ' : k === 'falsch' ? '✗ ' : ''}${esc(o)}</button>`;
    }).join('');
  } else if (f.satzbau) {
    const teile = r.bausteine();
    antwort = `<div class="satz">${r.gelegt.length ? r.gelegt.map((pos, stelle) =>
      `<button class="baustein gelegt" data-a="nimm" data-v="${stelle}">${esc(teile[pos])}</button>`).join('') :
      '<span class="leise">Tippe die Wörter in der richtigen Reihenfolge an.</span>'}</div>
      <div class="bausteine">${teile.map((w, pos) => (r.gelegt.includes(pos) ? '' : `<button class="baustein" data-a="lege" data-v="${pos}">${esc(w)}</button>`)).join('')}</div>
      ${beantwortet ? '' : `<button class="knopf" data-a="pruefeSatz" ${r.gelegt.length === teile.length ? '' : 'disabled'}>Prüfen</button>`}`;
  } else {
    antwort = `<input id="eingabe" class="eingabe" type="text" placeholder="Deine Antwort" value="${esc(r.eingabe)}"
        autocomplete="off" autocorrect="off" autocapitalize="none" spellcheck="false" enterkeyhint="done" ${beantwortet ? 'readonly' : ''}>
      ${beantwortet ? '' : `<div class="akzente">${['à', 'è', 'é', 'ì', 'ò', 'ù'].map((z) => `<button class="akzent haelt-fokus" data-a="akzent" data-v="${z}">${z}</button>`).join('')}</div>
        <button class="knopf" data-a="pruefeText">Prüfen</button>`}`;
  }

  let tipps = '';
  if (!beantwortet && !mc) {
    const stufen = [L.tippErster(u), L.tippZweiter(u)].filter(Boolean);
    tipps = `<div class="tipps">${stufen.slice(0, r.tippStufe).map((t) => `<p class="tipp pop">💡 ${esc(t)}</p>`).join('')}
      ${r.tippStufe < stufen.length ? `<button class="text-knopf haelt-fokus" data-a="tipp">💡 ${r.tippStufe ? 'Noch ein Tipp' : 'Tipp zeigen'}</button>` : ''}</div>`;
  }

  return `${kopfzeile(r, f.satzbau ? 'Sätze bauen' : L.typName(u))}
    <div class="aufgabe-zeile"><h2 class="aufgabe">${esc(u.aufgabe)}</h2>
      ${u.aufgabeDe ? `<button class="de-knopf haelt-fokus ${r.deHilfe ? 'an' : ''}" data-a="deHilfe" aria-label="Deutsche Hilfe">DE</button>` : ''}</div>
    ${r.deHilfe && u.aufgabeDe ? `<p class="de-hilfe pop">🇩🇪 ${esc(u.aufgabeDe)}</p>` : ''}
    ${u.typ === 'hoeren' && vorlesbar() ? '<button class="knopf tonal" data-a="hoerenNochmal">🔊 Nochmal anhören</button>' : ''}
    ${antwort}${tipps}
    ${beantwortet ? `${rueckmeldung(u, r.bewertung)}<button class="knopf" data-a="weiter">Weiter</button>` : ''}`;
}

function hinweise(u) {
  return `${u.generiert ? '<small class="dezent">Lösung ohne Gewähr</small>' : ''}${u.quelle ? `<small class="dezent">Quelle: ${esc(u.quelle)}</small>` : ''}`;
}

function rueckmeldung(u, bewertung) {
  const [klasse, titel] = { RICHTIG: ['richtig', '✅ Richtig!'], FAST: ['gelb', '🟡 Fast! Schau auf die Schreibweise.'], FALSCH: ['falsch', 'Noch nicht ganz.'] }[bewertung];
  return `<section class="rueckmeldung ${klasse} pop">
    <b>${titel}</b>
    <div class="loesung-zeile"><span class="wachsen"><span class="loesung">${esc(u.loesung)}</span>
      ${u.loesungDe ? `<span>= ${esc(u.loesungDe)}</span>` : ''}${u.typ === 'hoeren' && u.vorlesen ? `<span>🔊 ${esc(u.vorlesen)}</span>` : ''}</span>
      ${lautsprecher(L.vorleseText(u))}</div>
    ${u.alternativen.length && !L.istMC(u) && !u.id.startsWith('w:') ? `<small>Auch richtig: ${esc(u.alternativen.join(' / '))}</small>` : ''}
    ${u.hinweis ? `<small>💡 ${esc(u.hinweis)}</small>` : ''}
    ${hinweise(u)}
  </section>`;
}

function viewPaare(r) {
  const f = r.aktuelle;
  if (!r.paare) {
    r.paare = { links: L.mische(f.vokabeln), rechts: L.mische(f.vokabeln), gefunden: new Set(), mitFehler: new Set(), auswahl: null, falsch: null, fehler: 0 };
  }
  const p = r.paare;
  const fertig = r.bewertung != null;
  return `${kopfzeile(r, 'Paare finden')}
    <h2 class="aufgabe">Was gehört zusammen?</h2>
    <p class="leise">Tippe erst links ein italienisches Wort an, dann rechts die Bedeutung.</p>
    <div class="paare">
      <div>${p.links.map((v, i) => `<button class="paar ${p.gefunden.has(v.it) ? 'richtig' : p.auswahl === v.it ? 'gewaehlt' : ''}"
        data-a="paarLinks" data-v="${i}" ${p.gefunden.has(v.it) || fertig ? 'disabled' : ''}>${esc(v.it)}</button>`).join('')}</div>
      <div>${p.rechts.map((v, i) => `<button class="paar ${p.gefunden.has(v.it) ? 'richtig' : p.falsch === v.it ? 'falsch wackeln' : ''}"
        data-a="paarRechts" data-v="${i}" ${p.gefunden.has(v.it) || fertig || !p.auswahl ? 'disabled' : ''}>${esc(v.de)}</button>`).join('')}</div>
    </div>
    ${fertig ? `<p class="titel-text pop">${p.fehler === 0 ? '✅ Alle Paare ohne Fehler!' : `Geschafft – mit ${p.fehler} ${p.fehler === 1 ? 'Fehler' : 'Fehlern'}.`}</p>
      <button class="knopf" data-a="weiter">Weiter</button>` : ''}`;
}

function viewErgebnis(r) {
  const richtig = r.ergebnisse.filter((e) => e.bewertung === 'RICHTIG').length;
  const fast = r.ergebnisse.filter((e) => e.bewertung === 'FAST').length;
  const nochmal = r.ergebnisse.filter((e) => e.bewertung !== 'RICHTIG' && e.frage.art === 'aufgabe').map((e) => e.frage.uebung);
  return `<section class="mitte">
    ${emojiKreis(r.sterne >= 2 ? '🎉' : '💪', 'riesig')}
    <h2>Geschafft!</h2>
    ${r.einheitId ? `<p class="sterne">${sterneText(r.sterne)}</p>` : ''}
    <p class="leise mittig">${richtig} von ${r.ergebnisse.length} richtig${fast ? `, ${fast} fast richtig` : ''}.<br>💾 Dein Fortschritt ist gespeichert.</p>
    <button class="knopf" data-a="zurueck">Fertig</button>
  </section>
  ${nochmal.length ? `<h3 class="abschnitt">Das schauen wir uns nochmal an</h3>${nochmal.map((u) => `
    <div class="karte klein-karte"><small>${esc(u.aufgabe)}</small><b>${esc(u.loesung)}${u.loesungDe ? ` = ${esc(u.loesungDe)}` : ''}</b>${hinweise(u)}</div>`).join('')}` : ''}`;
}

function viewEinstellungen() {
  const e = stand.einstellungen;
  const schalter = (name, titel, text) => `<label class="schalter"><span class="wachsen"><b>${titel}</b><small class="leise">${text}</small></span>
    <input type="checkbox" data-c="${name}" ${e[name] ? 'checked' : ''}><i></i></label>`;
  const woerter = Object.values(stand.vokabeln).filter((w) => w >= 1).length;
  const aufgaben = Object.values(stand.uebungen).filter((w) => w >= 1).length;
  const einheiten = Object.values(stand.einheiten).filter((x) => x.sterne > 0).length;
  return `${topbar('Einstellungen')}<main class="seite">
    <section class="karte gruppe"><h2>Runden</h2>
      ${schalter('kurzeRunden', 'Kurze Runden', '10 statt 18 Aufgaben pro Übung.')}
      <b>Tagesziel</b><small class="leise">So viele Antworten möchtest du pro Tag schaffen.</small>
      <div class="chips">${[10, 20, 40].map((z) => `<button class="chip ${e.tagesziel === z ? 'an' : ''}" data-a="ziel" data-v="${z}">${z}</button>`).join('')}</div>
    </section>
    <section class="karte gruppe"><h2>Lesen und Hören</h2>
      ${schalter('grosseSchrift', 'Größere Schrift', 'Alle Texte werden etwas größer.')}
      ${vorlesbar() ? `${schalter('autoVorlesen', 'Automatisch vorlesen', 'Italienische Wörter und Lösungen werden vorgelesen.')}
        <button class="text-knopf" data-a="sprich" data-v="Ciao! Come stai?">🔊 Stimme testen</button>
        ${aufnahmen ? `<small class="leise" id="audioStand">🎧 Aussprache: echte Aufnahmen${audioStand.gesamt ? ` · ${audioStandText()}` : ''}</small>` : ''}
        <small class="leise">Nichts zu hören? Lautstärke hochdrehen.${!aufnahmen && sprache.stimme ? ` Stimme: ${esc(sprache.stimme.name)}` : ''}</small>`
        : '<small class="leise">Vorlesen ist auf diesem Gerät gerade nicht möglich (keine italienische Stimme gefunden).</small>'}
    </section>
    <section class="karte gruppe"><h2>Dein Lernstand</h2>
      <small>💾 Wird nach jeder Antwort auf diesem Gerät gespeichert – ohne Internet.<br>
        ${woerter} Wörter gewusst · ${aufgaben} Aufgaben gelöst · ${einheiten} Einheiten abgeschlossen</small>
      <small class="leise">Tipp: Sichere deinen Lernstand ab und zu als Datei, z. B. bevor du Safari-Daten löschst.</small>
      <button class="knopf dezent" data-a="export">Lernstand als Datei sichern</button>
      <label class="knopf dezent">Lernstand aus Datei laden<input type="file" id="importDatei" accept="application/json,.json" hidden></label>
      <button class="text-knopf" data-a="reset">Lernstand zurücksetzen</button>
    </section>
  </main>`;
}

// ---------------------------------------------------------------------------
// Darstellen
// ---------------------------------------------------------------------------

function view() {
  switch (screen.name) {
    case 'kapitel': return viewKapitel(screen.id);
    case 'einheit': return viewEinheit(screen.id);
    case 'erklaerung': return viewErklaerung(screen.runde);
    case 'karten': return viewKarten(screen);
    case 'ueben': return viewUeben(screen);
    case 'einstellungen': return viewEinstellungen();
    default: return viewStart();
  }
}

function render({ neu = false } = {}) {
  const hatteFokus = document.activeElement?.id === 'eingabe';
  const alt = new Map([...document.querySelectorAll('.balken i[data-key]')].map((el) => [el.dataset.key, el.style.width]));
  app.innerHTML = view();
  if (neu && !wenigBewegung) app.firstElementChild?.classList.add('einblenden');

  // Balken weich von der alten zur neuen Breite füllen.
  for (const el of app.querySelectorAll('.balken i[data-key]')) {
    const vorher = alt.get(el.dataset.key) ?? balkenBreiten.get(el.dataset.key);
    const ziel = el.style.width;
    balkenBreiten.set(el.dataset.key, ziel);
    if (vorher != null && vorher !== ziel && !wenigBewegung) {
      el.style.transition = 'none';
      el.style.width = vorher;
      requestAnimationFrame(() => requestAnimationFrame(() => { el.style.transition = ''; el.style.width = ziel; }));
    }
  }
  const eingabe = document.getElementById('eingabe');
  if (eingabe && hatteFokus && !eingabe.readOnly) {
    eingabe.focus();
    eingabe.setSelectionRange(eingabe.value.length, eingabe.value.length);
  }
}

function zeige(neu) {
  stillAlles();
  screen = neu;
  render({ neu: true });
  window.scrollTo(0, 0);
}

// ---------------------------------------------------------------------------
// Abläufe
// ---------------------------------------------------------------------------

function speichereErgebnis(frage, bewertung, ersterVersuch) {
  aendern((s) => {
    let n = L.mitAntwort(s, heute());
    if (ersterVersuch && frage.art === 'aufgabe') {
      const ok = bewertung !== 'FALSCH';
      const f = frage.fortschritt;
      if (f.art === 'aufgabe' || f.art === 'verb') n = L.mitUebung(n, f.schluessel, ok);
      else if (f.art === 'wort') n = L.mitVokabel(n, f.schluessel, ok);
    }
    return n;
  });
}

const speichereWort = (v, gewusst) => aendern((s) => L.mitVokabel(s, v.it, gewusst));

function starteErklaerung(e) {
  zeige({ name: 'erklaerung', runde: new L.ErklaerRunde(e, e.erklaerung.map((k) => L.kurzCheck(k, e))) });
}

function starteUeben(e) {
  const fragen = L.fragenFuerEinheit(inhalt, e, stand, anzahlFragen(), vorlesbar());
  zeige({ name: 'ueben', zurueck: { name: 'einheit', id: e.id }, runde: new L.UebenRunde(fragen, e.titel, e.id, speichereErgebnis, speichereWort) });
  frageBeginnt();
}

function starteWiederholung() {
  const fragen = L.fragenWiederholung(inhalt, stand, anzahlFragen(), vorlesbar());
  if (!fragen.length) return aktionen.weiterLernen();
  zeige({ name: 'ueben', zurueck: { name: 'start' }, runde: new L.UebenRunde(fragen, 'Wiederholen', null, speichereErgebnis, speichereWort) });
  frageBeginnt();
}

function starteKarten(e) {
  // Gewichtete Auswahl: neue und nicht gewusste Wörter kommen öfter dran.
  const karten = e.vokabeln
    .map((v) => [v, Math.random() ** (1 / (L.MAX_FACH + 1 - (stand.vokabeln[v.it] ?? 0)) ** 2)])
    .sort((a, b) => b[1] - a[1])
    .slice(0, anzahlKarten())
    .map(([vokabel]) => ({ vokabel, italienischVorne }));
  const runde = new L.KartenRunde(karten, (k, gewusst) => aendern((s) => L.mitAntwort(L.mitVokabel(s, k.vokabel.it, gewusst), heute())));
  zeige({ name: 'karten', zurueck: { name: 'einheit', id: e.id }, titel: e.titel, runde });
  if (italienischVorne && karten.length && stand.einstellungen.autoVorlesen) sprich(karten[0].vokabel.it);
}

/** Beim Start einer neuen Frage: Hören-Fragen sofort abspielen, Eingabefeld fokussieren. */
function frageBeginnt() {
  const f = screen.runde?.aktuelle;
  if (!f || f.art !== 'aufgabe') return;
  if (f.uebung.typ === 'hoeren') sprich(f.uebung.vorlesen);
  document.getElementById('eingabe')?.focus();
}

function nachAntwort() {
  const r = screen.runde;
  vibriere(r.bewertung === 'FALSCH' ? [30, 40, 30] : 20);
  const u = r.aktuelle?.uebung;
  if (u && stand.einstellungen.autoVorlesen) sprich(L.vorleseText(u));
  document.activeElement?.blur();
  render();
}

const aktuelleEinheit = () => inhalt.einheit(screen.id);

const aktionen = {
  zurueck() {
    switch (screen.name) {
      case 'karten':
      case 'ueben': return zeige(screen.zurueck);
      case 'erklaerung': return zeige({ name: 'einheit', id: screen.runde.einheit.id });
      case 'einheit': return zeige({ name: 'kapitel', id: aktuelleEinheit().kapitelId });
      default: return zeige({ name: 'start' });
    }
  },
  einstellungen: () => zeige({ name: 'einstellungen' }),
  kapitel: (id) => zeige({ name: 'kapitel', id }),
  einheit(id) {
    aendern((s) => ({ ...s, letzteEinheit: id }));
    zeige({ name: 'einheit', id });
  },
  weiterLernen() {
    const e = L.naechsteEinheit(inhalt, stand);
    if (!e) return;
    aendern((s) => ({ ...s, letzteEinheit: e.id }));
    if (L.einheitStand(stand, e.id).erklaerung) starteUeben(e); else starteErklaerung(e);
  },
  wiederholen: () => starteWiederholung(),
  losgehts() {
    const e = aktuelleEinheit();
    if (L.einheitStand(stand, e.id).erklaerung) starteUeben(e); else starteErklaerung(e);
  },
  erklaerung: () => starteErklaerung(aktuelleEinheit()),
  karten: () => starteKarten(aktuelleEinheit()),
  ueben: () => starteUeben(aktuelleEinheit()),
  richtung(v) { italienischVorne = v === 'it'; render(); },
  sprich: (text) => sprich(text),
  hinweisWeg() { localStorage.setItem('leonardo-hinweis-weg', '1'); render(); },

  // Erklärung
  erkWeiter() {
    const r = screen.runde;
    const letzte = r.karte === r.anzahl - 1 && (r.imCheck || !r.check);
    if (letzte) {
      aendern((s) => L.mitErklaerungGelesen(s, r.einheit.id));
      starteUeben(r.einheit);
    } else {
      r.weiter();
      render();
      window.scrollTo(0, 0);
    }
  },
  erkZurueck() { screen.runde.zurueck(); render(); },
  erkWaehle(i) {
    const r = screen.runde;
    r.waehle(r.optionen()[Number(i)]);
    sprich(r.check?.uebung.vorlesen);
    vibriere(20);
    render();
  },

  // Karteikarten
  umdrehen() {
    const r = screen.runde;
    if (!r.aktuelle) return;
    r.umgedreht = !r.umgedreht;
    document.querySelector('.flip')?.classList.toggle('umgedreht', r.umgedreht);
    document.getElementById('kartenKnoepfe').innerHTML = kartenKnoepfe(r);
    if (stand.einstellungen.autoVorlesen && r.aktuelle.italienischVorne !== r.umgedreht) sprich(r.aktuelle.vokabel.it);
  },
  gewusst() { kartenBewerten(true); },
  nochNicht() { kartenBewerten(false); },

  // Üben
  waehle(i) {
    const r = screen.runde;
    r.waehle(r.optionen()[Number(i)]);
    nachAntwort();
  },
  pruefeText() {
    const el = document.getElementById('eingabe');
    if (!el || !el.value.trim()) return;
    screen.runde.pruefeText(el.value);
    nachAntwort();
  },
  akzent(zeichen) {
    const el = document.getElementById('eingabe');
    if (!el || el.readOnly) return;
    const start = el.selectionStart ?? el.value.length;
    const ende = el.selectionEnd ?? el.value.length;
    el.value = el.value.slice(0, start) + zeichen + el.value.slice(ende);
    el.setSelectionRange(start + zeichen.length, start + zeichen.length);
    screen.runde.eingabe = el.value;
    el.focus();
  },
  lege(pos) { screen.runde.legeBaustein(Number(pos)); render(); },
  nimm(stelle) { screen.runde.nimmBaustein(Number(stelle)); render(); },
  pruefeSatz() { screen.runde.pruefeSatzbau(); nachAntwort(); },
  tipp() {
    const r = screen.runde;
    r.naechsterTipp();
    if (!L.tippErster(r.aktuelle.uebung) && r.tippStufe === 1) r.naechsterTipp();
    render();
  },
  deHilfe() { screen.runde.deHilfe = !screen.runde.deHilfe; render(); },
  hoerenNochmal() { sprich(screen.runde.aktuelle?.uebung.vorlesen); },
  weiter() {
    const r = screen.runde;
    r.weiter();
    if (r.fertig && !r.gespeichert) {
      r.gespeichert = true;
      if (r.einheitId) aendern((s) => L.mitSternen(s, r.einheitId, r.sterne));
      if (r.sterne >= 2) setTimeout(konfetti, 150);
    }
    render();
    window.scrollTo(0, 0);
    frageBeginnt();
  },
  paarLinks(i) {
    const p = screen.runde.paare;
    const v = p.links[Number(i)];
    p.auswahl = v.it;
    sprich(v.it);
    render();
  },
  paarRechts(i) {
    const r = screen.runde;
    const p = r.paare;
    const v = p.rechts[Number(i)];
    const gewaehlt = p.links.find((x) => x.it === p.auswahl);
    if (!gewaehlt) return;
    if (gewaehlt.it === v.it) {
      p.gefunden.add(v.it);
      r.paarBewertet(v, !p.mitFehler.has(v.it));
      p.auswahl = null;
      vibriere(15);
      if (p.gefunden.size === r.aktuelle.vokabeln.length) r.paareFertig(p.fehler);
    } else {
      p.fehler++;
      p.mitFehler.add(gewaehlt.it);
      p.falsch = v.it;
      vibriere([30, 40, 30]);
      setTimeout(() => {
        if (screen.runde === r && r.paare === p) { p.falsch = null; render(); }
      }, 600);
    }
    render();
  },

  // Einstellungen
  ziel(n) { aendern((s) => ({ ...s, einstellungen: { ...s.einstellungen, tagesziel: Number(n) } })); render(); },
  export() {
    const blob = new Blob([L.standAlsJson(stand)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `leonardo-lernstand-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
  },
  reset() {
    if (!confirm('Alle Sterne, gelernten Wörter und gelösten Aufgaben werden gelöscht. Die Einstellungen bleiben. Fortfahren?')) return;
    aendern((s) => ({ ...L.neuerStand(), einstellungen: s.einstellungen }));
    render();
  },
};

function kartenBewerten(gewusst) {
  const r = screen.runde;
  r.bewerte(gewusst);
  vibriere(gewusst ? 20 : [30, 40, 30]);
  render();
  if (!r.aktuelle && r.anzahl) setTimeout(konfetti, 150);
  else if (r.aktuelle?.italienischVorne && stand.einstellungen.autoVorlesen) sprich(r.aktuelle.vokabel.it);
}

// ---------------------------------------------------------------------------
// Ereignisse
// ---------------------------------------------------------------------------

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (!el || el.disabled) return;
  const aktion = aktionen[el.dataset.a];
  if (aktion) aktion(el.dataset.v);
});

// Diese Knöpfe sollen das Eingabefeld nicht schließen (Tastatur bleibt offen).
document.addEventListener('pointerdown', (e) => {
  if (e.target.closest('.haelt-fokus') && document.activeElement?.id === 'eingabe') e.preventDefault();
});

document.addEventListener('input', (e) => {
  if (e.target.id === 'eingabe' && screen.runde) screen.runde.eingabe = e.target.value;
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && e.target.id === 'eingabe') {
    e.preventDefault();
    aktionen.pruefeText();
  }
});

document.addEventListener('change', (e) => {
  const name = e.target.dataset?.c;
  if (name) {
    aendern((s) => ({ ...s, einstellungen: { ...s.einstellungen, [name]: e.target.checked } }));
    document.documentElement.classList.toggle('gross', stand.einstellungen.grosseSchrift);
    render();
  }
  if (e.target.id === 'importDatei' && e.target.files?.[0]) {
    e.target.files[0].text().then((text) => {
      const neu = L.standAusJson(text);
      if (confirm('Diesen Lernstand laden? Der aktuelle wird ersetzt.')) {
        aendern(() => neu);
        document.documentElement.classList.toggle('gross', stand.einstellungen.grosseSchrift);
        render();
      }
    });
  }
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

async function starte() {
  if (sprache.verfuegbar) {
    stimmeSuchen();
    speechSynthesis.addEventListener?.('voiceschanged', stimmeSuchen);
    document.addEventListener('pointerdown', spracheFreischalten, { once: true, capture: true });
  }
  document.addEventListener('visibilitychange', () => { if (document.hidden) stillAlles(); });
  document.documentElement.classList.toggle('gross', stand.einstellungen.grosseSchrift);
  try {
    const liste = await (await fetch('content/index.json')).json();
    const jsons = await Promise.all(liste.map((name) => fetch(`content/${name}`).then((r) => r.json())));
    inhalt = L.baueInhalt(jsons);
  } catch {
    app.innerHTML = '<main class="seite mitte"><h2>Ups</h2><p class="leise mittig">Die Inhalte konnten nicht geladen werden. Öffne die App einmal mit Internet, danach geht sie offline.</p></main>';
    return;
  }
  try {
    aufnahmen = (await (await fetch('audio/index.json')).json()).dateien;
  } catch {
    aufnahmen = null; // keine Aufnahmen → Gerätestimme
  }
  render({ neu: true });
  if (aufnahmen) audioOfflineSpeichern();
}

// ---------------------------------------------------------------------------
// Aufnahmen offline speichern (macht der Service Worker im Hintergrund)
// ---------------------------------------------------------------------------

const audioStand = { fertig: 0, gesamt: 0 };
const audioStandText = () => (audioStand.fertig >= audioStand.gesamt
  ? '✓ offline gespeichert'
  : `wird gespeichert … ${audioStand.fertig} von ${audioStand.gesamt}`);

async function audioOfflineSpeichern() {
  if (!('serviceWorker' in navigator)) return;
  const reg = await navigator.serviceWorker.ready;
  reg.active?.postMessage('audio-speichern');
}

navigator.serviceWorker?.addEventListener('message', (e) => {
  if (e.data?.art !== 'audio') return;
  audioStand.fertig = e.data.fertig;
  audioStand.gesamt = e.data.gesamt;
  const zeile = document.getElementById('audioStand');
  if (zeile) zeile.textContent = `🎧 Aussprache: echte Aufnahmen · ${audioStandText()}`;
});

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js', { scope: './' }).catch(() => {});
}
navigator.storage?.persist?.().catch(() => {});

// Für Tests im Browser erreichbar.
window.leonardo = { logik: L, get stand() { return stand; }, get screen() { return screen; } };

starte();
