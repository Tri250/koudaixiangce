/* ==================== RapidRAW App ==================== */

(function() {
  'use strict';

  // ==================== DATA ====================

  const PHOTOS = [
    { id: 1, prompt: 'golden hour mountain landscape photography', raw: true, stars: 5, folder: 'dcim' },
    { id: 2, prompt: 'professional portrait photography studio lighting', raw: false, stars: 4, folder: 'dcim' },
    { id: 3, prompt: 'city skyline at sunset dramatic clouds', raw: true, stars: 3, folder: 'dcim' },
    { id: 4, prompt: 'autumn forest path warm sunlight', raw: false, stars: 5, folder: 'dcim' },
    { id: 5, prompt: 'ocean waves crashing on rocky shore', raw: false, stars: 4, folder: 'downloads' },
    { id: 6, prompt: 'cherry blossom tree spring photography', raw: true, stars: 5, folder: 'dcim' },
    { id: 7, prompt: 'street photography neon lights rain', raw: false, stars: 3, folder: 'downloads' },
    { id: 8, prompt: 'wildlife eagle soaring mountain sky', raw: true, stars: 4, folder: 'dcim' },
    { id: 9, prompt: 'architecture modern glass building reflections', raw: false, stars: 3, folder: 'screenshots' },
    { id: 10, prompt: 'desert sand dunes golden light', raw: false, stars: 4, folder: 'dcim' },
    { id: 11, prompt: 'macro flower dew drops morning', raw: true, stars: 5, folder: 'dcim' },
    { id: 12, prompt: 'night sky milky way astrophotography', raw: true, stars: 5, folder: 'downloads' },
    { id: 13, prompt: 'vintage car classic automobile', raw: false, stars: 2, folder: 'screenshots' },
    { id: 14, prompt: 'underwater coral reef tropical fish', raw: false, stars: 4, folder: 'dcim' },
    { id: 15, prompt: 'coffee latte art cafe morning', raw: false, stars: 3, folder: 'screenshots' },
    { id: 16, prompt: 'snow covered alpine village winter', raw: true, stars: 4, folder: 'dcim' },
    { id: 17, prompt: 'food photography gourmet plating', raw: false, stars: 3, folder: 'downloads' },
    { id: 18, prompt: 'foggy morning lake reflection calm', raw: false, stars: 5, folder: 'dcim' },
  ];

  const FILTER_PRESETS = {
    natural: { brightness: 0, contrast: 0, saturate: 100, 'hue-rotate': 0, sepia: 0, grayscale: 0 },
    vibrant: { brightness: 5, contrast: 10, saturate: 140, 'hue-rotate': 0, sepia: 0, grayscale: 0 },
    classic: { brightness: 2, contrast: 5, saturate: 85, 'hue-rotate': -5, sepia: 15, grayscale: 0 },
    portrait: { brightness: 5, contrast: -3, saturate: 95, 'hue-rotate': 3, sepia: 8, grayscale: 0 },
    landscape: { brightness: 0, contrast: 15, saturate: 115, 'hue-rotate': 0, sepia: 0, grayscale: 0 },
    monochrome: { brightness: 3, contrast: 10, saturate: 0, 'hue-rotate': 0, sepia: 0, grayscale: 100 },
  };

  const SLIDER_RANGES = {
    exposure: { min: -100, max: 100, css: 'brightness', cssUnit: '%', cssBase: 100, cssScale: 0.5 },
    brightness: { min: -100, max: 100, css: 'brightness', cssUnit: '%', cssBase: 100, cssScale: 0.4 },
    contrast: { min: -100, max: 100, css: 'contrast', cssUnit: '%', cssBase: 100, cssScale: 0.5 },
    highlights: { min: -100, max: 100, css: null },
    shadows: { min: -100, max: 100, css: null },
    whites: { min: -100, max: 100, css: null },
    blacks: { min: -100, max: 100, css: null },
    temperature: { min: -100, max: 100, css: 'sepia', cssUnit: '%', cssBase: 0, cssScale: 0.3 },
    tint: { min: -100, max: 100, css: 'hue-rotate', cssUnit: 'deg', cssBase: 0, cssScale: 0.5 },
    saturation: { min: -100, max: 100, css: 'saturate', cssUnit: '%', cssBase: 100, cssScale: 1.0 },
    vibrance: { min: -100, max: 100, css: 'saturate', cssUnit: '%', cssBase: 100, cssScale: 0.5 },
    vignette: { min: 0, max: 100, css: null },
    grain: { min: 0, max: 100, css: null },
    glow: { min: 0, max: 100, css: null },
    sharpness: { min: 0, max: 100, css: null },
    'detail-amount': { min: 0, max: 100, css: null },
    'noise-reduction': { min: 0, max: 100, css: null },
    'color-noise': { min: 0, max: 100, css: null },
    'hsl-hue': { min: -100, max: 100, css: 'hue-rotate', cssUnit: 'deg', cssBase: 0, cssScale: 0.3 },
    'hsl-sat': { min: -100, max: 100, css: null },
    'hsl-lum': { min: -100, max: 100, css: null },
    rotation: { min: -180, max: 180, css: null },
  };

  // ==================== STATE ====================

  const state = {
    currentView: 'library',
    currentFolder: 'all',
    currentImage: null,
    activeFilter: 'natural',
    activeTab: 'basic',
    sliderValues: {},
    undoStack: [],
    redoStack: [],
  };

  // ==================== HELPERS ====================

  function $(sel) { return document.querySelector(sel); }
  function $$(sel) { return document.querySelectorAll(sel); }

  function getImageUrl(prompt) {
    return `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(prompt)}&image_size=landscape_4_3`;
  }

  function showToast(msg) {
    let toast = $('#app .toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.className = 'toast';
      $('#app').appendChild(toast);
    }
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 2000);
  }

  // ==================== LIBRARY VIEW ====================

  function renderLibrary() {
    const grid = $('#image-grid');
    const photos = state.currentFolder === 'all'
      ? PHOTOS
      : PHOTOS.filter(p => p.folder === state.currentFolder);

    grid.innerHTML = '';
    photos.forEach((photo, i) => {
      const item = document.createElement('div');
      item.className = 'grid-item';
      item.style.animationDelay = `${i * 0.03}s`;
      item.dataset.id = photo.id;

      const img = document.createElement('img');
      img.loading = 'lazy';
      img.src = getImageUrl(photo.prompt);
      img.alt = photo.prompt;
      img.onerror = function() {
        this.src = `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect fill="#2E2E2E" width="200" height="200"/><text x="100" y="105" text-anchor="middle" fill="#666" font-size="13" font-family="sans-serif">IMG_${photo.id}</text></svg>`)}`;
      };

      item.appendChild(img);

      if (photo.raw) {
        const badge = document.createElement('div');
        badge.className = 'raw-badge';
        badge.textContent = 'RAW';
        item.appendChild(badge);
      }

      if (photo.stars > 0) {
        const rating = document.createElement('div');
        rating.className = 'star-rating';
        for (let s = 0; s < 5; s++) {
          const star = document.createElement('span');
          star.className = `star ${s < photo.stars ? 'filled' : 'empty'}`;
          star.innerHTML = '&#9733;';
          rating.appendChild(star);
        }
        item.appendChild(rating);
      }

      item.addEventListener('click', () => openEditor(photo));
      grid.appendChild(item);
    });
  }

  function initFolderChips() {
    $$('.chip').forEach(chip => {
      chip.addEventListener('click', () => {
        $$('.chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        state.currentFolder = chip.dataset.folder;
        renderLibrary();
      });
    });
  }

  function initBottomNav() {
    $$('.nav-item').forEach(item => {
      item.addEventListener('click', () => {
        $$('.nav-item').forEach(n => n.classList.remove('active'));
        item.classList.add('active');
      });
    });
  }

  // ==================== EDITOR VIEW ====================

  function openEditor(photo) {
    state.currentImage = photo;
    state.sliderValues = {};
    state.activeFilter = 'natural';

    const preview = $('#preview-image');
    preview.src = getImageUrl(photo.prompt);
    preview.onerror = function() {
      this.src = `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect fill="#2E2E2E" width="400" height="300"/><text x="200" y="155" text-anchor="middle" fill="#999" font-size="16" font-family="sans-serif">IMG_${photo.id}</text></svg>`)}`;
    };

    renderFilmstrip();
    resetAllSliders();
    applyImageFilter();

    switchView('editor');
    animateHistogram();
  }

  function closeEditor() {
    state.currentImage = null;
    switchView('library');
  }

  function switchView(view) {
    const prev = $(`.view.active`);
    const next = $(`#${view}-view`);
    if (prev === next) return;

    prev.classList.remove('active');
    prev.classList.add('exit-left');

    next.classList.add('active');

    setTimeout(() => {
      prev.classList.remove('exit-left');
    }, 400);

    state.currentView = view;
  }

  function renderFilmstrip() {
    const strip = $('#filmstrip');
    strip.innerHTML = '';
    PHOTOS.forEach(photo => {
      const item = document.createElement('div');
      item.className = `filmstrip-item${state.currentImage && photo.id === state.currentImage.id ? ' active' : ''}`;
      const img = document.createElement('img');
      img.loading = 'lazy';
      img.src = getImageUrl(photo.prompt);
      img.alt = '';
      img.onerror = function() {
        this.src = `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44"><rect fill="#2E2E2E" width="44" height="44"/></svg>`)}`;
      };
      item.appendChild(img);
      item.addEventListener('click', () => openEditor(photo));
      strip.appendChild(item);
    });
  }

  // ==================== TABS ====================

  function initTabs() {
    $$('.tab').forEach(tab => {
      tab.addEventListener('click', () => {
        $$('.tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        $$('.panel-content').forEach(p => p.classList.remove('active'));
        const panel = $(`.panel-content[data-panel="${tab.dataset.tab}"]`);
        if (panel) panel.classList.add('active');
        state.activeTab = tab.dataset.tab;

        if (tab.dataset.tab === 'curve') {
          drawCurve();
        }
      });
    });
  }

  // ==================== SLIDERS ====================

  function initSliders() {
    $$('.slider-row').forEach(row => {
      const param = row.dataset.param;
      const trackContainer = row.querySelector('.slider-track-container');
      const track = row.querySelector('.slider-track');
      const fill = row.querySelector('.slider-fill');
      const thumb = row.querySelector('.slider-thumb');
      const valueEl = row.querySelector('.slider-value');
      const range = SLIDER_RANGES[param] || { min: -100, max: 100 };

      let isDragging = false;

      function setSliderValue(pct) {
        pct = Math.max(0, Math.min(1, pct));
        const val = Math.round(range.min + pct * (range.max - range.min));
        state.sliderValues[param] = val;
        updateSliderUI(row, pct, val);
        applyImageFilter();
      }

      function updateSliderUI(row, pct, val) {
        const fill = row.querySelector('.slider-fill');
        const thumb = row.querySelector('.slider-thumb');
        const valueEl = row.querySelector('.slider-value');
        const range = SLIDER_RANGES[row.dataset.param] || { min: -100, max: 100 };
        const hasCenter = range.min < 0;

        if (hasCenter) {
          const center = 0.5;
          if (pct >= center) {
            fill.style.left = (center * 100) + '%';
            fill.style.width = ((pct - center) * 100) + '%';
          } else {
            fill.style.left = (pct * 100) + '%';
            fill.style.width = ((center - pct) * 100) + '%';
          }
        } else {
          fill.style.left = '0';
          fill.style.width = (pct * 100) + '%';
        }

        thumb.style.left = (pct * 100) + '%';
        valueEl.textContent = val > 0 ? `+${val}` : val;

        if (val !== 0) {
          valueEl.classList.add('non-zero');
        } else {
          valueEl.classList.remove('non-zero');
        }
      }

      function handlePointer(e) {
        const rect = track.getBoundingClientRect();
        const clientX = e.touches ? e.touches[0].clientX : e.clientX;
        const pct = (clientX - rect.left) / rect.width;
        setSliderValue(pct);
      }

      // Pointer events
      trackContainer.addEventListener('mousedown', (e) => {
        isDragging = true;
        thumb.classList.add('dragging');
        handlePointer(e);
        e.preventDefault();
      });

      trackContainer.addEventListener('touchstart', (e) => {
        isDragging = true;
        thumb.classList.add('dragging');
        handlePointer(e);
      }, { passive: true });

      document.addEventListener('mousemove', (e) => {
        if (isDragging) {
          handlePointer(e);
          e.preventDefault();
        }
      });

      document.addEventListener('touchmove', (e) => {
        if (isDragging) {
          handlePointer(e);
        }
      }, { passive: true });

      document.addEventListener('mouseup', () => {
        if (isDragging) {
          isDragging = false;
          thumb.classList.remove('dragging');
        }
      });

      document.addEventListener('touchend', () => {
        if (isDragging) {
          isDragging = false;
          thumb.classList.remove('dragging');
        }
      });

      // Double click to reset
      trackContainer.addEventListener('dblclick', () => {
        const hasCenter = range.min < 0;
        const resetPct = hasCenter ? 0.5 : 0;
        setSliderValue(resetPct);
        showToast('已重置');
      });

      // Initialize
      const hasCenter = range.min < 0;
      const initPct = hasCenter ? 0.5 : 0;
      updateSliderUI(row, initPct, 0);
    });
  }

  function resetAllSliders() {
    $$('.slider-row').forEach(row => {
      const param = row.dataset.param;
      const range = SLIDER_RANGES[param] || { min: -100, max: 100 };
      const hasCenter = range.min < 0;
      const pct = hasCenter ? 0.5 : 0;
      const fill = row.querySelector('.slider-fill');
      const thumb = row.querySelector('.slider-thumb');
      const valueEl = row.querySelector('.slider-value');

      state.sliderValues[param] = 0;

      if (hasCenter) {
        fill.style.left = '50%';
        fill.style.width = '0%';
      } else {
        fill.style.left = '0';
        fill.style.width = '0%';
      }

      thumb.style.left = (pct * 100) + '%';
      valueEl.textContent = '0';
      valueEl.classList.remove('non-zero');
    });
  }

  // ==================== IMAGE FILTER APPLICATION ====================

  function applyImageFilter() {
    const img = $('#preview-image');
    if (!img) return;

    const filters = [];
    const vals = state.sliderValues;

    // Exposure & Brightness → CSS brightness
    const exposure = vals.exposure || 0;
    const brightness = vals.brightness || 0;
    const totalBrightness = 1 + (exposure * 0.005) + (brightness * 0.004);
    filters.push(`brightness(${totalBrightness})`);

    // Contrast
    const contrast = vals.contrast || 0;
    filters.push(`contrast(${1 + contrast * 0.005})`);

    // Saturation & Vibrance
    const saturation = vals.saturation || 0;
    const vibrance = vals.vibrance || 0;
    const totalSaturate = 1 + (saturation * 0.01) + (vibrance * 0.005);
    filters.push(`saturate(${totalSaturate})`);

    // Temperature → sepia
    const temperature = vals.temperature || 0;
    if (temperature > 0) {
      filters.push(`sepia(${temperature * 0.003})`);
    }

    // Tint → hue-rotate
    const tint = vals.tint || 0;
    if (tint !== 0) {
      filters.push(`hue-rotate(${tint * 0.5}deg)`);
    }

    // Apply filter preset
    const preset = FILTER_PRESETS[state.activeFilter];
    if (preset && state.activeFilter !== 'natural') {
      // Combine with slider values
      if (preset.sepia) {
        const existingSepia = temperature > 0 ? temperature * 0.003 : 0;
        // Preset already accounted for; keep it additive
      }
      filters.push(`sepia(${(preset.sepia || 0) / 100})`);
      if (preset['hue-rotate']) {
        filters.push(`hue-rotate(${preset['hue-rotate']}deg)`);
      }
    }

    // Glow → blur overlay simulation
    const glow = vals.glow || 0;
    if (glow > 0) {
      // Slight brightness boost simulates glow
      filters.push(`brightness(${1 + glow * 0.002})`);
    }

    img.style.filter = filters.join(' ');

    // Vignette overlay
    updateVignette(vals.vignette || 0);
  }

  function updateVignette(amount) {
    let overlay = $('#vignette-overlay');
    if (amount <= 0) {
      if (overlay) overlay.style.opacity = '0';
      return;
    }
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.id = 'vignette-overlay';
      overlay.style.cssText = `
        position: absolute; inset: 0; pointer-events: none;
        background: radial-gradient(ellipse at center, transparent 30%, rgba(0,0,0,0.8) 100%);
        transition: opacity 0.3s;
      `;
      $('#preview-area').appendChild(overlay);
    }
    overlay.style.opacity = (amount / 100).toString();
  }

  // ==================== FILTER CARDS ====================

  function initFilterCards() {
    $$('.filter-card').forEach(card => {
      card.addEventListener('click', () => {
        $$('.filter-card').forEach(c => c.classList.remove('active'));
        card.classList.add('active');
        state.activeFilter = card.dataset.filter;
        applyImageFilter();
        showToast(`已应用: ${card.querySelector('.filter-name').textContent}`);
      });
    });
  }

  // ==================== HSL CHIPS ====================

  function initHslChips() {
    $$('.hsl-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        $$('.hsl-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
      });
    });
  }

  // ==================== CURVE CANVAS ====================

  function drawCurve() {
    const canvas = $('#curve-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width;
    const h = canvas.height;

    ctx.clearRect(0, 0, w, h);

    // Grid
    ctx.strokeStyle = '#3A3A3A';
    ctx.lineWidth = 0.5;
    for (let i = 0; i <= 4; i++) {
      const x = (w / 4) * i;
      const y = (h / 4) * i;
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }

    // Diagonal (linear)
    ctx.strokeStyle = '#666';
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(0, h);
    ctx.lineTo(w, 0);
    ctx.stroke();
    ctx.setLineDash([]);

    // Curve
    const activeChannel = $('.curve-chip.active')?.dataset.channel || 'rgb';
    const colors = { rgb: '#F0F0F0', r: '#FF5555', g: '#55FF55', b: '#5599FF' };
    ctx.strokeStyle = colors[activeChannel] || '#F0F0F0';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, h);

    // S-curve
    const points = [];
    for (let x = 0; x <= w; x++) {
      const t = x / w;
      // S-curve: gentle
      const y = h - (h * (3 * t * t - 2 * t * t * t));
      points.push({ x, y });
    }

    ctx.moveTo(0, h);
    for (const pt of points) {
      ctx.lineTo(pt.x, pt.y);
    }
    ctx.stroke();

    // Control points
    ctx.fillStyle = colors[activeChannel] || '#F0F0F0';
    const cp1 = { x: w * 0.25, y: h * 0.75 };
    const cp2 = { x: w * 0.75, y: h * 0.25 };
    [cp1, cp2].forEach(cp => {
      ctx.beginPath();
      ctx.arc(cp.x, cp.y, 4, 0, Math.PI * 2);
      ctx.fill();
    });
  }

  function initCurveChips() {
    $$('.curve-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        $$('.curve-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        drawCurve();
      });
    });
  }

  // ==================== GEOMETRY ====================

  function initGeoButtons() {
    $$('.geo-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        $$('.geo-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  // ==================== HISTOGRAM ====================

  function animateHistogram() {
    const path = $('#hist-path');
    if (!path) return;

    function generateHistogram() {
      const points = [];
      const numBars = 60;
      let maxVal = 0;
      const values = [];

      for (let i = 0; i < numBars; i++) {
        const x = i / numBars;
        // Bell curve-ish distribution with some randomness
        const center1 = 0.35;
        const center2 = 0.65;
        const val = Math.exp(-((x - center1) ** 2) / 0.05) * 0.8
                  + Math.exp(-((x - center2) ** 2) / 0.08) * 0.6
                  + Math.random() * 0.2
                  + 0.1;
        values.push(val);
        if (val > maxVal) maxVal = val;
      }

      // Build SVG path
      const width = 120;
      const height = 60;
      let d = `M 0 ${height}`;

      for (let i = 0; i < numBars; i++) {
        const x = (i / numBars) * width;
        const barH = (values[i] / maxVal) * height * 0.9;
        const y = height - barH;
        d += ` L ${x} ${y}`;
      }

      d += ` L ${width} ${height} Z`;
      return d;
    }

    // Animate
    let frame = 0;
    function animate() {
      if (state.currentView !== 'editor') return;
      frame++;
      if (frame % 120 === 0) { // Update every ~2s
        path.setAttribute('d', generateHistogram());
      }
      requestAnimationFrame(animate);
    }
    path.setAttribute('d', generateHistogram());
    animate();
  }

  // ==================== BOTTOM SHEETS ====================

  function openSheet(sheetId) {
    const sheet = $(`#${sheetId}`);
    const overlay = $(`#${sheetId}-overlay`);
    if (sheet) sheet.classList.add('visible');
    if (overlay) overlay.classList.add('visible');
  }

  function closeSheet(sheetId) {
    const sheet = $(`#${sheetId}`);
    const overlay = $(`#${sheetId}-overlay`);
    if (sheet) sheet.classList.remove('visible');
    if (overlay) overlay.classList.remove('visible');
  }

  function initSheets() {
    // Filter sheet
    $('#fab-filters').addEventListener('click', () => openSheet('filter-sheet'));
    $('#close-filter-sheet').addEventListener('click', () => closeSheet('filter-sheet'));
    $('#filter-sheet-overlay').addEventListener('click', () => closeSheet('filter-sheet'));

    // Export sheet
    $('#btn-more').addEventListener('click', () => openSheet('export-sheet'));
    $('#close-export-sheet').addEventListener('click', () => closeSheet('export-sheet'));
    $('#export-sheet-overlay').addEventListener('click', () => closeSheet('export-sheet'));
  }

  // ==================== EXPORT OPTIONS ====================

  function initExportOptions() {
    // Format radio
    $$('.format-option').forEach(opt => {
      opt.addEventListener('click', () => {
        $$('.format-option').forEach(o => o.classList.remove('active'));
        opt.classList.add('active');
        const qualitySection = $('#quality-section');
        if (opt.querySelector('input').value === 'jpeg') {
          qualitySection.style.display = '';
        } else {
          qualitySection.style.display = 'none';
        }
      });
    });

    // Resize buttons
    $$('.resize-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const parent = btn.parentElement;
        parent.querySelectorAll('.resize-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });

    // Quality slider
    initExportSlider();

    // Export button
    $('#btn-export').addEventListener('click', () => {
      closeSheet('export-sheet');
      showToast('正在导出...');
    });
  }

  function initExportSlider() {
    const track = $('.export-slider-track');
    const fill = track.querySelector('.export-slider-fill');
    const thumb = track.querySelector('.export-slider-thumb');
    const valEl = $('#quality-val');
    let isDragging = false;

    function setQuality(pct) {
      pct = Math.max(0, Math.min(1, pct));
      const val = Math.round(50 + pct * 50); // 50-100
      fill.style.width = (pct * 100) + '%';
      thumb.style.left = (pct * 100) + '%';
      valEl.textContent = val;
    }

    function handlePointer(e) {
      const rect = track.getBoundingClientRect();
      const clientX = e.touches ? e.touches[0].clientX : e.clientX;
      const pct = (clientX - rect.left) / rect.width;
      setQuality(pct);
    }

    track.addEventListener('mousedown', (e) => {
      isDragging = true;
      handlePointer(e);
      e.preventDefault();
    });

    track.addEventListener('touchstart', (e) => {
      isDragging = true;
      handlePointer(e);
    }, { passive: true });

    document.addEventListener('mousemove', (e) => {
      if (isDragging) handlePointer(e);
    });

    document.addEventListener('touchmove', (e) => {
      if (isDragging) handlePointer(e);
    });

    document.addEventListener('mouseup', () => isDragging = false);
    document.addEventListener('touchend', () => isDragging = false);
  }

  // ==================== EDITOR ACTIONS ====================

  function initEditorActions() {
    // Back button
    $('#btn-back').addEventListener('click', closeEditor);

    // Undo/Redo
    $('#btn-undo').addEventListener('click', () => showToast('撤销'));
    $('#btn-redo').addEventListener('click', () => showToast('重做'));

    // EXIF
    $('#btn-exif').addEventListener('click', () => showToast('EXIF 信息'));

    // Crop
    $('#btn-crop').addEventListener('click', () => showToast('裁剪模式'));
  }

  // ==================== CURVE CANVAS INTERACTION ====================

  function initCurveInteraction() {
    const canvas = $('#curve-canvas');
    if (!canvas) return;

    let isDragging = false;

    canvas.addEventListener('mousedown', (e) => {
      isDragging = true;
      drawCurveAtPoint(e);
    });

    canvas.addEventListener('mousemove', (e) => {
      if (isDragging) drawCurveAtPoint(e);
    });

    canvas.addEventListener('mouseup', () => isDragging = false);
    canvas.addEventListener('mouseleave', () => isDragging = false);

    function drawCurveAtPoint(e) {
      // Simplified visual feedback
      drawCurve();
    }
  }

  // ==================== INITIALIZATION ====================

  function init() {
    renderLibrary();
    initFolderChips();
    initBottomNav();
    initTabs();
    initSliders();
    initHslChips();
    initCurveChips();
    initCurveInteraction();
    initGeoButtons();
    initSheets();
    initExportOptions();
    initEditorActions();
    initFilterCards();

    // Hide zoom hint after animation
    setTimeout(() => {
      const hint = $('#zoom-hint');
      if (hint) hint.style.display = 'none';
    }, 3500);
  }

  // Wait for DOM
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
