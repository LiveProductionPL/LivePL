'use strict';

// Języki dla tłumacza stron: polskie nazwy i rozpoznawanie języka tekstu.
// Rozpoznawanie działa w tym komputerze: najpierw po piśmie (cyrylica, arabskie, CJK…),
// a dla alfabetu łacińskiego po najczęstszych słowach. Atrybut lang strony pomaga,
// ale nie wygrywa z wyraźnym wynikiem z tekstu (wiele polskich stron ma lang="en" z szablonu).

const NAZWY = {
  af: 'afrikaans', ar: 'arabski', az: 'azerski', be: 'białoruski', bg: 'bułgarski', bn: 'bengalski',
  bs: 'bośniacki', ca: 'kataloński', cs: 'czeski', da: 'duński', de: 'niemiecki', el: 'grecki',
  en: 'angielski', es: 'hiszpański', et: 'estoński', eu: 'baskijski', fa: 'perski', fi: 'fiński',
  fr: 'francuski', gl: 'galicyjski', gu: 'gudżarati', he: 'hebrajski', hi: 'hindi', hr: 'chorwacki',
  hu: 'węgierski', id: 'indonezyjski', is: 'islandzki', it: 'włoski', ja: 'japoński', kn: 'kannada',
  ko: 'koreański', lt: 'litewski', lv: 'łotewski', ml: 'malajalam', mr: 'marathi', ms: 'malajski',
  nb: 'norweski', nn: 'norweski (nynorsk)', nl: 'niderlandzki', pl: 'polski', pt: 'portugalski',
  ro: 'rumuński', ru: 'rosyjski', sk: 'słowacki', sl: 'słoweński', sq: 'albański', sr: 'serbski',
  sv: 'szwedzki', ta: 'tamilski', te: 'telugu', th: 'tajski', tl: 'tagalski', tr: 'turecki',
  ug: 'ujgurski', uk: 'ukraiński', ur: 'urdu', vi: 'wietnamski', 'zh-Hans': 'chiński (uproszczony)',
  'zh-Hant': 'chiński (tradycyjny)'
};

const nazwa = (kod) => NAZWY[kod] || kod || 'nieznany';

// najczęstsze słowa (bez tych, które wyglądają tak samo w wielu językach naraz)
const SLOWA = {
  en: 'the and of to is that for it with as was on are be this by you not or have from at which but will can they we has more their been about your would there what when were who also into than',
  de: 'der die und den von zu das mit sich des auf für ist im dem nicht ein eine als auch es werden aus er hat dass sie nach wird bei einer um sind noch wie einem über zum war haben nur oder aber vor zur bis mehr durch wurde sein kann ich',
  fr: 'le la les de des et un une du est que qui dans pour pas au sur par ne se plus ce il avec sont ou son aux été mais nous vous cette leur être comme elle tout',
  es: 'el la de que y en los del se las por un para con no una su al lo como más pero sus le ya este porque esta entre cuando muy sin sobre también ser es son fue ha hay',
  it: 'il di che è la un per una sono ma ho lo ha le si del della dei delle non con come anche più gli nel nella alla al questo sua suo essere stato',
  pt: 'o a e de que do da em um para é com não uma os no se na por mais as dos como mas foi ao ele das tem à seu sua ou ser quando muito há nos já está também são pelo pela sob num numa',
  nl: 'de het een en van ik te dat die in is niet op aan met voor er zijn maar ook als bij of om door naar dan nog wel wordt worden deze uit over kan hij zij wij',
  pl: 'i w z na się nie do to że jest o jak a po co ale od za przez dla są jego już czy tak ich oraz był jej może być tylko które który która także przy tym gdy jako',
  cs: 'a se na je že v o s z do to k ve by i pro jsou jako ale od po za jak jeho které který která také byl bylo není tak jsem nebo však při této tento',
  sk: 'a sa na je že v o s z do to k vo by aj pre sú ako ale od po za jeho ktoré ktorý ktorá tiež bol bolo nie tak som alebo však pri tejto tento',
  sv: 'och i att det som en på är av för med till den har de inte om ett han men var jag sig från vi så kan man när år hon under också efter',
  da: 'og i at det en til er som på de med han af for ikke der var mig sig men et har om vi min havde ham hun nu over da fra du ud sin dem os blev være',
  nb: 'og i det er en til på som å av for med har de ikke den at var jeg et om men seg han fra vi kan så eller hun ble også skal etter sin når være',
  fi: 'ja on ei se että oli hän mutta kun niin ovat myös joka tai ole jos kuin mitä sen tämä olla voi vain nyt jo sitten',
  hu: 'a az és hogy nem is egy meg de van ez csak már mint el ki még volt vagy ha azt ami kell lesz nagyon mert sem után között',
  ro: 'și de la în cu pe a nu un o care să din pentru se este mai ce sunt au fost sau ca al dar lui prin acest această',
  tr: 've bir bu da de için ile çok ne gibi daha olarak olan ama en kadar sonra veya her ki mi değil var ancak şey',
  hr: 'i je u na se da za su od a s o ili koji koja koje kao iz sa ali bi što to nije biti bio ima samo',
  sl: 'in je v na se da za so z s ki pa ne to od kot tudi bi ali po do iz sem smo bo že lahko',
  lt: 'ir yra kad į su iš o bet kaip buvo tai ne jo jis ji už apie prie per nuo nes taip arba dar tik',
  lv: 'un ir ka ar no uz par kas bet arī tā vai to kā ko tas pie viņš bija nav jau kur pēc',
  et: 'ja on ei et see ka oli kui ta aga mis või ning siis nii kes oma seda kõik veel nagu pole ole üle',
  ca: 'el la de i que a en els les per amb del un una no és al com més però seu seva va han ha aquest aquesta també pel dels',
  id: 'yang dan di ini itu dengan untuk dari dalam tidak ke akan pada juga ada adalah oleh atau saya kami mereka bisa tersebut telah sudah',
  ms: 'yang dan di ini itu dengan untuk dari dalam tidak ke akan pada juga ada ialah oleh atau saya kami mereka boleh kerana daripada telah sudah',
  vi: 'của và là có không được các cho những một trong người này với đã để khi từ như nhiều',
  sq: 'të e në dhe një për me që nga është së nuk u do si ka janë por edhe',
  is: 'og að í á sem er um við ekki til það var með fyrir en hann sig af ég þá hún eru',
  af: 'die en van is in het dat nie te wat op vir met word sy hy ook as aan deur kan was om maar',
  tl: 'ang ng mga sa na at ay ni si para hindi ko ito ka siya kung may din rin lang ako',
  gl: 'unha non máis tamén coa polo pola cun cunha nun nunha dende cando ten xa iso isto moi aínda',
  eu: 'eta da ez du bat ere den zen dira dute baina edo hau honek bere egin izan behar oso',
  az: 'və bir bu ilə üçün da də olan kimi ki isə olaraq onun daha çox ən sonra amma'
};

