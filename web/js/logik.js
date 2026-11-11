// Leonardo – Lernlogik ohne Oberfläche.
// Entspricht der Android-App (AnswerChecker.kt, Fragen.kt, Sessions.kt, Lernstand.kt).

export const MAX_FACH = 5;

// ---------------------------------------------------------------------------
// Antworten prüfen
// ---------------------------------------------------------------------------

export function normalisiere(text) {
  return text
    .toLowerCase()
    .replace(/[’‘`´]/g, "'")
    .replace(/\s*'\s*/g, "'")
    .replace(/[.,!?;:¿¡…"“”„«»()–-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export const ohneAkzente = (text) => text.normalize('NFD').replace(/\p{Mn}+/gu, '');

/** "paralre" statt "parlare": genau zwei benachbarte Buchstaben vertauscht. */
function istBuchstabendreher(a, b) {
  if (a.length !== b.length || a.length < 4) return false;
  let i = 0;
  while (i < a.length && a[i] === b[i]) i++;
  if (i >= a.length) return false;
  return i + 1 < a.length && a[i] === b[i + 1] && a[i + 1] === b[i] && a.slice(i + 2) === b.slice(i + 2);
}

/** "abiamo" statt "abbiamo": Unterschied nur bei verdoppelten Buchstaben. */
function nurDoppelbuchstabeAnders(a, b) {
  if (Math.min(a.length, b.length) < 4) return false;
  const doppelt = /(.)\1+/g;
  return a.replace(doppelt, '$1') === b.replace(doppelt, '$1');
}

/** Ergebnis: 'RICHTIG', 'FAST' (nur Akzent/Tippfehler) oder 'FALSCH'. */
export function pruefe(eingabe, loesungen) {
  const antwort = normalisiere(eingabe);
  if (!antwort) return 'FALSCH';
  const ziele = loesungen.map(normalisiere);
  if (ziele.includes(antwort)) return 'RICHTIG';
  const a = ohneAkzente(antwort);
  const fast = ziele.map(ohneAkzente).some((z) => a === z || istBuchstabendreher(a, z) || nurDoppelbuchstabeAnders(a, z));
  return fast ? 'FAST' : 'FALSCH';
}

// ---------------------------------------------------------------------------
// Inhalte (aus den Kapitel-JSONs in ./content)
// ---------------------------------------------------------------------------

const leer = (s) => (s == null || String(s).trim() === '' ? null : s);

function uebungAus(u) {
  return {
    id: u.id,
    typ: u.typ,
    aufgabe: u.aufgabe,
    loesung: u.loesung,
    aufgabeDe: leer(u.aufgabe_de),
    loesungDe: leer(u.loesung_de),
    alternativen: u.alternativen || [],
    optionen: u.optionen || [],
    hinweis: leer(u.hinweis),
    generiert: !!u.loesung_generiert,
    quelle: leer(u.quelle),
    vorlesen: null,
  };
}

function einheitAus(e, kapitelId) {
  return {
    id: e.id,
    kapitelId,
    titel: e.titel,
    emoji: e.emoji || '📘',
    ziel: e.ziel || '',
    erklaerung: (e.erklaerung || []).map((k) => ({
      titel: k.titel,
      punkte: k.punkte || [],
      beispiele: (k.beispiele || []).map((b) => ({ it: b.it, de: b.de })),
      tabelle: k.tabelle || [],
      merke: leer(k.merke),
    })),
    vokabeln: (e.vokabeln || []).map((v) => ({ it: v.it, de: v.de, wortart: leer(v.wortart), genus: leer(v.genus) })),
    uebungen: (e.uebungen || []).map(uebungAus),
    verben: e.verben || [],
    mix: !!e.mix,
  };
}

export function baueInhalt(jsons) {
  const verben = {};
  for (const j of jsons) {
    for (const v of j.verben || []) {
      verben[v.inf] = { inf: v.inf, de: v.de, gruppe: v.gruppe, formen: v.formen, formenDe: v.formen_de, generiert: !!v.loesung_generiert };
    }
  }
  const kapitel = jsons
    .map((j) => ({
      id: j.kapitel_id,
      nummer: j.nummer ?? 99,
      titel: j.titel,
      untertitel: j.untertitel || '',
      emoji: j.emoji || '📘',
      einheiten: (j.einheiten || []).map((e) => einheitAus(e, j.kapitel_id)),
    }))
    .sort((a, b) => a.nummer - b.nummer);
  const einheiten = kapitel.flatMap((k) => k.einheiten);
  const einheitNachId = new Map(einheiten.map((e) => [e.id, e]));
  const kapitelNachId = new Map(kapitel.map((k) => [k.id, k]));
  return {
    kapitel,
    verben,
    einheiten,
    einheit: (id) => (id ? einheitNachId.get(id) : undefined),
    kapitelNach: (id) => kapitelNachId.get(id),
    kapitelVon: (e) => kapitelNachId.get(e.kapitelId),
  };
}

export const istMC = (u) => u.optionen.length > 0 && (u.typ === 'multiple_choice' || u.typ === 'hoeren');

export function typName(u) {
  return { lueckentext: 'Lückentext', uebersetzung: 'Übersetzung', konjugation: 'Verbform', multiple_choice: 'Auswahl', hoeren: 'Hören' }[u.typ] || u.typ;
}

/** Was nach dem Beantworten vorgelesen wird (nur italienischer Text). */
export const vorleseText = (u) => u.vorlesen ?? (!istMC(u) || u.loesungDe ? u.loesung : null);

// ---------------------------------------------------------------------------
// Lernstand (gleiches JSON-Format wie in der Android-App)
// ---------------------------------------------------------------------------

export function neuerStand() {
  return {
    vokabeln: {},
    uebungen: {},
    einheiten: {},
    einstellungen: { kurzeRunden: true, grosseSchrift: false, autoVorlesen: true, tagesziel: 20 },
    letzterTag: 0,
    antwortenAmLetztenTag: 0,
    serie: 0,
    letzteEinheit: null,
  };
}

const zahlen = (o) => Object.fromEntries(Object.entries(o && typeof o === 'object' ? o : {}).filter(([, v]) => Number.isFinite(v)));

export function standAusJson(text) {
  const n = neuerStand();
  try {
    const j = JSON.parse(text);
    const e = j.einstellungen || {};
    const einheiten = {};
    for (const [id, x] of Object.entries(j.einheiten || {})) {
      einheiten[id] = { sterne: Number(x.sterne) || 0, erklaerung: !!x.erklaerung };
    }
    return {
      vokabeln: zahlen(j.vokabeln),
      uebungen: zahlen(j.uebungen),
      einheiten,
      einstellungen: {
        kurzeRunden: e.kurzeRunden ?? n.einstellungen.kurzeRunden,
        grosseSchrift: e.grosseSchrift ?? n.einstellungen.grosseSchrift,
        autoVorlesen: e.autoVorlesen ?? n.einstellungen.autoVorlesen,
        tagesziel: Number(e.tagesziel) || n.einstellungen.tagesziel,
      },
      letzterTag: Number(j.letzterTag) || 0,
      antwortenAmLetztenTag: Number(j.antwortenAmLetztenTag) || 0,
      serie: Number(j.serie) || 0,
      letzteEinheit: typeof j.letzteEinheit === 'string' ? j.letzteEinheit : null,
    };
  } catch {
    return n;
  }
}

export const standAlsJson = (s) => JSON.stringify({ version: 1, ...s });

/** Heutiger Tag als Tageszahl (wie LocalDate.toEpochDay). */
export function tagesZahl(datum = new Date()) {
  return Math.floor((datum.getTime() - datum.getTimezoneOffset() * 60000) / 86400000);
}

export const heuteAntworten = (s, tag) => (s.letzterTag === tag ? s.antwortenAmLetztenTag : 0);
export const serie = (s, tag) => (s.letzterTag >= tag - 1 ? s.serie : 0);
export const einheitStand = (s, id) => s.einheiten[id] || { sterne: 0, erklaerung: false };

export function mitAntwort(s, tag) {
  if (s.letzterTag === tag) return { ...s, antwortenAmLetztenTag: s.antwortenAmLetztenTag + 1 };
  if (s.letzterTag === tag - 1) return { ...s, letzterTag: tag, antwortenAmLetztenTag: 1, serie: s.serie + 1 };
  return { ...s, letzterTag: tag, antwortenAmLetztenTag: 1, serie: 1 };
}

export function mitVokabel(s, wort, gewusst) {
  const neu = gewusst ? Math.min((s.vokabeln[wort] ?? 0) + 1, MAX_FACH) : 0;
  return { ...s, vokabeln: { ...s.vokabeln, [wort]: neu } };
}

export function mitUebung(s, schluessel, richtig) {
  const neu = richtig ? Math.min((s.uebungen[schluessel] ?? 0) + 1, 3) : 0;
  return { ...s, uebungen: { ...s.uebungen, [schluessel]: neu } };
}

export function mitSternen(s, id, sterne) {
  const alt = einheitStand(s, id);
  return { ...s, einheiten: { ...s.einheiten, [id]: { ...alt, sterne: Math.max(alt.sterne, sterne) } } };
}

export function mitErklaerungGelesen(s, id) {
  return { ...s, einheiten: { ...s.einheiten, [id]: { ...einheitStand(s, id), erklaerung: true } } };
}

// ---------------------------------------------------------------------------
// Fortschritt
// ---------------------------------------------------------------------------

export const verbSchluessel = (v, person) => `k:${v.inf}:${person}`;

export function fortschrittEinheit(inhalt, e, s) {
  let gesamt = e.uebungen.length + e.vokabeln.length;
  let geschafft = e.uebungen.filter((u) => (s.uebungen[u.id] ?? 0) >= 1).length + e.vokabeln.filter((v) => (s.vokabeln[v.it] ?? 0) >= 1).length;
  if (e.kapitelId === 'verbi') {
    for (const name of e.verben) {
      const v = inhalt.verben[name];
      if (!v) continue;
      gesamt += 6;
      for (let p = 0; p < 6; p++) if ((s.uebungen[verbSchluessel(v, p)] ?? 0) >= 1) geschafft++;
    }
  }
  if (gesamt === 0) return einheitStand(s, e.id).sterne > 0 ? 1 : 0;
  return geschafft / gesamt;
}

export function fortschrittKapitel(inhalt, k, s) {
  if (!k.einheiten.length) return 0;
  return k.einheiten.reduce((summe, e) => summe + fortschrittEinheit(inhalt, e, s), 0) / k.einheiten.length;
}

export function naechsteEinheit(inhalt, s) {
  const lernpfad = inhalt.kapitel.filter((k) => k.id !== 'verbi').flatMap((k) => k.einheiten);
  const zuletzt = inhalt.einheit(s.letzteEinheit);
  if (zuletzt && einheitStand(s, zuletzt.id).sterne === 0) return zuletzt;
  return lernpfad.find((e) => einheitStand(s, e.id).sterne === 0) || lernpfad[lernpfad.length - 1];
}

export const anzahlZumWiederholen = (s) =>
  Object.values(s.uebungen).filter((w) => w === 0).length + Object.values(s.vokabeln).filter((w) => w <= 1).length;

// ---------------------------------------------------------------------------
// Fragen bauen
// ---------------------------------------------------------------------------

/** Kleiner Zufallsgenerator mit Startwert (für Tests). */
export function zufallMitStart(start) {
  let a = start >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function mische(liste, rnd = Math.random) {
  const a = [...liste];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

const eindeutig = (liste, schluessel) => {
  const gesehen = new Set();
  return liste.filter((x) => {
    const k = schluessel(x);
    if (gesehen.has(k)) return false;
    gesehen.add(k);
    return true;
  });
};

const PERSONEN = ['io', 'tu', 'lui / lei / Lei', 'noi', 'voi', 'loro'];
const PERSONEN_DE = ['ich', 'du', 'er / sie', 'wir', 'ihr', 'sie'];

export function genusText(genus) {
  if (!genus) return null;
  const plural = genus.includes('Plural') ? ', Mehrzahl' : '';
  if (genus.startsWith('m/f')) return 'männlich / weiblich';
  if (genus.startsWith('m')) return 'männlich' + plural;
  if (genus.startsWith('f')) return 'weiblich' + plural;
  return genus;
}

/** Akzeptierte Schreibweisen: ohne Artikel, ohne Klammer, einzelne Teile bei „a / b“. */
export function varianten(italienisch) {
  const teile = italienisch.split(' / ').map((t) => t.trim());
  const basis = [...teile, italienisch].flatMap((t) => [t, t.replace(/\s*\(.*?\)/g, '').trim(), t.replace(/[()]/g, '').trim()]);
  const ohneArtikel = basis.map((t) => t.replace(/^(il|lo|la|i|gli|le)\s+|^l'/, '').trim());
  return [...new Set([...basis, ...ohneArtikel])].filter((t) => t && t !== italienisch);
}

/** Tipp 1: deutsche Bedeutung (oder Hinweis). Tipp 2: Anfangsbuchstaben. */
export function tippErster(u) {
  if (u.loesungDe && !u.aufgabe.toLowerCase().includes(u.loesungDe.toLowerCase())) return `Auf Deutsch: ${u.loesungDe}`;
  return u.hinweis || null;
}

export const tippZweiter = (u) =>
  'Beginnt mit: ' + u.loesung.split(' ').filter((w) => w.trim()).map((w) => [...w][0] + '…').join('   ');

function wortFrage(inhalt, v, fach, alle, mitHoeren, rnd) {
  let andere = mische(alle.filter((x) => x.de !== v.de && x.it !== v.it), rnd).slice(0, 3);
  if (!andere.length) andere = mische(inhalt.einheiten.flatMap((e) => e.vokabeln).filter((x) => x.de !== v.de), rnd).slice(0, 3);
  const stufe = fach ?? -1;
  const schreibbar = v.it.length <= 30 && !v.it.includes('…') && v.it.split(' ').length <= 4;
  const basis = { id: `w:${v.it}`, aufgabeDe: null, loesungDe: null, alternativen: [], optionen: [], hinweis: null, generiert: false, quelle: null, vorlesen: v.it };
  let uebung;
  if (stufe >= 3 && schreibbar) {
    uebung = {
      ...basis,
      typ: 'uebersetzung',
      aufgabe: `Schreib auf Italienisch: „${v.de}“`,
      loesung: v.it,
      alternativen: varianten(v.it),
      hinweis: [v.wortart, genusText(v.genus)].filter(Boolean).join(' · ') || null,
    };
  } else if (stufe >= 1) {
    uebung = { ...basis, typ: 'multiple_choice', aufgabe: `Wie sagt man „${v.de}“ auf Italienisch?`, loesung: v.it, loesungDe: v.de, optionen: [...andere.map((x) => x.it), v.it] };
  } else if (mitHoeren && Math.floor(rnd() * 3) === 0) {
    uebung = { ...basis, typ: 'hoeren', aufgabe: 'Hör genau hin: Was bedeutet das?', loesung: v.de, optionen: [...andere.map((x) => x.de), v.de] };
  } else {
    uebung = { ...basis, typ: 'multiple_choice', aufgabe: `Was heißt „${v.it}“?`, loesung: v.de, optionen: [...andere.map((x) => x.de), v.de] };
  }
  return { art: 'aufgabe', uebung, fortschritt: { art: 'wort', schluessel: v.it }, satzbau: false };
}

function verbFrage(v, person) {
  const deutsch = v.de.split(';')[0].split(',')[0].trim();
  const id = verbSchluessel(v, person);
  return {
    art: 'aufgabe',
    satzbau: false,
    fortschritt: { art: 'verb', schluessel: id },
    uebung: {
      id,
      typ: 'konjugation',
      aufgabe: `${v.inf} – ${PERSONEN[person]}`,
      aufgabeDe: `${deutsch} – ${PERSONEN_DE[person]}`,
      loesung: v.formen[person],
      loesungDe: v.formenDe[person],
      alternativen: [],
      optionen: [],
      hinweis: null,
      generiert: v.generiert,
      quelle: 'Verben-Trainer',
      vorlesen: v.formen[person],
    },
  };
}

function mischeFragen(inhalt, uebungen, vokabeln, verben, stand, anzahl, mitHoeren, nurSchwache, rnd) {
  // Dringlichkeit: zuletzt falsch → neu → unsicher → sicher (Zufall nur einmal pro Eintrag).
  const prio = (w) => (w === 0 ? 0 : w == null ? 1 : 1 + w) + rnd() * 0.9;
  const nachPrio = (liste, wert) => liste.map((x) => [x, prio(wert(x))]).sort((a, b) => a[1] - b[1]).map((p) => p[0]);

  let uebK = nachPrio(uebungen, (u) => stand.uebungen[u.id]);
  let vokK = nachPrio(vokabeln, (v) => stand.vokabeln[v.it]);
  let verbK = nachPrio(verben.flatMap((v) => [0, 1, 2, 3, 4, 5].map((p) => [v, p])), ([v, p]) => stand.uebungen[verbSchluessel(v, p)]);
  if (nurSchwache) {
    uebK = uebK.filter((u) => (stand.uebungen[u.id] ?? 0) < 3);
    vokK = vokK.filter((v) => (stand.vokabeln[v.it] ?? 0) < 3);
    verbK = verbK.filter(([v, p]) => (stand.uebungen[verbSchluessel(v, p)] ?? 0) < 2);
  }

  // Anteile: Kursaufgaben 50 %, Wörter 30 %, Verben 20 % – je nachdem, was es gibt.
  const gewichte = [uebK.length ? 5 : 0, vokK.length ? 3 : 0, verbK.length ? 2 : 0];
  const summe = gewichte.reduce((a, b) => a + b, 0);
  if (!summe) return [];
  const verfuegbar = [uebK.length, vokK.length, verbK.length];
  const soll = gewichte.map((g, i) => Math.min(Math.round((anzahl * g) / summe), verfuegbar[i]));
  let rest = anzahl - soll.reduce((a, b) => a + b, 0);
  while (rest > 0 && soll.some((x, i) => x < verfuegbar[i])) {
    for (let i = 0; i < 3; i++) if (rest > 0 && soll[i] < verfuegbar[i]) { soll[i]++; rest--; }
  }

  const fragen = [];
  for (const u of uebK.slice(0, soll[0])) {
    const moeglich = u.typ === 'uebersetzung' && u.loesung.split(' ').length >= 3 && u.loesung.split(' ').length <= 8 && !u.aufgabe.startsWith('Trenne');
    const satzbau = moeglich && (stand.uebungen[u.id] ?? 0) < 2 && rnd() < 0.5;
    fragen.push({ art: 'aufgabe', uebung: u, fortschritt: { art: 'aufgabe', schluessel: u.id }, satzbau });
  }

  const alleWoerter = vokabeln.length ? vokabeln : inhalt.einheiten.flatMap((e) => e.vokabeln);
  const gewaehlt = vokK.slice(0, soll[1]);
  const paare = gewaehlt.length >= 5 && anzahl >= 6 ? gewaehlt.slice(0, 4) : null;
  for (const v of gewaehlt.slice(paare ? 4 : 0)) fragen.push(wortFrage(inhalt, v, stand.vokabeln[v.it], alleWoerter, mitHoeren, rnd));
  for (const [v, p] of verbK.slice(0, soll[2])) fragen.push(verbFrage(v, p));

  const gemischt = mische(fragen, rnd);
  const leicht = gemischt.findIndex((f) => istMC(f.uebung));
  if (leicht > 0) gemischt.unshift(gemischt.splice(leicht, 1)[0]);
  if (paare) gemischt.splice(Math.min(2, gemischt.length), 0, { art: 'paare', vokabeln: paare });
  return gemischt;
}

export function fragenFuerEinheit(inhalt, einheit, stand, anzahl, mitHoeren, rnd = Math.random) {
  const kapitel = inhalt.kapitelVon(einheit);
  const pool = einheit.mix && kapitel ? kapitel.einheiten : [einheit];
  const uebungen = eindeutig([...einheit.uebungen, ...pool.flatMap((e) => e.uebungen)], (u) => u.id);
  const vokabeln = eindeutig(pool.flatMap((e) => e.vokabeln), (v) => v.it);
  const verben = [...new Set(pool.flatMap((e) => e.verben))].map((n) => inhalt.verben[n]).filter(Boolean);
  return mischeFragen(inhalt, uebungen, vokabeln, verben, stand, anzahl, mitHoeren, false, rnd);
}

/** Wiederholung über alles, was schon einmal geübt wurde. */
export function fragenWiederholung(inhalt, stand, anzahl, mitHoeren, rnd = Math.random) {
  const begonnen = inhalt.einheiten.filter((e) => {
    const es = einheitStand(stand, e.id);
    return es.sterne > 0 || es.erklaerung || e.uebungen.some((u) => u.id in stand.uebungen);
  });
  const uebungen = eindeutig(begonnen.flatMap((e) => e.uebungen).filter((u) => u.id in stand.uebungen), (u) => u.id);
  const vokabeln = eindeutig(begonnen.flatMap((e) => e.vokabeln).filter((v) => v.it in stand.vokabeln), (v) => v.it);
  const verben = [...new Set(begonnen.flatMap((e) => e.verben))].map((n) => inhalt.verben[n]).filter(Boolean);
  return mischeFragen(inhalt, uebungen, vokabeln, verben, stand, anzahl, mitHoeren, true, rnd);
}

/** Kurz-Check direkt nach einer Erklärkarte (wird nicht gespeichert). */
export function kurzCheck(karte, einheit, rnd = Math.random) {
  if (!karte.beispiele.length) return null;
  const beispiel = karte.beispiele[Math.floor(rnd() * karte.beispiele.length)];
  const andere = mische(
    [...new Set([...einheit.erklaerung.flatMap((k) => k.beispiele).map((b) => b.de), ...einheit.vokabeln.map((v) => v.de)])].filter((d) => d !== beispiel.de),
    rnd,
  ).slice(0, 3);
  if (andere.length < 2) return null;
  return {
    art: 'aufgabe',
    satzbau: false,
    fortschritt: { art: 'keiner' },
    uebung: {
      id: `check:${einheit.id}:${beispiel.it}`,
      typ: 'multiple_choice',
      aufgabe: `Kurz-Check: Was passt zu „${beispiel.it}“?`,
      loesung: beispiel.de,
      aufgabeDe: null,
      loesungDe: null,
      alternativen: [],
      optionen: [...andere, beispiel.de],
      hinweis: null,
      generiert: false,
      quelle: null,
      vorlesen: beispiel.it,
    },
  };
}

// ---------------------------------------------------------------------------
// Runden (Zustand ohne Oberfläche)
// ---------------------------------------------------------------------------

/** Karteikarten: nicht gewusste Karten kommen 3 Karten später wieder (max. 2×). */
export class KartenRunde {
  constructor(karten, beiBewertung) {
    this.warteschlange = [...karten];
    this.anzahl = karten.length;
    this.wiederholungen = new Map();
    this.gesehen = new Set();
    this.gewusstBeimErstenMal = 0;
    this.schritt = 0;
    this.umgedreht = false;
    this.beiBewertung = beiBewertung;
  }
  get aktuelle() { return this.warteschlange[0]; }
  get verbleibend() { return this.warteschlange.length; }
  get anteil() { return this.schritt + this.verbleibend === 0 ? 0 : this.schritt / (this.schritt + this.verbleibend); }
  bewerte(gewusst) {
    const karte = this.warteschlange.shift();
    if (!karte) return;
    const k = karte.vokabel.it;
    if (!this.gesehen.has(k)) {
      this.gesehen.add(k);
      this.beiBewertung(karte, gewusst);
      if (gewusst) this.gewusstBeimErstenMal++;
    }
    if (!gewusst) {
      const bisher = this.wiederholungen.get(k) || 0;
      if (bisher < 2) {
        this.wiederholungen.set(k, bisher + 1);
        this.warteschlange.splice(Math.min(3, this.warteschlange.length), 0, karte);
      }
    }
    this.umgedreht = false;
    this.schritt++;
  }
}

/** Übungsrunde. Falsche Antworten kommen am Ende einmal als "zweiter Versuch". */
export class UebenRunde {
  constructor(fragen, titel, einheitId, beiErgebnis, beiPaar, rnd = Math.random) {
    this.fragen = [...fragen];
    this.anzahlErst = fragen.length;
    this.titel = titel;
    this.einheitId = einheitId;
    this.beiErgebnis = beiErgebnis;
    this.beiPaar = beiPaar;
    this.rnd = rnd;
    this.index = 0;
    this.ergebnisse = [];
    this.optionenCache = new Map();
    this.bausteinCache = new Map();
    this.gespeichert = false;
    this.zuruecksetzen();
  }
  zuruecksetzen() {
    this.eingabe = '';
    this.gewaehlt = null;
    this.bewertung = null;
    this.tippStufe = 0;
    this.deHilfe = false;
    this.gelegt = [];
    this.paare = null;
  }
  get aktuelle() { return this.fragen[this.index]; }
  get fertig() { return this.index >= this.fragen.length; }
  istZweiterVersuch(i = this.index) { return i >= this.anzahlErst; }
  optionen(i = this.index) {
    if (!this.optionenCache.has(i)) this.optionenCache.set(i, mische([...new Set(this.fragen[i].uebung.optionen)], this.rnd));
    return this.optionenCache.get(i);
  }
  bausteine(i = this.index) {
    if (!this.bausteinCache.has(i)) {
      const woerter = this.fragen[i].uebung.loesung.split(' ').filter((w) => w.trim());
      let gemischt = mische(woerter, this.rnd);
      for (let n = 0; n < 5 && woerter.length > 1 && gemischt.join(' ') === woerter.join(' '); n++) gemischt = mische(woerter, this.rnd);
      this.bausteinCache.set(i, gemischt);
    }
    return this.bausteinCache.get(i);
  }
  waehle(option) {
    const f = this.aktuelle;
    if (!f || f.art !== 'aufgabe' || this.bewertung) return;
    this.gewaehlt = option;
    this.abschliessen(option === f.uebung.loesung ? 'RICHTIG' : 'FALSCH');
  }
  pruefeText(text) {
    const f = this.aktuelle;
    if (!f || f.art !== 'aufgabe' || this.bewertung || !text.trim()) return;
    this.eingabe = text;
    this.abschliessen(pruefe(text, [f.uebung.loesung, ...f.uebung.alternativen]));
  }
  legeBaustein(pos) { if (!this.bewertung && !this.gelegt.includes(pos)) this.gelegt.push(pos); }
  nimmBaustein(stelle) { if (!this.bewertung) this.gelegt.splice(stelle, 1); }
  pruefeSatzbau() {
    const f = this.aktuelle;
    if (!f || this.bewertung || !this.gelegt.length) return;
    const teile = this.bausteine();
    this.abschliessen(pruefe(this.gelegt.map((p) => teile[p]).join(' '), [f.uebung.loesung, ...f.uebung.alternativen]));
  }
  paarBewertet(vokabel, gewusst) { if (!this.istZweiterVersuch()) this.beiPaar(vokabel, gewusst); }
  paareFertig(fehler) {
    if (this.bewertung) return;
    this.abschliessen(fehler === 0 ? 'RICHTIG' : fehler <= 2 ? 'FAST' : 'FALSCH');
  }
  naechsterTipp() { if (this.tippStufe < 2) this.tippStufe++; }
  weiter() { this.index++; this.zuruecksetzen(); }
  get sterne() {
    if (!this.ergebnisse.length) return 0;
    const punkte = this.ergebnisse.reduce((s, e) => s + (e.bewertung === 'RICHTIG' ? 2 : e.bewertung === 'FAST' ? 1 : 0), 0) / (2 * this.ergebnisse.length);
    return punkte >= 0.9 ? 3 : punkte >= 0.7 ? 2 : 1;
  }
  abschliessen(ergebnis) {
    const f = this.aktuelle;
    if (!f) return;
    this.bewertung = ergebnis;
    const erster = !this.istZweiterVersuch();
    this.beiErgebnis(f, ergebnis, erster);
    if (erster) {
      this.ergebnisse.push({ frage: f, bewertung: ergebnis });
      if (ergebnis === 'FALSCH' && f.art === 'aufgabe') this.fragen.push(f);
    }
  }
}

/** Erklärung: Karte für Karte, nach jeder Karte ggf. ein Kurz-Check. */
export class ErklaerRunde {
  constructor(einheit, checks, rnd = Math.random) {
    this.einheit = einheit;
    this.checks = checks;
    this.rnd = rnd;
    this.karte = 0;
    this.imCheck = false;
    this.gewaehlt = null;
    this.optionenCache = new Map();
  }
  get anzahl() { return this.einheit.erklaerung.length; }
  get check() { return this.checks[this.karte] || null; }
  optionen() {
    if (!this.optionenCache.has(this.karte)) this.optionenCache.set(this.karte, mische(this.check?.uebung.optionen || [], this.rnd));
    return this.optionenCache.get(this.karte);
  }
  waehle(option) { if (this.gewaehlt == null) this.gewaehlt = option; }
  weiter() {
    if (!this.imCheck && this.check) { this.imCheck = true; return; }
    this.imCheck = false;
    this.gewaehlt = null;
    this.karte++;
  }
  zurueck() {
    if (this.imCheck) { this.imCheck = false; this.gewaehlt = null; } else if (this.karte > 0) this.karte--;
  }
}
