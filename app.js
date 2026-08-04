/* Blinkzähler-Reader — Kamera-basierte Impulserfassung für Zähler-Blink-LED */
(() => {
  'use strict';

  // ---------- State ----------
  const state = {
    running: false,
    ir: false,
    torch: false,
    sensitivity: 0.5,      // 0..1 -> Hysterese-Fensterbreite
    refractoryMs: 40,
    rateImpPerKWh: 10000,
    vtP: 1, vtS: 1,
    ctP: 1, ctS: 1,
    pulses: [],             // timestamps (ms)
    powerHistory: [],       // {t, pKW} primärseitig, für Chart
    energyKWh: 0,
    lastPulseTime: 0,
    edgeState: 'low',       // hysterese state machine
    buffer: [],             // {t, v} raw brightness ring buffer (last ~2.5s)
  };

  const els = {};
  ['video','overlay','roiBox','camHint','camStart','startBtn','statusDot','pulseCount',
   'powerPrimary','powerRaw','freqVal','energyVal','chart','scope',
   'sensSlider','sensVal','refracSlider','refracVal','irToggle','torchToggle',
   'rateChips','rateCustom','vtChips','vtP','vtS','vtFactor','ctChips','ctP','ctS','ctFactor',
   'resetBtn','tapBtn','chartRange'
  ].forEach(id => els[id] = document.getElementById(id));

  // ---------- Persistence ----------
  const SKEY = 'blinkzaehler_settings_v1';
  function saveSettings(){
    try{
      localStorage.setItem(SKEY, JSON.stringify({
        rateImpPerKWh: state.rateImpPerKWh,
        vtP: state.vtP, vtS: state.vtS, ctP: state.ctP, ctS: state.ctS,
        sensitivity: state.sensitivity, refractoryMs: state.refractoryMs, ir: state.ir
      }));
    }catch(e){}
  }
  function loadSettings(){
    try{
      const raw = localStorage.getItem(SKEY);
      if(!raw) return;
      const s = JSON.parse(raw);
      Object.assign(state, s);
    }catch(e){}
  }

  // ---------- UI wiring: chips ----------
  function wireChips(container, onPick){
    container.addEventListener('click', (e) => {
      const chip = e.target.closest('.chip');
      if(!chip) return;
      [...container.children].forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      onPick(chip);
    });
  }

  wireChips(els.rateChips, (chip) => {
    state.rateImpPerKWh = parseFloat(chip.dataset.rate);
    els.rateCustom.value = '';
    saveSettings();
  });
  els.rateCustom.addEventListener('input', () => {
    const v = parseFloat(els.rateCustom.value);
    if(v > 0){
      state.rateImpPerKWh = v;
      [...els.rateChips.children].forEach(c => c.classList.remove('active'));
      saveSettings();
    }
  });

  wireChips(els.vtChips, (chip) => {
    state.vtP = parseFloat(chip.dataset.vp);
    state.vtS = parseFloat(chip.dataset.vs);
    els.vtP.value = state.vtP; els.vtS.value = state.vtS;
    updateVtFactor(); saveSettings();
  });
  els.vtP.addEventListener('input', () => { state.vtP = parseFloat(els.vtP.value)||0; deactivateChips(els.vtChips); updateVtFactor(); saveSettings(); });
  els.vtS.addEventListener('input', () => { state.vtS = parseFloat(els.vtS.value)||0; deactivateChips(els.vtChips); updateVtFactor(); saveSettings(); });

  wireChips(els.ctChips, (chip) => {
    state.ctP = parseFloat(chip.dataset.cp);
    state.ctS = parseFloat(chip.dataset.cs);
    els.ctP.value = state.ctP; els.ctS.value = state.ctS;
    updateCtFactor(); saveSettings();
  });
  els.ctP.addEventListener('input', () => { state.ctP = parseFloat(els.ctP.value)||0; deactivateChips(els.ctChips); updateCtFactor(); saveSettings(); });
  els.ctS.addEventListener('input', () => { state.ctS = parseFloat(els.ctS.value)||0; deactivateChips(els.ctChips); updateCtFactor(); saveSettings(); });

  function deactivateChips(container){ [...container.children].forEach(c => c.classList.remove('active')); }
  function updateVtFactor(){ els.vtFactor.textContent = (state.vtS>0 ? (state.vtP/state.vtS) : 0).toFixed(3); }
  function updateCtFactor(){ els.ctFactor.textContent = (state.ctS>0 ? (state.ctP/state.ctS) : 0).toFixed(3); }

  els.sensSlider.addEventListener('input', () => {
    state.sensitivity = els.sensSlider.value/100;
    els.sensVal.textContent = els.sensSlider.value + '%';
    saveSettings();
  });
  els.refracSlider.addEventListener('input', () => {
    state.refractoryMs = parseInt(els.refracSlider.value,10);
    els.refracVal.textContent = state.refractoryMs + ' ms';
    saveSettings();
  });

  els.irToggle.addEventListener('click', () => {
    state.ir = !state.ir;
    els.irToggle.classList.toggle('on', state.ir);
    saveSettings();
  });

  els.torchToggle.addEventListener('click', async () => {
    state.torch = !state.torch;
    els.torchToggle.classList.toggle('on', state.torch);
    try{
      const track = window._camTrack;
      if(track && track.getCapabilities && track.getCapabilities().torch){
        await track.applyConstraints({advanced:[{torch: state.torch}]});
      }
    }catch(e){ /* torch not supported */ }
  });

  els.resetBtn.addEventListener('click', () => {
    state.pulses = [];
    state.powerHistory = [];
    state.energyKWh = 0;
    els.pulseCount.textContent = '0 Pulse';
    els.energyVal.textContent = '0.000';
    els.powerPrimary.textContent = '–';
    els.powerRaw.textContent = 'Zähler-Leistung: – kW · Periode: – ms';
    els.freqVal.textContent = '–';
  });

  els.tapBtn.addEventListener('click', () => registerPulse(performance.now()));

  // ---------- Camera ----------
  let stream, videoTrack, animId, roiRect;

  els.startBtn.addEventListener('click', startCamera);

  async function startCamera(){
    try{
      stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 640 }, height: { ideal: 480 },
          frameRate: { ideal: 30, max: 60 }
        },
        audio: false
      });
      els.video.srcObject = stream;
      await els.video.play();
      videoTrack = stream.getVideoTracks()[0];
      window._camTrack = videoTrack;
      els.camStart.style.display = 'none';
      state.running = true;
      els.statusDot.classList.add('live');
      computeROI();
      requestAnimationFrame(loop);
    }catch(err){
      alert('Kamera konnte nicht gestartet werden: ' + err.message);
    }
  }

  function computeROI(){
    const wrap = document.getElementById('camwrap');
    const box = els.roiBox.getBoundingClientRect();
    const wrapRect = wrap.getBoundingClientRect();
    // relative ROI in video pixel space assuming object-fit:cover center-crop
    roiRect = {
      xRatio: (box.left - wrapRect.left) / wrapRect.width,
      yRatio: (box.top - wrapRect.top) / wrapRect.height,
      wRatio: box.width / wrapRect.width,
      hRatio: box.height / wrapRect.height
    };
  }
  window.addEventListener('resize', computeROI);

  // offscreen canvas for ROI sampling
  const sampleCanvas = document.createElement('canvas');
  sampleCanvas.width = 32; sampleCanvas.height = 32;
  const sampleCtx = sampleCanvas.getContext('2d', { willReadFrequently: true });

  function loop(ts){
    if(!state.running) return;
    if(els.video.readyState >= 2 && roiRect){
      const vw = els.video.videoWidth, vh = els.video.videoHeight;
      if(vw && vh){
        const sx = roiRect.xRatio * vw;
        const sy = roiRect.yRatio * vh;
        const sw = Math.max(4, roiRect.wRatio * vw);
        const sh = Math.max(4, roiRect.hRatio * vh);
        try{
          sampleCtx.drawImage(els.video, sx, sy, sw, sh, 0, 0, sampleCanvas.width, sampleCanvas.height);
          const data = sampleCtx.getImageData(0,0,sampleCanvas.width, sampleCanvas.height).data;
          let sum = 0, n = 0;
          for(let i=0;i<data.length;i+=4){
            const r = data[i], g = data[i+1], b = data[i+2];
            const v = state.ir ? (r*0.7 + g*0.2 + b*0.1) : (0.299*r + 0.587*g + 0.114*b);
            sum += v; n++;
          }
          const avg = sum/n;
          processSample(performance.now(), avg);
        }catch(e){ /* frame not ready */ }
      }
    }
    animId = requestAnimationFrame(loop);
  }

  // ---------- Pulse detection: adaptive hysteresis ----------
  const BUFFER_MS = 2500;

  function processSample(t, v){
    state.buffer.push({t, v});
    while(state.buffer.length && t - state.buffer[0].t > BUFFER_MS) state.buffer.shift();
    if(state.buffer.length < 5) return;

    let min = Infinity, max = -Infinity;
    for(const s of state.buffer){ if(s.v<min) min=s.v; if(s.v>max) max=s.v; }
    const range = Math.max(1, max - min);

    // sensitivity 0..1 controls hysteresis band width around midpoint
    const half = 0.5 - (state.sensitivity * 0.4); // higher sensitivity -> narrower band -> more sensitive
    const upper = min + (0.5 + half) * range;
    const lower = min + (0.5 - half) * range;

    if(state.edgeState === 'low' && v > upper){
      if(t - state.lastPulseTime > state.refractoryMs){
        registerPulse(t);
        state.lastPulseTime = t;
      }
      state.edgeState = 'high';
    } else if(state.edgeState === 'high' && v < lower){
      state.edgeState = 'low';
    }

    drawScope(min, max, upper, lower);
  }

  function registerPulse(t){
    state.pulses.push(t);
    while(state.pulses.length > 200) state.pulses.shift();
    els.pulseCount.textContent = state.pulses.length + ' Pulse';

    if(state.pulses.length >= 2){
      const periodMs = t - state.pulses[state.pulses.length-2];
      updatePowerFromPeriod(periodMs, t);
    }
    flashRoi();
  }

  function flashRoi(){
    els.roiBox.style.borderColor = 'var(--green)';
    setTimeout(() => { els.roiBox.style.borderColor = 'var(--amber)'; }, 90);
  }

  function updatePowerFromPeriod(periodMs, t){
    const periodS = periodMs / 1000;
    if(periodS <= 0) return;
    const rate = state.rateImpPerKWh;
    const pMeterKW = 3600 / (rate * periodS);
    const vtFactor = state.vtS>0 ? state.vtP/state.vtS : 1;
    const ctFactor = state.ctS>0 ? state.ctP/state.ctS : 1;
    const pPrimaryKW = pMeterKW * vtFactor * ctFactor;
    const freqHz = 1/periodS;

    // energy accumulation: 1 pulse = (1/rate) kWh at meter side -> * VT*CT for primary
    state.energyKWh += (1/rate) * vtFactor * ctFactor;

    els.powerPrimary.textContent = formatPower(pPrimaryKW);
    els.powerRaw.textContent = `Zähler-Leistung: ${pMeterKW.toFixed(3)} kW · Periode: ${periodMs.toFixed(0)} ms`;
    els.freqVal.textContent = freqHz.toFixed(2);
    els.energyVal.textContent = state.energyKWh.toFixed(3);

    state.powerHistory.push({t, p: pPrimaryKW});
    while(state.powerHistory.length > 60) state.powerHistory.shift();
    drawChart();
  }

  function formatPower(pKW){
    if(pKW >= 1000) return (pKW/1000).toFixed(3) + ' MW';
    return pKW.toFixed(3);
  }

  // ---------- Chart (rolling power) ----------
  const chartCtx = els.chart.getContext('2d');
  function drawChart(){
    const w = els.chart.width, h = els.chart.height;
    chartCtx.clearRect(0,0,w,h);
    const pts = state.powerHistory;
    if(pts.length < 2){
      chartCtx.fillStyle = '#8ea0b8';
      chartCtx.font = '12px monospace';
      chartCtx.fillText('Warte auf Pulse …', 12, h/2);
      return;
    }
    const vals = pts.map(p=>p.p);
    let min = Math.min(...vals), max = Math.max(...vals);
    if(min === max){ min -= 1; max += 1; }
    const pad = (max-min)*0.1;
    min -= pad; max += pad;

    chartCtx.strokeStyle = '#243044';
    chartCtx.lineWidth = 1;
    for(let i=0;i<=4;i++){
      const y = (h-20) * (i/4) + 10;
      chartCtx.beginPath(); chartCtx.moveTo(0,y); chartCtx.lineTo(w,y); chartCtx.stroke();
    }

    chartCtx.beginPath();
    pts.forEach((p, i) => {
      const x = (i/(pts.length-1)) * (w-20) + 10;
      const y = h - 10 - ((p.p - min)/(max-min)) * (h-20);
      if(i===0) chartCtx.moveTo(x,y); else chartCtx.lineTo(x,y);
    });
    chartCtx.strokeStyle = '#ffb020';
    chartCtx.lineWidth = 2;
    chartCtx.stroke();

    // fill
    chartCtx.lineTo(w-10, h-10);
    chartCtx.lineTo(10, h-10);
    chartCtx.closePath();
    chartCtx.fillStyle = 'rgba(255,176,32,0.08)';
    chartCtx.fill();

    chartCtx.fillStyle = '#8ea0b8';
    chartCtx.font = '10px monospace';
    chartCtx.fillText(max.toFixed(2)+' kW', 12, 14);
    chartCtx.fillText(min.toFixed(2)+' kW', 12, h-14);
  }

  // ---------- Scope (raw brightness debug) ----------
  const scopeCtx = els.scope.getContext('2d');
  function drawScope(min, max, upper, lower){
    const w = els.scope.width, h = els.scope.height;
    scopeCtx.clearRect(0,0,w,h);
    const buf = state.buffer;
    if(buf.length < 2) return;
    const t0 = buf[0].t, t1 = buf[buf.length-1].t;
    const span = Math.max(1, t1-t0);
    const range = Math.max(1, max-min);

    function yFor(v){ return h - ((v-min)/range) * h; }

    scopeCtx.strokeStyle = 'rgba(55,224,138,0.5)';
    scopeCtx.setLineDash([3,3]);
    scopeCtx.beginPath(); scopeCtx.moveTo(0, yFor(upper)); scopeCtx.lineTo(w, yFor(upper)); scopeCtx.stroke();
    scopeCtx.strokeStyle = 'rgba(255,93,93,0.5)';
    scopeCtx.beginPath(); scopeCtx.moveTo(0, yFor(lower)); scopeCtx.lineTo(w, yFor(lower)); scopeCtx.stroke();
    scopeCtx.setLineDash([]);

    scopeCtx.beginPath();
    buf.forEach((s,i) => {
      const x = ((s.t-t0)/span) * w;
      const y = yFor(s.v);
      if(i===0) scopeCtx.moveTo(x,y); else scopeCtx.lineTo(x,y);
    });
    scopeCtx.strokeStyle = '#e7edf5';
    scopeCtx.lineWidth = 1.5;
    scopeCtx.stroke();
  }

  // ---------- Init ----------
  loadSettings();
  els.sensSlider.value = state.sensitivity*100;
  els.sensVal.textContent = Math.round(state.sensitivity*100) + '%';
  els.refracSlider.value = state.refractoryMs;
  els.refracVal.textContent = state.refractoryMs + ' ms';
  els.irToggle.classList.toggle('on', state.ir);
  els.vtP.value = state.vtP; els.vtS.value = state.vtS;
  els.ctP.value = state.ctP; els.ctS.value = state.ctS;
  updateVtFactor(); updateCtFactor();

  // re-activate matching chip if loaded settings match a preset
  [...els.rateChips.children].forEach(c => {
    c.classList.toggle('active', parseFloat(c.dataset.rate) === state.rateImpPerKWh);
  });
  if(![...els.rateChips.children].some(c=>c.classList.contains('active'))){
    els.rateCustom.value = state.rateImpPerKWh;
  }

})();