const ZBIORY = Object.fromEntries(Object.entries(SLOWA).map(([k, v]) => [k, new Set(v.split(' '))]));

// pomocnicze znaki: litery charakterystyczne tylko dla jednego języka
const ZNAKI = [
  [/[ąćęłńśźż]/g, 'pl', 3], [/[ěůřť]/g, 'cs', 3], [/[ľĺŕô]/g, 'sk', 3], [/[őű]/g, 'hu', 3],
  [/[ăîșțşţ]/g, 'ro', 2], [/[ğış]/g, 'tr', 3], [/[ß]/g, 'de', 3], [/[äöü]/g, 'de', 0.5],
  [/[ñ¿¡]/g, 'es', 3], [/[ãõç]/g, 'pt', 1.5], [/[øæ]/g, 'da', 1], [/[å]/g, 'sv', 0.5],
  [/[ðþ]/g, 'is', 4], [/[ėųūį]/g, 'lt', 3], [/[āēīūņļķģ]/g, 'lv', 3], [/[õ]/g, 'et', 0.5],
  [/[ə]/g, 'az', 5], [/[ơưđạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]/g, 'vi', 5],
  [/[ë]/g, 'sq', 0.7], [/[đ]/g, 'hr', 2], [/[čšž]/g, 'hr', 0.3]
];

function normalizuj(kod) {
  const k = String(kod || '').trim().replace(/_/g, '-').toLowerCase();
  if (!k) return null;
  if (/^zh-(tw|hk|mo|hant)/.test(k)) return 'zh-Hant';
  if (/^zh/.test(k)) return 'zh-Hans';
  const glowny = k.split('-')[0];
  const zamiany = { iw: 'he', in: 'id', fil: 'tl', no: 'nb', ji: 'yi' };
  const wynik = zamiany[glowny] || glowny;
  return /^[a-z]{2,3}$/.test(wynik) ? wynik : null;
}

const ile = (tekst, re) => (tekst.match(re) || []).length;

