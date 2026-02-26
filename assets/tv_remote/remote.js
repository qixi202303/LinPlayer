// Based on synamedia-senza/remote (ISC), but transport is WebSocket + JSON.

const params = new URLSearchParams(location.search);
const token = params.get('token') || '';

const statusEl = document.getElementById('status');

function wsUrl() {
  const isHttps = location.protocol === 'https:';
  const scheme = isHttps ? 'wss:' : 'ws:';
  return `${scheme}//${location.host}/ws?token=${encodeURIComponent(token)}`;
}

let socket = null;
let socketReady = false;
let connectStartedAt = 0;
let handshakeMs = null;
let lastRttMs = null;
let pingSeq = 0;
let pingTimer = null;
const pendingPings = new Map(); // id -> sentAt(ms)

function updateStatus(prefix) {
  const parts = [prefix];
  if (handshakeMs != null) parts.push(`握手 ${handshakeMs}ms`);
  if (lastRttMs != null) parts.push(`延迟 ${lastRttMs}ms`);
  statusEl.textContent = parts.join(' · ');
}

function connect() {
  if (!token) {
    statusEl.textContent = '缺少 token：请重新扫码打开。';
    return;
  }
  if (pingTimer) {
    clearInterval(pingTimer);
    pingTimer = null;
  }
  pendingPings.clear();
  connectStartedAt = Date.now();
  socket = new WebSocket(wsUrl());
  socketReady = false;

  socket.onopen = () => {
    socketReady = true;
    handshakeMs = Date.now() - connectStartedAt;
    updateStatus('已连接：可遥控 TV');
    sendPing();
    pingTimer = setInterval(sendPing, 2000);
  };

  socket.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      if (!msg || msg.type !== 'pong') return;
      const id = String(msg.id || '');
      const sentAt = pendingPings.get(id);
      if (!sentAt) return;
      pendingPings.delete(id);
      lastRttMs = Date.now() - sentAt;
      updateStatus('已连接：可遥控 TV');
    } catch (_) {
      // ignore
    }
  };

  socket.onclose = () => {
    socketReady = false;
    handshakeMs = null;
    lastRttMs = null;
    updateStatus('连接已断开，正在重连…');
    setTimeout(connect, 800);
  };

  socket.onerror = () => {
    socketReady = false;
    handshakeMs = null;
    lastRttMs = null;
    updateStatus('连接错误，正在重连…');
  };
}

function sendCommand(name, payload = {}) {
  if (!socket || !socketReady) return;
  const msg = { type: 'command', name, ...payload };
  socket.send(JSON.stringify(msg));
}

function sendPing() {
  if (!socket || !socketReady) return;
  const id = String(++pingSeq);
  pendingPings.set(id, Date.now());
  try {
    socket.send(JSON.stringify({ type: 'ping', id }));
  } catch (_) {
    // ignore
  }
}

// D-pad style commands
function left() { sendCommand('nav.left'); }
function right() { sendCommand('nav.right'); }
function up() { sendCommand('nav.up'); }
function down() { sendCommand('nav.down'); }
function enter() { sendCommand('nav.select'); }
function back() { sendCommand('nav.back'); }
function home() { sendCommand('nav.home'); }

connect();

// Optional: try to send some keys as text input commands.
const textfield = document.getElementById('textfield');
if (textfield) {
  textfield.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') return;
    if (event.key.length === 1) {
      sendCommand('input.text', { text: event.key });
    }
  });
}
