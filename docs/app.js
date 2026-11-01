/* Petal Clock website · a browser-side illustration of the live wallpaper.
   The Android APK is not bundled or represented as tested. */
(() => {
  'use strict';
  const GLYPHS = {"0":{"outer":"M51 1 C79 -2 98 25 98 65 C98 110 77 142 47 141 C20 141 2 115 3 74 C2 33 24 3 51 1 Z","slit":"M44 79 C43 65 50 51 61 44","slitWidth":7.4},"1":{"outer":"M59 1 C72 -3 88 6 89 23 L89 117 C89 135 77 142 59 140 C40 140 32 128 32 110 L32 54 C24 60 12 60 5 51 C-3 39 0 27 12 18 L40 4 C48 1 53 0 59 1 Z"},"2":{"outer":"M8 28 C13 6 34 -3 58 1 C84 2 101 23 97 52 C96 69 78 85 56 103 L86 103 C106 103 106 140 85 140 L17 140 C-1 140 -4 123 7 109 L51 61 C62 49 59 38 51 39 C44 39 44 50 30 53 C12 56 1 44 8 28 Z","slit":"M42 31 C48 24 58 26 61 39","slitWidth":5},"3":{"outer":"M14 13 C31 -3 62 -4 81 10 C103 24 103 46 88 65 C106 84 104 114 85 130 C62 149 28 143 10 128 C-4 115 7 92 25 91 C35 90 38 99 51 99 C66 98 68 87 54 85 C41 84 35 76 36 67 C37 57 44 51 56 48 C65 45 63 35 52 36 C40 37 38 45 28 44 C10 44 2 28 14 13 Z"},"4":{"outer":"M59 1 C78 -2 91 9 91 28 L91 73 C107 76 107 106 91 110 L91 119 C91 136 79 142 63 140 C48 140 39 131 39 115 L15 115 C-1 115 -5 102 1 87 L38 15 C43 5 49 2 59 1 Z","slit":"M46 81 C51 68 57 56 61 45","slitWidth":6},"5":{"outer":"M24 2 L80 2 C103 2 103 36 82 37 L49 37 L45 55 C80 43 103 73 98 101 C91 145 35 154 10 128 C-2 112 6 95 24 94 C38 94 39 106 54 106 C74 106 72 82 52 83 C45 83 37 88 25 86 C9 83 7 68 10 55 L12 18 C12 8 17 3 24 2 Z"},"6":{"outer":"M66 1 C88 -2 97 22 83 35 C75 43 64 40 56 46 C88 40 101 61 98 91 C98 123 77 143 49 141 C17 141 1 118 3 81 C4 47 16 22 38 8 C47 3 57 1 66 1 Z","slit":"M40 91 C42 77 54 70 63 72","slitWidth":7},"7":{"outer":"M13 1 L88 1 C105 1 108 14 101 30 L59 128 C53 143 38 145 22 136 C8 128 9 114 16 99 L41 39 L12 39 C-6 39 -7 2 13 1 Z"},"8":{"outer":"M51 0 C80 -1 100 16 99 43 C99 57 93 66 83 72 C96 80 100 93 97 111 C91 139 72 144 50 143 C19 143 2 125 2 107 C0 90 6 79 18 72 C6 63 3 53 4 40 C6 16 26 0 51 0 Z","slit":"M43 48 C46 39 56 36 60 46 M40 106 C43 95 57 93 61 105","slitWidth":5.5},"9":{"outer":"M52 0 C84 0 101 23 99 60 C98 91 84 117 60 134 C41 149 13 136 18 116 C21 103 37 106 44 97 C10 100 0 74 4 43 C7 17 23 1 52 0 Z","slit":"M39 52 C42 39 54 35 63 45","slitWidth":7}};
  const phone = document.getElementById('phone-demo');
  const digits = Array.from(document.querySelectorAll('[data-digit-index]'));
  const dots = Array.from(document.querySelectorAll('[data-dot]'));
  const wakeBtn = document.getElementById('wake-btn');
  const sleepBtn = document.getElementById('sleep-btn');
  const replayBtn = document.getElementById('replay-btn');
  if (!phone || digits.length !== 4) return;
  const wallpaper = phone.querySelector('.wallpaper');
  const wallpaperVignette = phone.querySelector('.wallpaper-vignette');
  const dateLock = document.getElementById('date-lock');
  const status = phone.querySelector('.screen-status');
  const lockBottom = phone.querySelector('.lock-bottom');
  const aodFooter = document.getElementById('aod-footer');
  const dateAod = document.getElementById('aod-date');
  const statusTime = document.getElementById('status-time');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let progress = 0; // 0 = simulated outlined clock, 1 = awake lockscreen
  let animationFrame = null;
  let animationSerial = 0;
  let lastDigits = '';
  let hasInteracted = false;
  const from = [
    {x:47.8,y:28.0,angle:43}, {x:62.0,y:34.7,angle:29},
    {x:38.3,y:40.5,angle:23}, {x:53.5,y:48.6,angle:15}
  ];
  const to = [
    {x:43.9,y:32.1,angle:44}, {x:67.8,y:44.5,angle:31},
    {x:34.3,y:50.9,angle:26}, {x:54.3,y:64.9,angle:16}
  ];
  const dotFrom = [{x:49.8,y:35.4}, {x:50,y:40.4}];
  const dotTo = [{x:49.6,y:43.4}, {x:50,y:51.7}];
  const mix = (a,b,t) => a + (b-a)*t;
  const clamp = v => Math.max(0,Math.min(1,v));
  const smooth = v => { v=clamp(v); return v*v*(3-2*v); };
  const ease = v => 1-Math.pow(1-clamp(v),3);

  function svgForDigit(value, i) {
    const glyph = GLYPHS[value] || GLYPHS['0'];
    const id = `petal-cut-${i}`;
    const slit = glyph.slit || '';
    const sw = glyph.slitWidth || 6;
    const mask = `<mask id="${id}" maskUnits="userSpaceOnUse" x="-20" y="-20" width="140" height="190"><rect x="-20" y="-20" width="140" height="190" fill="white"/>${slit ? `<path d="${slit}" stroke="black" stroke-width="${sw}" fill="none" stroke-linecap="round"/>` : ''}</mask>`;
    const color = i < 2 ? '#fafcff' : '#d8dfff';
    return `<svg viewBox="-10 -10 120 160" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" preserveAspectRatio="xMidYMid meet"><defs>${mask}</defs><g class="digit-glow" mask="url(#${id})"><path d="${glyph.outer}" fill="${color}"/></g><g class="digit-fill" mask="url(#${id})"><path d="${glyph.outer}" fill="${color}"/></g><g class="digit-outline" stroke-linecap="round" stroke-linejoin="round"><path d="${glyph.outer}"/><path d="${glyph.outer}" stroke-width=".6" opacity=".5"/>${slit ? `<path d="${slit}" stroke-width="1.9"/>` : ''}</g></svg>`;
  }

  function refreshTime() {
    const now = new Date();
    const hh = String(now.getHours()).padStart(2,'0');
    const mm = String(now.getMinutes()).padStart(2,'0');
    const time = hh+mm;
    if (time !== lastDigits) {
      lastDigits = time;
      digits.forEach((element,i) => { element.innerHTML = svgForDigit(time[i],i); element.dataset.row = i >= 2 ? 'low':'top'; });
      render();
    }
    const shortWeek = new Intl.DateTimeFormat('en',{weekday:'short'}).format(now);
    const shortMonth = new Intl.DateTimeFormat('en',{month:'short'}).format(now);
    const month = new Intl.DateTimeFormat('en',{month:'long'}).format(now);
    const day = now.getDate();
    const year = now.getFullYear();
    const weekday = new Intl.DateTimeFormat('en',{weekday:'long'}).format(now);
    if (dateLock) dateLock.textContent = `${weekday}, ${month} ${day}`;
    if (dateAod) dateAod.textContent = `${shortWeek}, ${shortMonth} ${day}`;
    if (statusTime) statusTime.textContent = `${hh}:${mm}`;
    document.getElementById('copyright-year').textContent = String(year);
  }

  function render() {
    const p = clamp(progress);
    const move = smooth(p);
    const bg = smooth((p-.14)/.60);
    const fill = smooth((p-.20)/.45);
    const outline = 1-smooth((p-.18)/.48);
    const trail = Math.sin(p*Math.PI);
    wallpaper.style.opacity = String(bg);
    wallpaperVignette.style.opacity = String(mix(.1,.6,bg));
    if (dateLock) dateLock.style.opacity = String(bg);
    if (status) status.style.opacity = String(bg);
    if (lockBottom) lockBottom.style.opacity = String(smooth((p-.5)/.5));
    if (aodFooter) aodFooter.style.opacity = String(1-smooth((p-.07)/.48));
    digits.forEach((el,i) => {
      const a=from[i],b=to[i];
      el.style.left = `${mix(a.x,b.x,move)}%`;
      el.style.top = `${mix(a.y,b.y,move)}%`;
      const scale = mix(.30,i>=2?1.04:1,move)*(1 + trail*.045);
      el.style.transform = `translate(-50%,-50%) rotate(${mix(a.angle,b.angle,move)}deg) scale(${scale})`;
      const face = el.querySelector('.digit-fill');
      const glow = el.querySelector('.digit-glow');
      const line = el.querySelector('.digit-outline');
      const bl = (i>=2 ? 6 : 3.2)+trail*(i>=2?3.4:2);
      if(face) {face.style.opacity=String(fill); face.style.filter=`blur(${bl.toFixed(1)}px)`;}
      if(glow) {glow.style.opacity=String(fill*.80); glow.style.filter=`blur(${(bl+6+trail*4).toFixed(1)}px)`;}
      if(line) line.style.opacity=String(outline);
    });
    dots.forEach((el,i) => {
      el.style.left=`${mix(dotFrom[i].x,dotTo[i].x,move)}%`;
      el.style.top=`${mix(dotFrom[i].y,dotTo[i].y,move)}%`;
      el.style.opacity=String(mix(.52,1,smooth(p)));
      el.style.transform=`translate(-50%,-50%) scale(${mix(.22,1,move)})`;
    });
  }

  function setButtons(target) {
    wakeBtn.classList.toggle('is-active',target===1);
    sleepBtn.classList.toggle('is-active',target===0);
    wakeBtn.setAttribute('aria-pressed',String(target===1));
    sleepBtn.setAttribute('aria-pressed',String(target===0));
  }

  function animate(target, {immediate=false}={}) {
    target=target?1:0;
    animationSerial++;
    const thisSerial=animationSerial;
    if (animationFrame!==null) {cancelAnimationFrame(animationFrame);animationFrame=null;}
    setButtons(target);
    if (immediate || reduceMotion.matches) {
      progress=target;phone.dataset.state=target?'lock':'aod';render();return;
    }
    const start=progress;
    const duration=target===1?1700:980;
    const startTime=performance.now();
    phone.dataset.state=target?'waking':'sleeping';
    function step(now) {
      if (animationSerial!==thisSerial) return;
      const t=clamp((now-startTime)/duration);
      // A small outline hold at the start of wake, as in the source animation.
      const local = target===1 ? ease((t-.10)/.90) : smooth(t);
      progress=mix(start,target,local);
      render();
      if(t<1){animationFrame=requestAnimationFrame(step);}else{
        progress=target;phone.dataset.state=target?'lock':'aod';render();animationFrame=null;
      }
    }
    animationFrame=requestAnimationFrame(step);
  }

  wakeBtn.addEventListener('click',()=>{hasInteracted=true;animate(1);});
  sleepBtn.addEventListener('click',()=>{hasInteracted=true;animate(0);});
  replayBtn.addEventListener('click',()=>{hasInteracted=true;animate(0,{immediate:true});requestAnimationFrame(()=>animate(1));});
  refreshTime();
  setInterval(refreshTime,1000);
  animate(0,{immediate:true});
  // Introduce the wallpaper animation once the page loads and the viewer can see it.
  if ('IntersectionObserver' in window && !reduceMotion.matches) {
    let played=false;
    const observer=new IntersectionObserver((entries)=>{
      if(!played&&!hasInteracted&&entries.some(e=>e.isIntersecting)) {played=true;observer.disconnect();setTimeout(()=>animate(1),400);}
    },{threshold:.35});
    observer.observe(phone);
  } else animate(1,{immediate:true});
})();
