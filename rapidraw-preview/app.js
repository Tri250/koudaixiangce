/* ==================== RapidRAW App - Hasselblad Master Film Edition ==================== */

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

  // 9 Hasselblad Master Films
  const FILMS = [
    { id: 'none', name: '无滤镜', nameEn: '', ref: '', category: 'all', char: '无',
      gradient: 'linear-gradient(135deg, #2E2E2E, #3A3A3A)' },
    // 原生经典
    { id: 'hewa', name: '和光', nameEn: 'Natural', ref: 'Kodak Portra 400', category: 'classic', char: '和',
      gradient: 'linear-gradient(135deg, #8B7355, #D2B48C, #87CEEB)',
      css: { brightness: 1.05, contrast: 0.95, saturate: 0.92, sepia: 0.03, 'hue-rotate': 2 } },
    { id: 'nongyu', name: '浓郁', nameEn: 'Vibrant', ref: 'Fujifilm Pro 400H', category: 'classic', char: '浓',
      gradient: 'linear-gradient(135deg, #1A5276, #2ECC71, #1ABC9C)',
      css: { brightness: 1.02, contrast: 1.1, saturate: 1.15, sepia: 0, 'hue-rotate': -2 } },
    { id: 'fugu', name: '复古', nameEn: 'Retro', ref: 'Kodak Gold 200', category: 'classic', char: '复',
      gradient: 'linear-gradient(135deg, #8B6914, #CD853F, #DEB887)',
      css: { brightness: 1.0, contrast: 1.05, saturate: 0.92, sepia: 0.15, 'hue-rotate': -5 } },
    // 情绪表达
    { id: 'qingxin', name: '清新', nameEn: 'Fresh', ref: 'Fujifilm Superia', category: 'emotional', char: '清',
      gradient: 'linear-gradient(135deg, #A8D8EA, #E8F4FD, #F5F5F5)',
      css: { brightness: 1.12, contrast: 0.88, saturate: 0.8, sepia: 0, 'hue-rotate': -1 } },
    { id: 'tongtou', name: '通透', nameEn: 'Clarity', ref: 'Hasselblad HNCS', category: 'emotional', char: '通',
      gradient: 'linear-gradient(135deg, #BDC3C7, #ECF0F1, #FFFFFF)',
      css: { brightness: 1.02, contrast: 1.0, saturate: 1.03, sepia: 0, 'hue-rotate': 0 } },
    // 结构时间
    { id: 'nihong', name: '霓虹', nameEn: 'Neon', ref: 'CineStill 800T', category: 'structural', char: '霓',
      gradient: 'linear-gradient(135deg, #E8600C, #FF6B6B, #4ECDC4)',
      css: { brightness: 1.0, contrast: 1.2, saturate: 1.12, sepia: 0.05, 'hue-rotate': 3 } },
    { id: 'lengdiao', name: '冷调闪光', nameEn: 'Cool Flash', ref: 'Early CCD Digital', category: 'structural', char: '冷',
      gradient: 'linear-gradient(135deg, #2C3E50, #3498DB, #85C1E9)',
      css: { brightness: 0.98, contrast: 1.08, saturate: 0.95, sepia: 0, 'hue-rotate': -8 } },
    { id: 'nuandiao', name: '暖调闪光', nameEn: 'Warm Flash', ref: 'Early CCD Digital (Warm)', category: 'structural', char: '暖',
      gradient: 'linear-gradient(135deg, #D4A574, #E8C39E, #F5DEB3)',
      css: { brightness: 1.02, contrast: 1.05, saturate: 1.03, sepia: 0.1, 'hue-rotate': 5 } },
    { id: 'heibai', name: '反差黑白', nameEn: 'Noir', ref: 'Kodak Tri-X 400', category: 'structural', char: '黑',
      gradient: 'linear-gradient(135deg, #1a1a1a, #555, #aaa)',
      css: { brightness: 1.03, contrast: 1.25, saturate: 0, sepia: 0, 'hue-rotate': 0 } },
  ];

  const SLIDER_RANGES = {
    intensity:   { min: -100, max: 100, css: 'brightness', cssUnit: '%', cssBase: 100, cssScale: 0.5 },
    softlight:   { min: -100, max: 100, css: null },
    tone:        { min: -100, max: 100, css: 'contrast', cssUnit: '%', cssBase: 100, cssScale: 0.5 },
    saturation:  { min: -100, max: 100, css: 'saturate', cssUnit: '%', cssBase: 100, cssScale: 1.0 },
    temperature: { min: -100, max: 100, css: 'sepia', cssUnit: '%', cssBase: 0, cssScale: 0.3 },
    tint:        { min: -100, max: 100, css: 'hue-rotate', cssUnit: 'deg', cssBase: 0, cssScale: 0.5 },
    sharpness:   { min: 0, max: 100, css: null },
    vignette:    { min: 0, max: 100, css: null },
    dehaze:      { min: -100, max: 100, css: null },
    rotation:    { min: -180, max: 180, css: null },
  };

  // ==================== STATE ====================

  const state = {
    currentView: 'library',
    currentFolder: 'all',
    currentImage: null,
    activeFilm: 'none',
    activeTab: 'film',
    activeFilmCategory: 'classic',
    sliderValues: {},
    isSmartOptimized: false,
    isLongPressing: false,
  };

  // ==================== HELPERS ====================

  function $(sel) { return document.querySelector(sel); }
  function $$(sel) { return document.querySelectorAll(sel); }

  function getImageUrl(prompt) {
    return `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(prompt)}&image_size=landscape_4_3`;
  }

  function getFallbackSvg(id, w, h) {
    return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect fill="#2E2E2E" width="${w}" height="${h}"/><text x="${w/2}" y="${h/2+5}" text-anchor="middle" fill="#8C8C8C" font-size="14" font-family="sans-serif">IMG_${id}</text></svg>`)}`;
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
      img.onerror = function() { this.src = getFallbackSvg(photo.id, 200, 200); };

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
    state.activeFilm = 'none';
    state.isSmartOptimized = false;

    const preview = $('#preview-image');
    preview.src = getImageUrl(photo.prompt);
    preview.onerror = function() { this.src = getFallbackSvg(photo.id, 400, 300); };

    resetAllSliders();
    applyImageFilter();

    // Smart auto-optimization on load
    setTimeout(() => {
      simulateSmartOptimize();
    }, 600);

    switchView('editor');
    animateHistogram();
  }

  function simulateSmartOptimize() {
    state.isSmartOptimized = true;
    const badge = $('#optimized-badge');
    if (badge) {
      badge.style.opacity = '1';
    }
    // Apply a subtle auto-enhancement via CSS filter
    const img = $('#preview-image');
    if (img) {
      const currentFilter = img.style.filter || '';
      // Smart optimize: slight brightness/contrast/saturation boost
      const optimizeFilter = 'brightness(1.04) contrast(1.06) saturate(1.05)';
      img.style.filter = currentFilter ? currentFilter + ' ' + optimizeFilter : optimizeFilter;
    }
    showToast('智能优化完成');
  }

  function closeEditor() {
    state.currentImage = null;
    state.isSmartOptimized = false;
    const badge = $('#optimized-badge');
    if (badge) badge.style.opacity = '0';
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
      });
    });
  }

  // ==================== FILM GRID ====================

  function renderFilmGrid() {
    const grid = $('#film-grid');
    grid.innerHTML = '';

    const category = state.activeFilmCategory;
    const filteredFilms = FILMS.filter(f => f.category === category || f.id === 'none');

    // Always include "无滤镜" at the start
    const noFilter = FILMS[0]; // id: 'none'
    const categoryFilms = FILMS.filter(f => f.category === category);
    const filmsToShow = [noFilter, ...categoryFilms];

    filmsToShow.forEach(film => {
      const card = document.createElement('div');
      card.className = `film-card${film.id === 'none' ? ' nofilter' : ''}${state.activeFilm === film.id ? ' active' : ''}`;
      card.dataset.film = film.id;

      const thumb = document.createElement('div');
      thumb.className = 'film-card-thumb';
      if (film.gradient) {
        thumb.style.background = film.gradient;
      }

      const char = document.createElement('span');
      char.className = 'film-char';
      char.textContent = film.char;
      thumb.appendChild(char);

      card.appendChild(thumb);

      const name = document.createElement('div');
      name.className = 'film-card-name';
      name.textContent = film.name;
      card.appendChild(name);

      if (film.nameEn) {
        const nameEn = document.createElement('div');
        nameEn.className = 'film-card-name-en';
        nameEn.textContent = film.nameEn;
        card.appendChild(nameEn);
      }

      if (film.ref) {
        const ref = document.createElement('div');
        ref.className = 'film-card-ref';
        ref.textContent = film.ref;
        card.appendChild(ref);
      }

      card.addEventListener('click', () => {
        $$('.film-card').forEach(c => c.classList.remove('active'));
        card.classList.add('active');
        state.activeFilm = film.id;
        applyImageFilter();
        if (film.id === 'none') {
          showToast('已清除滤镜');
        } else {
          showToast(`已应用: ${film.name}`);
        }
      });

      grid.appendChild(card);
    });
  }

  function initFilmCategoryTabs() {
    $$('.film-cat-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        $$('.film-cat-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        state.activeFilmCategory = tab.dataset.cat;
        renderFilmGrid();
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

    // Intensity & brightness
    const intensity = vals.intensity || 0;
    const totalBrightness = 1 + (intensity * 0.005);
    if (intensity !== 0) filters.push(`brightness(${totalBrightness})`);

    // Tone → contrast
    const tone = vals.tone || 0;
    if (tone !== 0) filters.push(`contrast(${1 + tone * 0.005})`);

    // Saturation
    const saturation = vals.saturation || 0;
    if (saturation !== 0) filters.push(`saturate(${1 + saturation * 0.01})`);

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

    // Film simulation CSS filter
    const activeFilmData = FILMS.find(f => f.id === state.activeFilm);
    if (activeFilmData && activeFilmData.css) {
      const filmCss = activeFilmData.css;
      if (filmCss.brightness && filmCss.brightness !== 1) {
        filters.push(`brightness(${filmCss.brightness})`);
      }
      if (filmCss.contrast && filmCss.contrast !== 1) {
        filters.push(`contrast(${filmCss.contrast})`);
      }
      if (filmCss.saturate !== undefined && filmCss.saturate !== 1) {
        filters.push(`saturate(${filmCss.saturate})`);
      }
      if (filmCss.sepia && filmCss.sepia > 0) {
        filters.push(`sepia(${filmCss.sepia})`);
      }
      if (filmCss['hue-rotate'] && filmCss['hue-rotate'] !== 0) {
        filters.push(`hue-rotate(${filmCss['hue-rotate']}deg)`);
      }
    }

    // Smart optimize base
    if (state.isSmartOptimized) {
      filters.push('brightness(1.04)', 'contrast(1.06)', 'saturate(1.05)');
    }

    img.style.filter = filters.length > 0 ? filters.join(' ') : 'none';

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

  // ==================== LONG-PRESS PREVIEW ====================

  function initLongPress() {
    const previewArea = $('#preview-area');
    const overlay = $('#original-overlay');
    let pressTimer = null;
    let savedFilter = '';

    previewArea.addEventListener('mousedown', (e) => {
      if (e.target.closest('.histogram-overlay') || e.target.closest('.optimized-badge')) return;
      pressTimer = setTimeout(() => {
        state.isLongPressing = true;
        savedFilter = $('#preview-image').style.filter;
        $('#preview-image').style.filter = 'none';
        overlay.classList.add('visible');
      }, 500);
    });

    previewArea.addEventListener('mouseup', () => {
      clearTimeout(pressTimer);
      if (state.isLongPressing) {
        state.isLongPressing = false;
        overlay.classList.remove('visible');
        applyImageFilter();
      }
    });

    previewArea.addEventListener('mouseleave', () => {
      clearTimeout(pressTimer);
      if (state.isLongPressing) {
        state.isLongPressing = false;
        overlay.classList.remove('visible');
        applyImageFilter();
      }
    });

    // Touch support
    previewArea.addEventListener('touchstart', (e) => {
      if (e.target.closest('.histogram-overlay') || e.target.closest('.optimized-badge')) return;
      pressTimer = setTimeout(() => {
        state.isLongPressing = true;
        savedFilter = $('#preview-image').style.filter;
        $('#preview-image').style.filter = 'none';
        overlay.classList.add('visible');
      }, 500);
    }, { passive: true });

    previewArea.addEventListener('touchend', () => {
      clearTimeout(pressTimer);
      if (state.isLongPressing) {
        state.isLongPressing = false;
        overlay.classList.remove('visible');
        applyImageFilter();
      }
    });
  }

  // ==================== CROP PANEL ====================

  function initCropButtons() {
    $$('.crop-ratio-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        $$('.crop-ratio-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        if (btn.dataset.ratio !== 'free') {
          showToast(`裁剪比例: ${btn.dataset.ratio}`);
        } else {
          showToast('自由裁剪');
        }
      });
    });

    // Crop tool buttons
    const btnFlipH = $('#btn-flip-h');
    const btnFlipV = $('#btn-flip-v');
    const btnRotateCcw = $('#btn-rotate-ccw');
    const btnRotateCw = $('#btn-rotate-cw');

    if (btnFlipH) btnFlipH.addEventListener('click', () => showToast('水平翻转'));
    if (btnFlipV) btnFlipV.addEventListener('click', () => showToast('垂直翻转'));
    if (btnRotateCcw) btnRotateCcw.addEventListener('click', () => showToast('逆时针旋转90°'));
    if (btnRotateCw) btnRotateCw.addEventListener('click', () => showToast('顺时针旋转90°'));
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
        const center1 = 0.35;
        const center2 = 0.65;
        const val = Math.exp(-((x - center1) ** 2) / 0.05) * 0.8
                  + Math.exp(-((x - center2) ** 2) / 0.08) * 0.6
                  + Math.random() * 0.2
                  + 0.1;
        values.push(val);
        if (val > maxVal) maxVal = val;
      }

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

    let frame = 0;
    function animate() {
      if (state.currentView !== 'editor') return;
      frame++;
      if (frame % 120 === 0) {
        path.setAttribute('d', generateHistogram());
      }
      requestAnimationFrame(animate);
    }
    path.setAttribute('d', generateHistogram());
    animate();
  }

  // ==================== EXPORT ====================

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
      showToast('正在导出...');
    });
  }

  function initExportSlider() {
    const track = $('.export-slider-track');
    if (!track) return;
    const fill = track.querySelector('.export-slider-fill');
    const thumb = track.querySelector('.export-slider-thumb');
    const valEl = $('#quality-val');
    let isDragging = false;

    function setQuality(pct) {
      pct = Math.max(0, Math.min(1, pct));
      const val = Math.round(50 + pct * 50);
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
    $('#btn-back').addEventListener('click', closeEditor);
    $('#btn-undo').addEventListener('click', () => showToast('撤销'));
    $('#btn-redo').addEventListener('click', () => showToast('重做'));
    $('#btn-exif').addEventListener('click', () => showToast('EXIF 信息'));
  }

  // ==================== INITIALIZATION ====================

  function init() {
    renderLibrary();
    initFolderChips();
    initBottomNav();
    initTabs();
    initSliders();
    initFilmCategoryTabs();
    renderFilmGrid();
    initCropButtons();
    initExportOptions();
    initEditorActions();
    initLongPress();

    // Hide optimized badge initially
    const badge = $('#optimized-badge');
    if (badge) badge.style.opacity = '0';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
