// Wstęga LivePL odrysowana wektorowo z assets/icon.png (siatka 512).
// Ciemne części sięgają pod czerwoną wstęgę, żeby na styku nie było jasnej szczeliny.
const arc = (r, a0, a1) => { const p = a => [r*Math.cos(a*Math.PI/180), r*Math.sin(a*Math.PI/180)].map(v => v.toFixed(2)).join(' ');
  return `M ${p(a0)} A ${r} ${r} 0 0 1 ${p(a1)}`; };
module.exports = function logo(k, opt = {}) {
  const s = opt.sygnal || {};
  return `<polygon fill="${k.trzon}" points="120,130.5 209.5,130.5 171,299 81.5,299"/>
<polygon fill="${k.trzon}" points="83.5,290.5 170,290.5 165,376 152,${opt.dol||381.5} 62.5,${opt.dol||381.5}"/>
<polygon fill="${k.wstega}" points="83.5,290.5 412,290.5 372.5,337 391.5,${opt.dol||381.5} 152,${opt.dol||381.5}"/>
<g transform="translate(${s.cx||264.5} ${s.cy||240.5}) skewX(-13)" fill="none" stroke="${k.sygnal}" stroke-width="${s.w||20.5}" stroke-linecap="round">
<circle r="${s.r||16}" fill="${k.sygnal}" stroke="none"/>
<path d="${arc(s.r1||51.5, s.a0||-80, s.a1||10)}"/>
<path d="${arc(s.r2||88, s.b0||-79, s.b1||8)}"/>
</g>`;
};