// pismo inne niż łacińskie rozstrzyga od razu
function poPismie(t) {
  const litery = ile(t, /\p{L}/gu) || 1;
  const udzial = (re) => ile(t, re) / litery;
  if (udzial(/[぀-ヿ]/g) > 0.05) return 'ja';
  if (udzial(/[가-힯ᄀ-ᇿ]/g) > 0.2) return 'ko';
  if (udzial(/[一-鿿]/g) > 0.3) {
    const trad = ile(t, /[們這國說時個來為會學門東車馬長開關見話頭電區體點經從麼對嗎們還與實現發應當]/g);
    const upr = ile(t, /[们这国说时个来为会学门东车马长开关见话头电区体点经从么对吗还与实现发应当]/g);
    return trad > upr ? 'zh-Hant' : 'zh-Hans';
  }
  if (udzial(/[Ѐ-ӿ]/g) > 0.3) {
    if (/[јђћљњџ]/i.test(t)) return 'sr';
    if (/ў/i.test(t)) return 'be';
    if (/[їєґ]/i.test(t) || (/і/i.test(t) && !/[ыэ]/i.test(t))) return 'uk';
    if (ile(t, /ъ/gi) / litery > 0.01 && !/[ыэё]/i.test(t)) return 'bg';
    return 'ru';
  }
  if (udzial(/[Ͱ-Ͽἀ-῿]/g) > 0.3) return 'el';
  if (udzial(/[֐-׿]/g) > 0.3) return 'he';
  if (udzial(/[؀-ۿݐ-ݿ]/g) > 0.3) {
    if (/[ئېۆۈۋ]/.test(t)) return 'ug';
    if (/[ٹڈڑںے]/.test(t)) return 'ur';
    if (/[پچژگی]/.test(t)) return 'fa';
    return 'ar';
  }
  if (udzial(/[฀-๿]/g) > 0.3) return 'th';
  if (udzial(/[ऀ-ॿ]/g) > 0.3) return ile(t, /ळ/g) > 2 ? 'mr' : 'hi';
  if (udzial(/[ঀ-৿]/g) > 0.3) return 'bn';
  if (udzial(/[஀-௿]/g) > 0.3) return 'ta';
  if (udzial(/[ఀ-౿]/g) > 0.3) return 'te';
  if (udzial(/[ಀ-೿]/g) > 0.3) return 'kn';
  if (udzial(/[ഀ-ൿ]/g) > 0.3) return 'ml';
  if (udzial(/[઀-૿]/g) > 0.3) return 'gu';
  return null;
}

// wynik: { kod, pewny } albo null, gdy tekstu jest za mało
function zTekstu(tekst) {
  const t = String(tekst || '').slice(0, 6000);
  if (ile(t, /\p{L}/gu) < 20) return null;
  const pismo = poPismie(t);
  if (pismo) return { kod: pismo, pewny: true };
  const slowa = t.toLowerCase().match(/\p{L}+/gu) || [];
  if (slowa.length < 4) return null;
  const punkty = {};
  for (const w of slowa) {
    for (const [kod, zbior] of Object.entries(ZBIORY)) if (zbior.has(w)) punkty[kod] = (punkty[kod] || 0) + 1;
  }
  const male = t.toLowerCase();
  for (const [re, kod, waga] of ZNAKI) {
    const n = ile(male, re);
    if (n) punkty[kod] = (punkty[kod] || 0) + Math.min(n, slowa.length / 2) * waga * 0.5;
  }
  const ranking = Object.entries(punkty).sort((a, b) => b[1] - a[1]);
  if (!ranking.length) return null;
  const [kod, najlepszy] = ranking[0];
  const drugi = ranking[1] ? ranking[1][1] : 0;
  const udzial = najlepszy / slowa.length;
  return { kod, pewny: udzial >= 0.12 && najlepszy >= 3 && najlepszy >= drugi * 1.25 };
}

// języki bliskie sobie: atrybut lang strony rozstrzyga między nimi
const RODZINY = [['da', 'nb', 'nn', 'sv'], ['hr', 'bs', 'sr', 'sl'], ['id', 'ms'], ['cs', 'sk'], ['es', 'gl', 'ca', 'pt'], ['zh-Hans', 'zh-Hant']];
const bliskie = (a, b) => a === b || RODZINY.some(r => r.includes(a) && r.includes(b));

// lang: atrybut lang strony (albo nagłówek Content-Language); probka: kawałek widocznego tekstu
function rozpoznaj(probka, lang) {
  const zLang = normalizuj(lang);
  const zTxt = zTekstu(probka);
  if (zTxt && zTxt.pewny) {
    if (zLang && bliskie(zLang, zTxt.kod)) return { kod: zLang, pewny: true };
    return { kod: zTxt.kod, pewny: true };
  }
  if (zLang) return { kod: zLang, pewny: !!(zTxt && bliskie(zTxt.kod, zLang)) };
  if (zTxt) return { kod: zTxt.kod, pewny: false };
  return null;
}

module.exports = { NAZWY, nazwa, normalizuj, zTekstu, rozpoznaj };
