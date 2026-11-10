(function () {
  'use strict';
  if (window.__YUZUKI_BRIDGE_READY__) return;

  var N = window.YuzukiNative;
  if (!N) return;

  // ---- 0. 同步前端退出登录 / token 失效 ----
  // 前端 auth.logout() 与 401 拦截器都走 clearToken() -> localStorage.removeItem('yuzuki_token')
  try {
    var origRemoveItem = Storage.prototype.removeItem;
    Storage.prototype.removeItem = function (key) {
      if (key === 'yuzuki_token') {
        try { N.logout(); } catch (e) {}
      }
      return origRemoveItem.apply(this, arguments);
    };
  } catch (e) {}

  // ---- 1. MediaSession / MediaMetadata 代理（必须代理，不能删除）----
  // player.ts 的 mediaSessionSupported() 用 'mediaSession' in navigator 判定，
  // 删除后前端不再调用 updateMediaMetadata()，原生就拿不到封面/标题/声优。
  var fakeSession = {
    metadata: null,
    playbackState: 'none',
    setActionHandler: function () {},
    setPositionState: function () {},
    setCameraActive: function () {},
    setMicrophoneActive: function () {}
  };
  try {
    Object.defineProperty(navigator, 'mediaSession', { value: fakeSession, configurable: true });
  } catch (e) {
    try {
      Object.defineProperty(Navigator.prototype, 'mediaSession', { value: fakeSession, configurable: true });
    } catch (e2) { /* 低版本 WebView：降级为仅 Audio 代理 */ }
  }

  window.MediaMetadata = function (init) {
    init = init || {};
    var art = (init.artwork && init.artwork[0] && init.artwork[0].src) || '';
    try {
      N.setMetadata(JSON.stringify({
        title: init.title || '',
        artist: init.artist || '',
        album: init.album || '',
        artworkUrl: art ? new URL(art, location.origin).href : ''
      }));
    } catch (e) {}
    this.title = init.title;
    this.artist = init.artist;
    this.album = init.album;
    this.artwork = init.artwork || [];
  };

  // ---- 2. Audio 代理（player.ts 唯一播放入口是 new Audio()）----
  function NativeAudio() {
    this.preload = 'metadata';
    this._src = '';
    this._paused = true;
    this._t = 0;
    this.duration = 0;
    this._v = 1;
    this._loop = false;
    this.playbackRate = 1;
    this._listeners = Object.create(null);
    window.__yuzukiAudio = this;
    // 文档级注入时页面还没 new Audio()：把快照留到构造后回放一次
    if (window.__yuzukiPendingSnapshot) {
      var s = window.__yuzukiPendingSnapshot;
      window.__yuzukiPendingSnapshot = null;
      window.__yuzukiOnNativeEvent(s);
    }
  }
  Object.defineProperty(NativeAudio.prototype, 'src', {
    get: function () { return this._src; },
    set: function (v) {
      this._src = v;
      try { N.setSource(v ? new URL(v, location.origin).href : ''); } catch (e) {}
    }
  });
  Object.defineProperty(NativeAudio.prototype, 'paused', {
    get: function () { return this._paused; }, configurable: true
  });
  Object.defineProperty(NativeAudio.prototype, 'currentTime', {
    get: function () { return this._t || 0; },
    set: function (v) {
      this._t = v;
      try { N.seekTo(Math.round(v * 1000)); } catch (e) {}
    },
    configurable: true
  });
  Object.defineProperty(NativeAudio.prototype, 'volume', {
    get: function () { return this._v == null ? 1 : this._v; },
    set: function (v) {
      this._v = v;
      try { N.setVolume(v); } catch (e) {}
    },
    configurable: true
  });
  Object.defineProperty(NativeAudio.prototype, 'loop', {
    get: function () { return !!this._loop; },
    set: function (v) {
      this._loop = v;
      try { N.setLoop(!!v); } catch (e) {}
    },
    configurable: true
  });
  NativeAudio.prototype.play = function () {
    try { N.play(); } catch (e) {}
    return Promise.resolve(); // 不得 reject：player.ts 的 play().catch 会把 playing 置回 false
  };
  NativeAudio.prototype.pause = function () {
    try { N.pause(); } catch (e) {}
  };
  NativeAudio.prototype.load = function () {};
  NativeAudio.prototype.removeAttribute = function (name) {
    if (name === 'src') {
      this._src = '';
      try { N.stop(); } catch (e) {}
    }
  };
  NativeAudio.prototype.addEventListener = function (type, fn) {
    var arr = this._listeners[type] || (this._listeners[type] = []);
    if (arr.indexOf(fn) < 0) arr.push(fn);
  };
  NativeAudio.prototype.removeEventListener = function (type, fn) {
    var arr = this._listeners[type];
    if (!arr) return;
    var i = arr.indexOf(fn);
    if (i >= 0) arr.splice(i, 1);
  };
  NativeAudio.prototype.dispatchEvent = function (ev) {
    (this._listeners[ev.type] || []).slice().forEach(function (fn) {
      try { fn(ev); } catch (e) {}
    });
    return true;
  };
  window.Audio = NativeAudio;

  // ---- 3. Native -> JS 事件总线 ----
  window.__yuzukiOnNativeEvent = function (raw) {
    var e;
    try { e = (typeof raw === 'string') ? JSON.parse(raw) : raw; }
    catch (err) { return; }
    if (!e || !e.type) return;
    var a = window.__yuzukiAudio;
    if (!a) {
      if (e.type === 'state') window.__yuzukiPendingSnapshot = e;
      return;
    }
    if (e.type === 'state' || e.type === 'timeupdate' || e.type === 'durationchange') {
      if (typeof e.currentTime === 'number') a._t = e.currentTime;
      if (typeof e.duration === 'number') a.duration = e.duration;
      if (typeof e.paused === 'boolean') a._paused = e.paused;
      if (typeof e.volume === 'number') a._v = e.volume;
    }
    if (e.type === 'play') a._paused = false;
    if (e.type === 'pause' || e.type === 'ended' || e.type === 'error') a._paused = true;
    a.dispatchEvent(new Event(e.type));
  };

  // ---- 4. 导航辅助：S2 已独立注入，此处只兜底、绝不覆盖 ----
  if (!window.__yuzukiNav) {
    window.__yuzukiNav = function (name) {
      var p = name === 'history' ? '/history' : name === 'favorites' ? '/favorites' : '/';
      if (location.pathname === p) { window.scrollTo(0, 0); return; }
      try {
        history.pushState({}, '', p);
        window.dispatchEvent(new PopStateEvent('popstate', { state: history.state }));
      } catch (e) { location.assign(p); }
    };
  }
  if (!window.__yuzukiOpenPlayer) {
    window.__yuzukiOpenPlayer = function () {
      var b = document.querySelector('.bottom-player .btn.expand');
      return !!b && (b.click(), true);
    };
  }

  // ---- 5. WebView 重建后拉取最新状态快照 ----
  try {
    var snapshot = N.getSnapshot();
    if (snapshot) window.__yuzukiOnNativeEvent(snapshot);
  } catch (e) {}

  window.__YUZUKI_BRIDGE_READY__ = true;
})();
