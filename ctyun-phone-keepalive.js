/*
天翼云手机账密保活

环境变量:
1. OCR_SERVER=http://127.0.0.1:5000
2. CTYUN_PHONE_ACCOUNTS=账号1#密码1&账号2#密码2&账号3#密码3

兼容变量:
1. CTYUN_ACCOUNTS=账号1#密码1&账号2#密码2

可选变量:
1. CTYUN_MAX_PARALLEL=2
2. CTYUN_STATUS_POLL_INTERVAL_MS=3000
3. CTYUN_BOOT_WAIT_MS=720000
4. CTYUN_ENTER_WAIT_MS=90000
5. CTYUN_POST_ENTER_HOLD_MS=15000
6. CTYUN_STATE_REFRESH_INTERVAL_MS=15000
7. CTYUN_CLINK_HOLD_MS=20000
8. CTYUN_CLINK_ATTACH_RETRIES=2
9. CTYUN_CLINK_RETRY_DELAY_MS=3000
10. CTYUN_DEBUG=false
11. CTYUN_STATE_FILE=自定义状态文件路径
*/

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

if (typeof fetch !== "function") {
  throw new Error("当前 Node 环境不支持 fetch，请使用 Node.js 18+ 运行。");
}

function resolveWebSocketImpl() {
  if (typeof WebSocket === "function") {
    return WebSocket;
  }

  for (const loader of [() => require("ws"), () => require("undici").WebSocket]) {
    try {
      const loaded = loader();
      const impl = loaded && (loaded.WebSocket || loaded.default || loaded);
      if (typeof impl === "function") {
        return impl;
      }
    } catch (error) {
      continue;
    }
  }

  throw new Error(
    "当前 Node 环境没有可用的 WebSocket 实现。Node.js 20 无需升级，先安装 ws 后再运行。"
  );
}

const WebSocketImpl = resolveWebSocketImpl();
const WS_READY_STATE_OPEN =
  typeof WebSocketImpl.OPEN === "number" ? WebSocketImpl.OPEN : 1;
const WS_READY_STATE_CONNECTING =
  typeof WebSocketImpl.CONNECTING === "number" ? WebSocketImpl.CONNECTING : 0;

const API_HOST = "https://desk.ctyun.cn:8810";
const DESKTOP_TOKEN_HEADER = "X-AUTH-TOKEN";
const DEFAULTS = {
  appModel: 3,
  deviceType: 60,
  osType: 15,
  appVersion: "3.2.0",
  version: 103020001,
  deviceName: "Chrome浏览器",
  deviceModel: "Windows NT 10.0; Win64; x64",
  sysVersion: "Windows NT 10.0; Win64; x64",
  userAgent:
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
  requestTimeoutMs: 15000,
  networkRetryCount: 2,
  networkRetryDelayMs: 1500,
  maxParallel: 2,
  statusPollIntervalMs: 3000,
  bootWaitMs: 3 * 60 * 1000,
  enterWaitMs: 90 * 1000,
  postEnterHoldMs: 15 * 1000,
  stateRefreshIntervalMs: 15 * 1000,
  clinkHoldMs: 10 * 1000,
  clinkAttachRetries: 2,
  clinkRetryDelayMs: 3 * 1000,
  maxCaptchaRetries: 5,
  debug: false,
};

const API_CODES = {
  NO_PERMISSIONS: 40010,
  INVALID_PASSWORD: 51010,
  AUTH_LOCKED: 51020,
  INVALID_CAPTCHA: 51030,
  EXPIRE_CAPTCHA: 51031,
  NEED_CAPTCHA: 51040,
  ERROR_CAPTCHA: 51085,
};

class ApiError extends Error {
  constructor(message, code, payload, meta = {}) {
    super(message);
    this.name = "ApiError";
    this.code = Number(code || 0);
    this.payload = payload;
    this.method = String(meta.method || "").toUpperCase();
    this.pathname = String(meta.pathname || "");
    this.auth = Boolean(meta.auth);
  }
}

function readEnv(...names) {
  for (const name of names) {
    const value = process.env[name];
    if (value !== undefined && String(value).trim() !== "") {
      return String(value).trim();
    }
  }
  return "";
}

function readIntEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || String(raw).trim() === "") {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    throw new Error(`${name} 必须是数字。`);
  }
  return value;
}

function readBoolEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || String(raw).trim() === "") {
    return fallback;
  }

  const value = String(raw).trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(value)) {
    return true;
  }
  if (["0", "false", "no", "off"].includes(value)) {
    return false;
  }
  throw new Error(`${name} 必须是 true/false。`);
}

function normalizeAccountKey(username) {
  return String(username || "").trim().toLowerCase();
}

function normalizeBaseUrl(url) {
  return url.replace(/\/+$/, "");
}

function sha256(text) {
  return crypto.createHash("sha256").update(String(text)).digest("hex");
}

function md5Upper(text) {
  return crypto.createHash("md5").update(String(text)).digest("hex").toUpperCase();
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function nowText() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, "0");
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

function divider(char = "=") {
  console.log(char.repeat(68));
}

function section(title) {
  console.log("");
  divider();
  console.log(title);
  divider();
}

function line(label, message) {
  console.log(`${label} ${message}`);
}

function maskAccount(account) {
  const value = String(account || "").trim();
  if (/^\d{11}$/.test(value)) {
    return `${value.slice(0, 3)}****${value.slice(-4)}`;
  }
  if (value.includes("@")) {
    const [name, domain] = value.split("@");
    if (name.length <= 2) {
      return `${name[0] || "*"}***@${domain}`;
    }
    return `${name.slice(0, 2)}***@${domain}`;
  }
  if (value.length <= 4) {
    return `${value[0] || "*"}***`;
  }
  return `${value.slice(0, 2)}***${value.slice(-2)}`;
}

function shorten(text, maxLength = 120) {
  const value = String(text || "").replace(/\s+/g, " ").trim();
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength - 3)}...`;
}

function buildErrorMessage(error) {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function readJsonSafe(filePath, fallback) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    return fallback;
  }
}

function writeJson(filePath, data) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), "utf8");
}

function loadState(filePath) {
  const state = readJsonSafe(filePath, { version: 1, accounts: {} });
  return {
    version: 1,
    accounts: state && typeof state.accounts === "object" && state.accounts ? state.accounts : {},
  };
}

function getCachedDeviceCode(state, username) {
  const key = normalizeAccountKey(username);
  return String(state?.accounts?.[key]?.deviceCode || "").trim();
}

function setCachedDeviceCode(state, username, deviceCode) {
  const key = normalizeAccountKey(username);
  if (!state.accounts || typeof state.accounts !== "object") {
    state.accounts = {};
  }
  state.accounts[key] = {
    ...(state.accounts[key] || {}),
    deviceCode: String(deviceCode),
    updatedAt: new Date().toISOString(),
  };
}

function getCachedAuth(state, username) {
  const key = normalizeAccountKey(username);
  const auth = state?.accounts?.[key]?.auth;
  if (!auth || typeof auth !== "object") {
    return null;
  }
  if (!auth.tenantId || !auth.userId || !auth.secretKey) {
    return null;
  }
  return {
    tenantId: auth.tenantId,
    userId: auth.userId,
    secretKey: auth.secretKey,
    userAccount: auth.userAccount || "",
    userName: auth.userName || "",
    bondedDevice: auth.bondedDevice,
    timestamp: auth.timestamp || "",
  };
}

function setCachedAuth(state, username, auth) {
  const key = normalizeAccountKey(username);
  if (!state.accounts || typeof state.accounts !== "object") {
    state.accounts = {};
  }
  state.accounts[key] = {
    ...(state.accounts[key] || {}),
    auth: {
      tenantId: auth.tenantId,
      userId: auth.userId,
      secretKey: auth.secretKey,
      userAccount: auth.userAccount || "",
      userName: auth.userName || "",
      bondedDevice: auth.bondedDevice,
      timestamp: auth.timestamp || "",
      updatedAt: new Date().toISOString(),
    },
  };
}

function clearCachedAuth(state, username) {
  const key = normalizeAccountKey(username);
  if (!state.accounts || !state.accounts[key]) {
    return;
  }
  delete state.accounts[key].auth;
  state.accounts[key].updatedAt = new Date().toISOString();
}

function parseAccounts(raw) {
  return raw
    .split("&")
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item, index) => {
      const splitIndex = item.indexOf("#");
      if (splitIndex <= 0 || splitIndex === item.length - 1) {
        throw new Error(`第 ${index + 1} 组账号格式不正确，应为 账号#密码。`);
      }
      const username = item.slice(0, splitIndex).trim();
      const password = item.slice(splitIndex + 1).trim();
      if (!username || !password) {
        throw new Error(`第 ${index + 1} 组账号格式不正确，应为 账号#密码。`);
      }
      return { username, password };
    });
}

function buildDeviceCode(username) {
  const digest = crypto
    .createHash("md5")
    .update(`ctyun_phone_keepalive:${String(username).toLowerCase()}`)
    .digest("hex");
  return `web_phone_${digest}`;
}

function isCaptchaCode(code) {
  return [
    API_CODES.NEED_CAPTCHA,
    API_CODES.INVALID_CAPTCHA,
    API_CODES.EXPIRE_CAPTCHA,
    API_CODES.ERROR_CAPTCHA,
  ].includes(Number(code));
}

function buildQueryString(params) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  return search;
}

function buildFormBody(params) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null) {
      search.set(key, String(value));
    }
  }
  return search;
}

async function runWithConcurrency(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;

  const runners = Array.from({ length: Math.max(1, limit) }, async () => {
    while (true) {
      const current = cursor++;
      if (current >= items.length) {
        return;
      }
      results[current] = await worker(items[current], current);
    }
  });

  await Promise.all(runners);
  return results;
}

const CLINK_MESSAGE_TYPES = {
  ACK_SYNC: 1,
  PONG: 3,
  PING: 4,
  HEARTBEAT: 7,
  HEARTBEAT_RES: 9,
  DISPLAY_INIT: 101,
  MAIN_INIT: 103,
  MOUSE_MODE_REQUEST: 105,
  DISPLAY_BOOTSTRAP: 108,
  DISPLAY_RESUME: 110,
  CLIENT_LOGIN_INFO: 112,
  CLIENT2SERVER_CUSTOM: 118,
};

const CLINK_HEARTBEAT_INTERVAL_MS = 30 * 1000;

const CLINK_CHANNEL_SPECS = [
  {
    key: "MAIN",
    name: "MAIN",
    channelType: 1,
    urlSegment: "MAIN",
    required: true,
    linkTailHex: "01000100000001000000120000000900000004080000",
  },
  {
    key: "DISPLAY",
    name: "DISPLAY",
    channelType: 2,
    urlSegment: "DISPLAY",
    required: true,
    linkTailHex: "020001000000010000001200000009000000114d8808",
  },
  {
    key: "INPUTS",
    name: "INPUTS",
    channelType: 3,
    urlSegment: "INPUTS",
    required: true,
    linkTailHex: "030001000000000000001200000009000000",
  },
  {
    key: "CURSOR",
    name: "CURSOR",
    channelType: 4,
    urlSegment: "CURSOR",
    required: true,
    linkTailHex: "040001000000000000001200000009000000",
  },
  {
    key: "PLAYBACK",
    name: "PLAYBACK",
    channelType: 5,
    urlSegment: "PLAYBACK",
    required: false,
    linkTailHex: "0500010000000100000012000000090000000e000000",
  },
  {
    key: "RECORD",
    name: "RECORD",
    channelType: 6,
    urlSegment: "RECORD",
    required: false,
    linkTailHex: "06000100000004000000120000000900000002000000040000000800000010000000",
  },
  {
    key: "PORT0",
    name: "PORT",
    channelType: 10,
    urlSegment: "PORT",
    required: false,
    linkTailHex: "0a0001000000000000001200000009000000",
  },
  {
    key: "PORT1",
    name: "PORT",
    channelType: 10,
    urlSegment: "PORT",
    required: false,
    linkTailHex: "0a0101000000000000001200000009000000",
  },
  {
    key: "DATA",
    name: "DATA",
    channelType: 12,
    urlSegment: "DATA",
    required: false,
    linkTailHex: "0c0001000000000000001200000009000000",
  },
];

const CLINK_DER_PUBLIC_KEY_MARKER = Buffer.from(
  "30819f300d06092a864886f70d010101050003818d0030818902818100",
  "hex"
);
const CLINK_DISPLAY_BOOTSTRAP_PAYLOAD = Buffer.from("0223031903", "hex");
const CLINK_DISPLAY_INIT_PAYLOAD = Buffer.from(
  "010000a000000000000000000000",
  "hex"
);

function createDeferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function withTimeout(promise, ms, label) {
  if (!Number.isFinite(ms) || ms <= 0) {
    return promise;
  }

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(`${label} 超时`));
    }, ms);

    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}

class AsyncQueue {
  constructor() {
    this.items = [];
    this.waiters = [];
    this.error = null;
  }

  push(item) {
    if (this.error) {
      return;
    }
    if (this.waiters.length > 0) {
      const waiter = this.waiters.shift();
      waiter.resolve(item);
      return;
    }
    this.items.push(item);
  }

  unshift(item) {
    if (this.error || item === undefined || item === null) {
      return;
    }
    if (this.waiters.length > 0) {
      const waiter = this.waiters.shift();
      waiter.resolve(item);
      return;
    }
    this.items.unshift(item);
  }

  fail(error) {
    if (this.error) {
      return;
    }
    this.error = error;
    while (this.waiters.length > 0) {
      const waiter = this.waiters.shift();
      waiter.reject(error);
    }
  }

  async next() {
    if (this.error) {
      throw this.error;
    }
    if (this.items.length > 0) {
      return this.items.shift();
    }
    return new Promise((resolve, reject) => {
      this.waiters.push({ resolve, reject });
    });
  }
}

async function toBufferFromMessage(data) {
  if (Buffer.isBuffer(data)) {
    return data;
  }
  if (data instanceof ArrayBuffer) {
    return Buffer.from(data);
  }
  if (ArrayBuffer.isView(data)) {
    return Buffer.from(data.buffer, data.byteOffset, data.byteLength);
  }
  if (typeof Blob !== "undefined" && data instanceof Blob) {
    return Buffer.from(await data.arrayBuffer());
  }
  if (typeof data === "string") {
    return Buffer.from(data, "utf8");
  }
  throw new Error(`不支持的 WebSocket 消息类型: ${typeof data}`);
}

function addWebSocketListener(ws, type, handler) {
  if (typeof ws.addEventListener === "function") {
    ws.addEventListener(type, handler);
    return;
  }

  if (typeof ws.on === "function") {
    if (type === "open") {
      ws.on("open", () => handler({}));
      return;
    }
    if (type === "message") {
      ws.on("message", (data) => handler({ data }));
      return;
    }
    if (type === "error") {
      ws.on("error", (error) => handler({ error }));
      return;
    }
    if (type === "close") {
      ws.on("close", (code, reason) => handler({ code, reason }));
      return;
    }
  }

  throw new Error("当前 WebSocket 实现不支持事件监听。");
}

function formatIpv6Host(host) {
  const value = String(host || "").trim();
  if (!value || value.includes("[") || !value.includes(":")) {
    return value;
  }
  return `[${value}]`;
}

function splitHostPort(text, fallbackPort) {
  const value = String(text || "").trim();
  if (!value) {
    return { host: "", port: String(fallbackPort || "").trim() };
  }
  if (value.startsWith("[")) {
    const matched = value.match(/^\[([^\]]+)\](?::(\d+))?$/);
    if (matched) {
      return {
        host: matched[1],
        port: matched[2] || String(fallbackPort || "").trim(),
      };
    }
  }

  const colonCount = (value.match(/:/g) || []).length;
  if (colonCount === 1) {
    const [host, port] = value.split(":");
    if (/^\d+$/.test(port)) {
      return { host, port };
    }
  }

  return { host: value, port: String(fallbackPort || "").trim() };
}

function encodeCString(text) {
  return Buffer.from(`${String(text || "")}\0`, "utf8");
}

function decodeBase64Url(text) {
  const value = String(text || "").replace(/-/g, "+").replace(/_/g, "/");
  const padding = value.length % 4 === 0 ? "" : "=".repeat(4 - (value.length % 4));
  return Buffer.from(`${value}${padding}`, "base64");
}

function decodeJwtPayload(token) {
  const parts = String(token || "").split(".");
  if (parts.length < 2) {
    return null;
  }
  try {
    return JSON.parse(decodeBase64Url(parts[1]).toString("utf8"));
  } catch (error) {
    return null;
  }
}

function collectStringCandidates(target, value) {
  if (Array.isArray(value)) {
    value.forEach((item) => collectStringCandidates(target, item));
    return;
  }
  const text = String(value || "").trim();
  if (text) {
    target.push(text);
  }
}

function parseClinkUrlCandidate(candidate, desktopId) {
  const text = String(candidate || "").trim();
  if (!/^wss?:\/\//i.test(text)) {
    return null;
  }

  try {
    const url = new URL(text);
    let pathname = url.pathname.replace(
      /\/(?:MAIN|DISPLAY|INPUTS|CURSOR|RECORD|PLAYBACK|PORT|DATA)$/i,
      ""
    );

    if (!/\/clinkProxy(?:\/|$)/i.test(pathname)) {
      return null;
    }

    pathname = pathname.replace(/\/+$/, "");
    if (!new RegExp(`/${desktopId}$`).test(pathname)) {
      pathname = `${pathname}/${desktopId}`;
    }

    return {
      uri: `${url.protocol}//${url.host}${pathname}`,
      host: url.hostname,
      port: url.port || (url.protocol === "wss:" ? "443" : "80"),
    };
  } catch (error) {
    return null;
  }
}

function resolveClinkEndpoint(desktopId, desktopInfo, device) {
  const lvsHost = String(
    desktopInfo?.clinkLvsOutHost ||
      desktopInfo?.clinkIpv6LvsOutHost ||
      desktopInfo?.clinkLvsOutHostBak ||
      desktopInfo?.clinkIpv6LvsOutHostBak ||
      ""
  ).trim();
  const lvsPort = String(
    desktopInfo?.clinkLvsOutPort ||
      desktopInfo?.clinkPort ||
      desktopInfo?.clinkLvsPort ||
      "9011"
  ).trim();
  if (lvsHost) {
    const parsed = splitHostPort(lvsHost, lvsPort);
    return {
      uri: `wss://${formatIpv6Host(parsed.host)}:${parsed.port}/clinkProxy/${desktopId}`,
      host: parsed.host,
      port: parsed.port,
    };
  }

  const host = String(desktopInfo?.host || "").trim();
  const port = String(desktopInfo?.port || "").trim();
  if (host && port) {
    return {
      uri: `wss://${formatIpv6Host(host)}:${port}/clinkProxy/${desktopId}`,
      host,
      port,
    };
  }

  const candidates = [];
  collectStringCandidates(candidates, desktopInfo?.connectUrl);
  collectStringCandidates(candidates, desktopInfo?.connectUrls);
  collectStringCandidates(candidates, desktopInfo?.websocketUrl);
  collectStringCandidates(candidates, device?.connectUrl);
  collectStringCandidates(candidates, device?.connectUrls);

  for (const candidate of candidates) {
    const parsed = parseClinkUrlCandidate(candidate, desktopId);
    if (parsed) {
      return parsed;
    }
  }

  return {
    uri: `wss://deskmsgz.ctyun.cn:9011/clinkProxy/${desktopId}`,
    host: "deskmsgz.ctyun.cn",
    port: "9011",
  };
}

function buildClinkHelloPayload(spec, clinkConfig) {
  return JSON.stringify({
    type: spec.channelType,
    ssl: 1,
    host: clinkConfig.host,
    port: String(clinkConfig.port),
    ca: clinkConfig.caCert,
    cert: clinkConfig.clientCert,
    key: clinkConfig.clientKey,
    servername: clinkConfig.serverName,
    oqs: clinkConfig.oqs ? 1 : 0,
  });
}

function buildClinkRedqPacket(payload) {
  const header = Buffer.alloc(16);
  header.write("REDQ", 0, "ascii");
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(2, 8);
  header.writeUInt32LE(payload.length, 12);
  return Buffer.concat([header, payload]);
}

function buildClinkLinkMessage(sessionId, linkTailHex) {
  const tail = Buffer.from(linkTailHex, "hex");
  const payload = Buffer.alloc(4 + tail.length);
  payload.writeUInt32LE(sessionId, 0);
  tail.copy(payload, 4);
  return buildClinkRedqPacket(payload);
}

function parseClinkReplyPacket(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 16) {
    return null;
  }

  const start = buffer.indexOf("REDQ", 0, "ascii");
  if (start < 0 || buffer.length < start + 16) {
    return null;
  }

  const size = buffer.readUInt32LE(start + 12);
  const end = start + 16 + size;
  if (buffer.length < end) {
    return null;
  }

  return {
    packet: buffer.subarray(start, end),
    rest: buffer.subarray(end),
  };
}

function readDerObjectLength(buffer, offset) {
  if (buffer.length < offset + 2) {
    throw new Error("DER 公钥长度不足");
  }

  const lengthByte = buffer[offset + 1];
  if ((lengthByte & 0x80) === 0) {
    return 2 + lengthByte;
  }

  const lengthSize = lengthByte & 0x7f;
  if (buffer.length < offset + 2 + lengthSize) {
    throw new Error("DER 公钥长度头不完整");
  }

  let valueLength = 0;
  for (let index = 0; index < lengthSize; index += 1) {
    valueLength = (valueLength << 8) | buffer[offset + 2 + index];
  }

  return 2 + lengthSize + valueLength;
}

function extractClinkPublicKey(packet) {
  const start = packet.indexOf(CLINK_DER_PUBLIC_KEY_MARKER);
  if (start < 0) {
    throw new Error("未从 clink 回复中找到公钥");
  }
  const totalLength = readDerObjectLength(packet, start);
  return packet.subarray(start, start + totalLength);
}

function buildClinkAuthPacket(publicKey) {
  const encrypted = crypto.publicEncrypt(
    {
      key: publicKey,
      format: "der",
      type: "spki",
      padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: "sha1",
      oaepLabel: Buffer.alloc(0),
    },
    Buffer.from([0])
  );
  const prefix = Buffer.alloc(4);
  prefix.writeUInt32LE(1, 0);
  return Buffer.concat([prefix, encrypted]);
}

function buildClinkMessage(type, payload = Buffer.alloc(0)) {
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(payload);
  const buffer = Buffer.alloc(6 + body.length);
  buffer.writeUInt16LE(type, 0);
  buffer.writeUInt32LE(body.length, 2);
  body.copy(buffer, 6);
  return buffer;
}

function parseClinkMessages(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 6) {
    return [];
  }
  if (buffer.indexOf("REDQ", 0, "ascii") === 0) {
    return [];
  }

  const messages = [];
  let offset = 0;
  while (offset + 6 <= buffer.length) {
    const type = buffer.readUInt16LE(offset);
    const size = buffer.readUInt32LE(offset + 2);
    const end = offset + 6 + size;
    if (size > buffer.length || end > buffer.length) {
      return messages.length > 0 ? messages : [];
    }

    messages.push({
      type,
      size,
      payload: buffer.subarray(offset + 6, end),
    });
    offset = end;
  }

  return messages;
}

function buildAckSyncPayload(payload) {
  return Buffer.from(payload.subarray(0, 4));
}

function buildMouseModeRequestPayload() {
  const payload = Buffer.alloc(2);
  payload.writeUInt16LE(2, 0);
  return payload;
}

function buildCustomJsonPayload(userName, userId) {
  const json = Buffer.from(
    JSON.stringify({
      type: 1,
      userName: String(userName || ""),
      userInfo: "",
      userId: Number(userId || 0),
    }),
    "utf8"
  );
  const header = Buffer.alloc(8);
  header.writeUInt32LE(json.length, 0);
  header.writeUInt32LE(8, 4);
  return Buffer.concat([header, json]);
}

function buildLoginInfoPayload(loginInfo) {
  const desktopId = Number(loginInfo.desktopId);
  if (!Number.isFinite(desktopId)) {
    throw new Error(`desktopId 无法转换为数字: ${loginInfo.desktopId}`);
  }

  const fields = [
    encodeCString(loginInfo.token),
    encodeCString(loginInfo.deviceType),
    encodeCString(loginInfo.deviceCode),
    encodeCString(loginInfo.userAccount),
  ];

  const header = Buffer.alloc(36);
  header.writeUInt32LE(desktopId, 0);

  let offset = 36;
  for (let index = 0; index < fields.length; index += 1) {
    header.writeUInt32LE(fields[index].length, 4 + index * 8);
    header.writeUInt32LE(offset, 8 + index * 8);
    offset += fields[index].length;
  }

  return Buffer.concat([header, ...fields]);
}

class ClinkChannel {
  constructor(attacher, spec) {
    this.attacher = attacher;
    this.runner = attacher.runner;
    this.spec = spec;
    this.label = `${attacher.session.label}/${spec.key}`;
    this.url = `${attacher.clinkConfig.uri}/${spec.urlSegment}`;
    this.helloPayload = buildClinkHelloPayload(spec, attacher.clinkConfig);
    this.timeoutMs = Math.max(10000, this.runner.config.requestTimeoutMs);
    this.rawQueue = new AsyncQueue();
    this.typeWaiters = new Map();
    this.lastMessages = new Map();
    this.ready = false;
    this.closed = false;
    this.closing = false;
    this.failedError = null;
    this.ws = null;
    this.replyDebugCount = 0;
    this.authDebugCount = 0;
    this.heartbeatTimer = null;
  }

  async connect() {
    await this.open();
    this.sendText(this.helloPayload);
    await this.waitForHandshakeStart();
    this.sendBuffer(
      buildClinkLinkMessage(
        this.attacher.getLinkSessionId(this.spec),
        this.spec.linkTailHex
      )
    );
    const reply = await this.waitForReplyPacket();
    this.sendBuffer(buildClinkAuthPacket(extractClinkPublicKey(reply)));
    await this.waitForAuthAck();
    this.ready = true;
    await this.flushRawQueue();
    this.startHeartbeatLoop();
    return this;
  }

  async open() {
    const opened = createDeferred();
    this.ws = new WebSocketImpl(this.url, "binary");
    if ("binaryType" in this.ws) {
      this.ws.binaryType = "arraybuffer";
    }
    addWebSocketListener(this.ws, "open", () => opened.resolve());
    addWebSocketListener(this.ws, "message", (event) => {
      void this.onMessage(event.data);
    });
    addWebSocketListener(this.ws, "error", () => {
      this.fail(new Error(`${this.label} WebSocket 连接失败`));
    });
    addWebSocketListener(this.ws, "close", (event) => {
      this.closed = true;
      if (!this.closing) {
        this.fail(new Error(`${this.label} WebSocket 已关闭 (${event.code})`));
      }
    });
    await withTimeout(opened.promise, this.timeoutMs, `${this.label} 建连`);
  }

  async onMessage(data) {
    try {
      const buffer = await toBufferFromMessage(data);
      if (!this.ready) {
        this.rawQueue.push(buffer);
        return;
      }
      this.handleReadyBuffer(buffer);
    } catch (error) {
      this.fail(error);
    }
  }

  handleReadyBuffer(buffer) {
    const reply = parseClinkReplyPacket(buffer);
    if (reply) {
      if (reply.rest.length > 0) {
        this.handleReadyBuffer(reply.rest);
      }
      return;
    }

    const messages = parseClinkMessages(buffer);
    if (messages.length === 0) {
      return;
    }

    for (const message of messages) {
      this.lastMessages.set(message.type, message);
      const waiters = this.typeWaiters.get(message.type);
      if (waiters && waiters.length > 0) {
        this.typeWaiters.delete(message.type);
        waiters.forEach((deferred) => deferred.resolve(message));
      }

      if (message.type === 3 && message.payload.length >= 4) {
        this.sendMessage(
          CLINK_MESSAGE_TYPES.ACK_SYNC,
          buildAckSyncPayload(message.payload)
        );
        continue;
      }

      if (message.type === CLINK_MESSAGE_TYPES.PING) {
        this.sendMessage(CLINK_MESSAGE_TYPES.PONG, message.payload);
        continue;
      }

      if (message.type === CLINK_MESSAGE_TYPES.HEARTBEAT) {
        this.sendMessage(CLINK_MESSAGE_TYPES.HEARTBEAT_RES, message.payload);
      }
    }
  }

  async waitForHandshakeStart() {
    while (true) {
      const buffer = await withTimeout(
        this.rawQueue.next(),
        this.timeoutMs,
        `${this.label} 握手应答`
      );
      if (buffer.length === 1 && buffer[0] === 1) {
        return;
      }
    }
  }

  async waitForReplyPacket() {
    let pending = Buffer.alloc(0);
    while (true) {
      const buffer = await withTimeout(
        this.rawQueue.next(),
        this.timeoutMs,
        `${this.label} 握手公钥`
      );
      pending = pending.length > 0 ? Buffer.concat([pending, buffer]) : buffer;
      const reply = parseClinkReplyPacket(pending);
      if (!reply) {
        if (this.replyDebugCount < 3) {
          this.replyDebugCount += 1;
          this.runner.debug(`clink reply raw ${this.label}`, {
            length: pending.length,
            hex: pending.toString("hex").slice(0, 120),
          });
        }
        continue;
      }
      if (reply.rest.length > 0) {
        this.rawQueue.unshift(reply.rest);
      }
      return reply.packet;
    }
  }

  async waitForAuthAck() {
    let pending = Buffer.alloc(0);
    while (true) {
      const buffer = await withTimeout(
        this.rawQueue.next(),
        this.timeoutMs,
        `${this.label} 鉴权确认`
      );
      pending = pending.length > 0 ? Buffer.concat([pending, buffer]) : buffer;
      if (pending.length >= 4 && pending.readInt32LE(0) === 0) {
        return;
      }
      if (this.authDebugCount < 3) {
        this.authDebugCount += 1;
        this.runner.debug(`clink auth raw ${this.label}`, {
          length: pending.length,
          hex: pending.toString("hex").slice(0, 120),
        });
      }
    }
  }

  async flushRawQueue() {
    while (this.rawQueue.items.length > 0) {
      const buffer = this.rawQueue.items.shift();
      this.handleReadyBuffer(buffer);
    }
  }

  startHeartbeatLoop() {
    if (this.heartbeatTimer) {
      return;
    }
    this.heartbeatTimer = setTimeout(() => {
      this.heartbeatTimer = null;
      if (this.closed || this.closing || this.failedError || !this.ready) {
        return;
      }
      try {
        this.sendMessage(CLINK_MESSAGE_TYPES.HEARTBEAT);
      } catch (error) {
        this.fail(error);
        return;
      }
      this.startHeartbeatLoop();
    }, CLINK_HEARTBEAT_INTERVAL_MS);
  }

  stopHeartbeatLoop() {
    if (!this.heartbeatTimer) {
      return;
    }
    clearTimeout(this.heartbeatTimer);
    this.heartbeatTimer = null;
  }

  waitForType(type, timeoutMs = this.timeoutMs) {
    const cached = this.lastMessages.get(type);
    if (cached) {
      return Promise.resolve(cached);
    }
    const deferred = createDeferred();
    const waiters = this.typeWaiters.get(type) || [];
    waiters.push(deferred);
    this.typeWaiters.set(type, waiters);
    return withTimeout(deferred.promise, timeoutMs, `${this.label} 等待消息 ${type}`);
  }

  sendText(text) {
    this.ensureOpen();
    this.ws.send(String(text));
  }

  sendBuffer(buffer) {
    this.ensureOpen();
    this.ws.send(buffer);
  }

  sendMessage(type, payload = Buffer.alloc(0)) {
    this.sendBuffer(buildClinkMessage(type, payload));
  }

  ensureOpen() {
    if (this.failedError) {
      throw this.failedError;
    }
    if (!this.ws || this.ws.readyState !== WS_READY_STATE_OPEN) {
      throw new Error(`${this.label} WebSocket 未处于可发送状态`);
    }
  }

  fail(error) {
    if (this.failedError) {
      return;
    }
    const normalized = error instanceof Error ? error : new Error(String(error));
    this.failedError = normalized;
    this.stopHeartbeatLoop();
    this.rawQueue.fail(normalized);
    for (const waiters of this.typeWaiters.values()) {
      waiters.forEach((deferred) => deferred.reject(normalized));
    }
    this.typeWaiters.clear();
  }

  close() {
    this.closing = true;
    this.stopHeartbeatLoop();
    if (!this.ws) {
      return;
    }
    if (
      this.ws.readyState === WS_READY_STATE_OPEN ||
      this.ws.readyState === WS_READY_STATE_CONNECTING
    ) {
      try {
        this.ws.close();
      } catch (error) {
        return;
      }
    }
  }
}

class ClinkDesktopAttacher {
  constructor(runner, session, clinkConfig) {
    this.runner = runner;
    this.session = session;
    this.clinkConfig = clinkConfig;
    this.channels = [];
    this.sessionId = 0;
  }

  debug(label, payload) {
    this.runner.debug(label, payload);
  }

  async attach() {
    this.debug(`clink 配置 ${this.session.label}`, {
      uri: this.clinkConfig.uri,
      host: this.clinkConfig.host,
      port: this.clinkConfig.port,
      servername: this.clinkConfig.serverName,
      oqs: this.clinkConfig.oqs,
      deviceCode: shorten(this.clinkConfig.deviceCode, 60),
    });

    line(
      "[通道]",
      `${this.session.label} 正在建立桌面通道，保持 ${Math.ceil(
        this.runner.config.clinkHoldMs / 1000
      )} 秒`
    );

    try {
      const main = await this.openChannel("MAIN");
      const mainInit = await main.waitForType(
        CLINK_MESSAGE_TYPES.MAIN_INIT,
        this.runner.config.enterWaitMs
      );
      if (mainInit.payload.length >= 4) {
        this.sessionId = mainInit.payload.readUInt32LE(0);
      }
      this.debug(`clink main init ${this.session.label}`, {
        sessionId: this.sessionId,
      });

      main.sendMessage(
        CLINK_MESSAGE_TYPES.MOUSE_MODE_REQUEST,
        buildMouseModeRequestPayload()
      );
      main.sendMessage(
        CLINK_MESSAGE_TYPES.CLIENT2SERVER_CUSTOM,
        buildCustomJsonPayload(this.clinkConfig.userName, this.clinkConfig.userId)
      );
      main.sendMessage(
        CLINK_MESSAGE_TYPES.CLIENT_LOGIN_INFO,
        buildLoginInfoPayload(this.clinkConfig)
      );

      const display = await this.openChannel("DISPLAY");
      display.sendMessage(
        CLINK_MESSAGE_TYPES.DISPLAY_BOOTSTRAP,
        CLINK_DISPLAY_BOOTSTRAP_PAYLOAD
      );
      display.sendMessage(
        CLINK_MESSAGE_TYPES.DISPLAY_INIT,
        CLINK_DISPLAY_INIT_PAYLOAD
      );
      display.sendMessage(CLINK_MESSAGE_TYPES.DISPLAY_RESUME);

      await Promise.all([
        this.openChannel("INPUTS"),
        this.openChannel("CURSOR"),
      ]);

      const optionalResults = await Promise.allSettled([
        this.openChannel("PLAYBACK"),
        this.openChannel("RECORD"),
        this.openChannel("PORT0"),
        this.openChannel("PORT1"),
        this.openChannel("DATA"),
      ]);

      optionalResults.forEach((item, index) => {
        if (item.status === "rejected") {
          const key = ["PLAYBACK", "RECORD", "PORT0", "PORT1", "DATA"][index];
          this.debug(
            `clink 可选通道失败 ${this.session.label}/${key}`,
            buildErrorMessage(item.reason)
          );
        }
      });

      await sleep(this.runner.config.clinkHoldMs);
    } finally {
      this.closeAll();
    }
  }

  getLinkSessionId(spec) {
    return spec.key === "MAIN" ? 0 : this.sessionId;
  }

  async openChannel(key) {
    const spec = CLINK_CHANNEL_SPECS.find((item) => item.key === key);
    if (!spec) {
      throw new Error(`未知 clink 通道: ${key}`);
    }

    try {
      const channel = new ClinkChannel(this, spec);
      this.channels.push(channel);
      await channel.connect();
      this.debug(`clink ready ${channel.label}`, {
        url: channel.url,
      });
      return channel;
    } catch (error) {
      if (spec.required) {
        throw error;
      }
      throw error;
    }
  }

  closeAll() {
    this.channels
      .slice()
      .reverse()
      .forEach((channel) => channel.close());
  }
}

class CtyunPhoneKeepAliveRunner {
  constructor(account, config) {
    this.account = account;
    this.config = config;
    this.maskedAccount = maskAccount(account.username);
    const cachedDeviceCode = getCachedDeviceCode(config.state, account.username);
    this.deviceCode = cachedDeviceCode || buildDeviceCode(account.username);
    this.deviceCodeSource = cachedDeviceCode ? "cache" : "derived";
    this.auth = getCachedAuth(config.state, account.username);
    this.authSource = this.auth ? "cache" : "password";
    this.requestCounter = 0;
  }

  persistDeviceCode() {
    const previous = getCachedDeviceCode(this.config.state, this.account.username);
    setCachedDeviceCode(this.config.state, this.account.username, this.deviceCode);
    if (previous !== this.deviceCode) {
      writeJson(this.config.stateFile, this.config.state);
      line("[设备号]", `${this.maskedAccount} 已写入缓存`);
    }
  }

  persistAuth() {
    setCachedAuth(this.config.state, this.account.username, this.auth);
    writeJson(this.config.stateFile, this.config.state);
  }

  clearAuthCache() {
    const previous = getCachedAuth(this.config.state, this.account.username);
    clearCachedAuth(this.config.state, this.account.username);
    if (previous) {
      writeJson(this.config.stateFile, this.config.state);
    }
  }

  canRetryCachedAuthProbe(error) {
    return (
      error instanceof ApiError &&
      error.auth === true &&
      error.method === "GET" &&
      error.pathname === "/api/desktop/client/list"
    );
  }

  async retryWithFreshLogin(action, probeError) {
    line("[登录]", `${this.maskedAccount} 缓存凭证探测失败，尝试账密登录校验`);
    this.auth = null;
    this.authSource = "password";
    this.clearAuthCache();
    await this.login();

    try {
      const result = await action();
      line("[登录]", `${this.maskedAccount} 缓存凭证已失效，已切换到账密登录`);
      return result;
    } catch (retryError) {
      const originalText = buildErrorMessage(probeError);
      const retryText = buildErrorMessage(retryError);
      throw new Error(
        `${this.maskedAccount} 缓存探测失败后已重登，但请求仍失败。` +
          ` 首次错误: ${originalText}; 重登后错误: ${retryText}`
      );
    }
  }

  nextRequestMeta() {
    const timestampMs = Date.now();
    const requestId = timestampMs + ++this.requestCounter;
    return { timestampMs, requestId };
  }

  buildBaseHeaders(extraHeaders = {}) {
    const { timestampMs, requestId } = this.nextRequestMeta();
    return {
      "CTG-DEVICECODE": this.deviceCode,
      "CTG-DEVICETYPE": String(this.config.deviceType),
      "CTG-REQUESTID": String(requestId),
      "CTG-TIMESTAMP": String(timestampMs),
      "CTG-VERSION": String(this.config.version),
      "CTG-APPMODEL": String(this.config.appModel),
      Origin: "https://pc.ctyun.cn",
      Referer: "https://pc.ctyun.cn/",
      "User-Agent": this.config.userAgent,
      ...extraHeaders,
    };
  }

  buildAuthHeaders(extraHeaders = {}) {
    if (!this.auth) {
      throw new Error("鉴权信息不存在，请先登录。");
    }

    const { timestampMs, requestId } = this.nextRequestMeta();
    return {
      "CTG-DEVICECODE": this.deviceCode,
      "CTG-DEVICETYPE": String(this.config.deviceType),
      "CTG-REQUESTID": String(requestId),
      "CTG-TIMESTAMP": String(timestampMs),
      "CTG-VERSION": String(this.config.version),
      "CTG-APPMODEL": String(this.config.appModel),
      "CTG-TENANTID": String(this.auth.tenantId),
      "CTG-USERID": String(this.auth.userId),
      "CTG-SIGNATURESTR": md5Upper(
        `${this.config.deviceType}${requestId}${this.auth.tenantId}` +
          `${timestampMs}${this.auth.userId}${this.config.version}${this.auth.secretKey}`
      ),
      Origin: "https://pc.ctyun.cn",
      Referer: "https://pc.ctyun.cn/",
      "User-Agent": this.config.userAgent,
      ...extraHeaders,
    };
  }

  async fetchJson(method, pathname, options = {}) {
    const {
      auth = false,
      params = null,
      body = undefined,
      contentType = "",
      extraHeaders = {},
      retryOnAuthExpired = true,
    } = options;

    let attempt = 0;
    while (true) {
      try {
        const url = new URL(pathname, API_HOST);
        const search = buildQueryString(params);
        url.search = search.toString();

        const headers = auth
          ? this.buildAuthHeaders(contentType ? { "Content-Type": contentType, ...extraHeaders } : extraHeaders)
          : this.buildBaseHeaders(contentType ? { "Content-Type": contentType, ...extraHeaders } : extraHeaders);

        let payload;
        if (body !== undefined) {
          if (contentType === "application/x-www-form-urlencoded") {
            payload = body instanceof URLSearchParams ? body.toString() : buildFormBody(body).toString();
          } else if (contentType === "application/json") {
            payload = JSON.stringify(body);
          } else {
            payload = body;
          }
        }

        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);

        let response;
        try {
          response = await fetch(url, {
            method,
            headers,
            body: payload,
            signal: controller.signal,
          });
        } finally {
          clearTimeout(timer);
        }

        const raw = await response.text();
        let json;
        try {
          json = JSON.parse(raw);
        } catch (error) {
          throw new Error(`${method} ${pathname} 返回了非 JSON 数据: ${shorten(raw, 200)}`);
        }

        if (Number(json.code) === 0) {
          return json.data;
        }

        if (
          auth &&
          retryOnAuthExpired &&
          Number(json.code) === API_CODES.NO_PERMISSIONS
        ) {
          line("[登录]", `${this.maskedAccount} 鉴权失效，自动重新登录`);
          this.clearAuthCache();
          this.auth = null;
          this.authSource = "password";
          await this.login(true);
          return this.fetchJson(method, pathname, {
            auth,
            params,
            body,
            contentType,
            extraHeaders,
            retryOnAuthExpired: false,
          });
        }

        throw new ApiError(
          `${method} ${pathname} 失败: ${json.msg || "unknown error"}`,
          json.code,
          json,
          { method, pathname, auth }
        );
      } catch (error) {
        if (error instanceof ApiError) {
          throw error;
        }

        attempt += 1;
        if (attempt > this.config.networkRetryCount) {
          throw error;
        }

        await sleep(this.config.networkRetryDelayMs * attempt);
      }
    }
  }

  async fetchBinary(pathname, params, extraHeaders = {}) {
    let attempt = 0;
    while (true) {
      try {
        const url = new URL(pathname, API_HOST);
        url.search = buildQueryString(params).toString();

        const headers = this.buildBaseHeaders(extraHeaders);
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);

        let response;
        try {
          response = await fetch(url, {
            method: "GET",
            headers,
            signal: controller.signal,
          });
        } finally {
          clearTimeout(timer);
        }

        const contentType = String(response.headers.get("content-type") || "").toLowerCase();
        if (contentType.includes("application/json")) {
          const raw = await response.text();
          let json;
          try {
            json = JSON.parse(raw);
          } catch (error) {
            throw new Error(`验证码接口返回异常: ${shorten(raw, 200)}`);
          }
          throw new ApiError(
            `验证码接口失败: ${json.msg || "unknown error"}`,
            json.code,
            json,
            { method: "GET", pathname, auth: false }
          );
        }

        if (!response.ok) {
          const raw = await response.text();
          throw new Error(`验证码接口 HTTP ${response.status}: ${shorten(raw, 200)}`);
        }

        return Buffer.from(await response.arrayBuffer());
      } catch (error) {
        if (error instanceof ApiError) {
          throw error;
        }

        attempt += 1;
        if (attempt > this.config.networkRetryCount) {
          throw error;
        }

        await sleep(this.config.networkRetryDelayMs * attempt);
      }
    }
  }

  async postOcr(imageBuffer) {
    const url = `${normalizeBaseUrl(this.config.ocrServer)}/classification`;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);

    let response;
    try {
      response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          image: imageBuffer.toString("base64"),
        }),
        signal: controller.signal,
      });
    } finally {
      clearTimeout(timer);
    }

    const raw = await response.text();
    let data = null;
    try {
      data = JSON.parse(raw);
    } catch (error) {
      data = raw;
    }

    if (typeof data === "string") {
      const result = data.replace(/\s+/g, "").trim();
      if (!result) {
        throw new Error("OCR 接口返回为空。");
      }
      return result;
    }

    const result = String(
      data.result ?? data.data ?? data.text ?? data.codeResult ?? ""
    )
      .replace(/\s+/g, "")
      .trim();

    if (!response.ok) {
      throw new Error(`OCR 接口 HTTP ${response.status}: ${shorten(raw, 200)}`);
    }
    if (!result) {
      throw new Error(`OCR 识别结果为空: ${shorten(raw, 200)}`);
    }

    return result;
  }

  async getChallengeData() {
    try {
      return await this.fetchJson("POST", "/api/auth/client/genChallengeData");
    } catch (error) {
      return null;
    }
  }

  buildLoginPayload(captchaCode) {
    return this.getChallengeData().then((challenge) => {
      const challengeId = String(
        challenge?.challengeId ?? challenge?.id ?? ""
      ).trim();
      const challengeCode = String(
        challenge?.challengeCode ?? challenge?.code ?? challenge?.challenge ?? ""
      ).trim();

      const plainSha256 = sha256(this.account.password);
      const payload = {
        userAccount: this.account.username,
        password: challengeCode
          ? sha256(`${this.account.password}${challengeCode}`)
          : plainSha256,
        sha256Password: challengeCode
          ? sha256(`${plainSha256}${challengeCode}`)
          : plainSha256,
        challengeId,
        deviceCode: this.deviceCode,
        deviceName: this.config.deviceName,
        deviceType: String(this.config.deviceType),
        deviceModel: this.config.deviceModel,
        appVersion: this.config.appVersion,
        sysVersion: this.config.sysVersion,
        clientVersion: String(this.config.version),
      };

      if (captchaCode) {
        payload.captchaCode = captchaCode;
      }

      return payload;
    });
  }

  async fetchCaptchaImage() {
    const commonParams = {
      width: 200,
      height: 80,
      mode: "auto",
      _t: Date.now(),
    };

    try {
      return await this.fetchBinary("/api/auth/client/captcha", {
        ...commonParams,
        userInfo: this.account.username,
      });
    } catch (error) {
      return this.fetchBinary("/api/auth/client/validateCode/captcha", {
        ...commonParams,
        userAccount: this.account.username,
      });
    }
  }

  async solveCaptcha(round) {
    line(
      "[验证码]",
      `${this.maskedAccount} 第 ${round} 次获取图形码并调用 OCR`
    );
    const imageBuffer = await this.fetchCaptchaImage();
    const captchaCode = await this.postOcr(imageBuffer);
    line(
      "[验证码]",
      `${this.maskedAccount} OCR 完成，识别长度 ${captchaCode.length}`
    );
    return captchaCode;
  }

  async login(silent = false) {
    let captchaCode = "";
    let lastError = null;

    for (let round = 0; round <= this.config.maxCaptchaRetries; round += 1) {
      try {
        const payload = await this.buildLoginPayload(captchaCode);
        const data = await this.fetchJson("POST", "/api/auth/client/login", {
          body: payload,
          contentType: "application/x-www-form-urlencoded",
        });

        this.auth = {
          tenantId: data.tenantId,
          userId: data.userId,
          secretKey: data.secretKey,
          userAccount: data.userAccount,
          userName: data.userName,
          bondedDevice: data.bondedDevice,
          timestamp: data.timestamp,
        };
        this.authSource = "password";

        this.persistDeviceCode();
        this.persistAuth();

        if (!silent) {
          line("[登录]", `${this.maskedAccount} 登录成功`);
        }
        return data;
      } catch (error) {
        lastError = error;
        if (!(error instanceof ApiError)) {
          throw error;
        }

        if (Number(error.code) === API_CODES.INVALID_PASSWORD) {
          throw new Error(`${this.maskedAccount} 账号或密码错误`);
        }

        if (Number(error.code) === API_CODES.AUTH_LOCKED) {
          throw new Error(`${this.maskedAccount} 账号已被锁定`);
        }

        if (!isCaptchaCode(error.code)) {
          throw error;
        }

        if (round >= this.config.maxCaptchaRetries) {
          break;
        }

        const nextRound = round + 1;
        line(
          "[登录]",
          `${this.maskedAccount} 触发验证码，准备第 ${nextRound} 次重试`
        );
        captchaCode = await this.solveCaptcha(nextRound);
      }
    }

    throw lastError || new Error(`${this.maskedAccount} 登录失败`);
  }

  async listDevices(retryOnAuthExpired = true) {
    const data = await this.fetchJson("GET", "/api/desktop/client/list", {
      auth: true,
      retryOnAuthExpired,
    });
    return Array.isArray(data?.desktopList) ? data.desktopList : [];
  }

  debug(label, payload) {
    if (!this.config.debug) {
      return;
    }

    let text = "";
    try {
      text = JSON.stringify(payload);
    } catch (error) {
      text = String(payload);
    }
    line("[调试]", `${label} ${shorten(text, 500)}`);
  }

  getConnectPath(device) {
    return Number(device?.connectMaster || 0) === 1
      ? "/api/desktop/client/connectMaster"
      : "/api/desktop/client/connect";
  }

  getStatusPath(device) {
    return Number(device?.connectMaster || 0) === 1
      ? "/api/desktop/client/statusMaster"
      : "/api/desktop/client/status";
  }

  buildDesktopHeaders(session, extraHeaders = {}) {
    const token = String(session?.token || "").trim();
    if (!token) {
      return extraHeaders;
    }
    return {
      [DESKTOP_TOKEN_HEADER]: token,
      ...extraHeaders,
    };
  }

  summarizeConnection(data) {
    const desktopInfo =
      data && typeof data.desktopInfo === "object" && data.desktopInfo ? data.desktopInfo : {};
    const desktopId = String(data?.desktopId ?? desktopInfo.desktopId ?? "").trim();
    const token = String(desktopInfo.token || "").trim();
    const internalIp = String(desktopInfo.internalIp || "").trim();
    const internalPort = String(desktopInfo.internalPort ?? "").trim();
    const clientCert = String(desktopInfo.clientCert || "").trim();
    const clientKey = String(desktopInfo.clientKey || "").trim();
    const caCert = String(desktopInfo.caCert || "").trim();

    return {
      desktopId,
      token,
      internalIp,
      internalPort,
      clientCert,
      clientKey,
      caCert,
      tokenReady: Boolean(token),
      transportReady: Boolean(internalIp && internalPort),
      certReady: Boolean(clientCert && clientKey && caCert),
      ready: Boolean(desktopId && token && internalIp && internalPort),
      goingRetry: Boolean(data?.goingRetry),
      preemption: Boolean(data?.preemption),
      fromCache: Boolean(data?.fromCache),
    };
  }

  snapshotConnection(data) {
    const summary = this.summarizeConnection(data);
    return {
      desktopId: summary.desktopId,
      tokenReady: summary.tokenReady,
      transportReady: summary.transportReady,
      certReady: summary.certReady,
      ready: summary.ready,
      goingRetry: summary.goingRetry,
      preemption: summary.preemption,
      fromCache: summary.fromCache,
      desktopInfoKeys: Object.keys(data?.desktopInfo || {}),
      topLevelKeys: Object.keys(data || {}),
    };
  }

  updateSession(session, data, source) {
    if (!data || typeof data !== "object") {
      return;
    }

    const summary = this.summarizeConnection(data);
    if (summary.desktopId) {
      session.desktopId = summary.desktopId;
    }
    if (summary.token) {
      session.token = summary.token;
    }
    if (summary.ready) {
      session.lastReadyData = data;
    }
    this.debug(`${source} ${session.label}`, this.snapshotConnection(data));
  }

  buildDesktopStatePayload(desktopId) {
    const numericId = Number(desktopId);
    if (Number.isFinite(numericId)) {
      return [numericId];
    }
    return [String(desktopId)];
  }

  async warmupDevice(device) {
    const params = {
      objId: String(device.objId),
      objType: String(device.objType),
    };
    const tasks = [
      this.fetchJson("GET", "/api/desktop/client/feature", {
        auth: true,
        params,
      }),
      this.fetchJson("GET", "/api/desktop/client/getDesktopExtraInfo", {
        auth: true,
        params,
      }),
    ];

    const results = await Promise.allSettled(tasks);
    results.forEach((item, index) => {
      if (item.status === "rejected") {
        const api = index === 0 ? "feature" : "getDesktopExtraInfo";
        this.debug(`预热 ${api} 失败`, buildErrorMessage(item.reason));
      }
    });
  }

  buildConnectPayload(device) {
    return {
      objId: String(device.objId),
      objType: String(device.objType),
      osType: String(this.config.osType),
      deviceId: String(this.config.deviceType),
      deviceCode: this.deviceCode,
      deviceName: this.config.deviceName,
      sysVersion: this.config.sysVersion,
      appVersion: this.config.appVersion,
      hostName: this.config.deviceName,
      vdCommand: "",
      ipAddress: "",
      macAddress: "",
      hardwareFeatureCode: this.deviceCode,
      specifiedCertCategory: "1",
    };
  }

  async connectDevice(device) {
    return this.fetchJson("POST", this.getConnectPath(device), {
      auth: true,
      body: this.buildConnectPayload(device),
      contentType: "application/x-www-form-urlencoded",
    });
  }

  async getDesktopStatus(session) {
    return this.fetchJson("GET", this.getStatusPath(session.device), {
      auth: true,
      params: {
        desktopId: String(session.desktopId),
        specifiedCertCategory: 1,
      },
    });
  }

  async getDesktopState(session) {
    return this.fetchJson("POST", "/api/desktop/client/state", {
      auth: true,
      body: this.buildDesktopStatePayload(session.desktopId),
      contentType: "application/json",
    });
  }

  async getDesktopStrategy(session) {
    const desktopIdNumber = Number(session.desktopId);
    return this.fetchJson("GET", "/api/desktop/client/strategy/desktopBasicInfo", {
      auth: true,
      params: {
        desktopId: Number.isFinite(desktopIdNumber) ? desktopIdNumber : session.desktopId,
      },
      extraHeaders: this.buildDesktopHeaders(session),
    });
  }

  deviceLabel(device) {
    const name = String(device.objName || device.desktopName || "未命名设备");
    const id =
      device.desktopCode || device.desktopId || device.objId || device.id || "unknown";
    return `${name}(${id})`;
  }

  async touchDesktopState(session, stage) {
    if (!session.desktopId) {
      return false;
    }

    try {
      const data = await this.getDesktopState(session);
      this.debug(`state ${stage} ${session.label}`, {
        type: typeof data,
        keys: Object.keys(data || {}),
      });
      return true;
    } catch (error) {
      this.debug(`state ${stage} ${session.label} 失败`, buildErrorMessage(error));
      return false;
    }
  }

  async waitUntilReady(session, initialData = null) {
    const deadline = Date.now() + this.config.bootWaitMs;
    let attempt = 0;
    let lastError = null;

    while (Date.now() <= deadline) {
      if (attempt > 0) {
        await sleep(this.config.statusPollIntervalMs);
      }
      attempt += 1;

      try {
        const data =
          attempt === 1 && initialData ? initialData : await this.getDesktopStatus(session);
        this.updateSession(session, data, attempt === 1 && initialData ? "connect" : "status");
        if (this.summarizeConnection(data).ready) {
          return data;
        }

        if (attempt === 1 || attempt % 5 === 0) {
          line("[等待]", `${session.label} 仍在进入云手机，已轮询 ${attempt} 次`);
        }
      } catch (error) {
        lastError = error;
      }
    }

    throw new Error(
      `${session.label} 启动等待超时${lastError ? `: ${buildErrorMessage(lastError)}` : ""}`
    );
  }

  async finishDesktopEntry(session) {
    const deadline = Date.now() + this.config.enterWaitMs;
    let nextStateAt = 0;
    let strategyReadyAt = 0;
    let stateOk = false;
    let strategyOk = false;
    let lastError = null;
    let attempt = 0;

    while (Date.now() <= deadline) {
      if (attempt > 0) {
        await sleep(this.config.statusPollIntervalMs);
      }
      attempt += 1;

      if (!strategyOk && Date.now() >= nextStateAt) {
        stateOk = (await this.touchDesktopState(session, `第${attempt}轮`)) || stateOk;
        nextStateAt = Date.now() + this.config.stateRefreshIntervalMs;
      }

      if (!strategyOk) {
        try {
          const strategy = await this.getDesktopStrategy(session);
          strategyOk = true;
          strategyReadyAt = Date.now();
          this.debug(`strategy ${session.label}`, {
            keys: Object.keys(strategy || {}),
          });
          line("[进入]", `${session.label} 已完成进入云手机`);
        } catch (error) {
          lastError = error;
          this.debug(`strategy ${session.label} 失败`, buildErrorMessage(error));
        }
      }

      if (!strategyOk) {
        try {
          const data = await this.getDesktopStatus(session);
          this.updateSession(session, data, "status-enter");
          if (!this.summarizeConnection(data).ready) {
            line("[进入]", `${session.label} 桌面仍在加载，继续等待`);
          }
        } catch (error) {
          lastError = error;
          this.debug(`status-enter ${session.label} 失败`, buildErrorMessage(error));
        }
      }

      if (
        strategyOk &&
        Date.now() - strategyReadyAt >= this.config.postEnterHoldMs
      ) {
        return;
      }
    }

    const extra = lastError ? `: ${buildErrorMessage(lastError)}` : "";
    if (!strategyOk) {
      throw new Error(`${session.label} 未完成桌面策略加载${extra}`);
    }
    if (!stateOk) {
      line("[进入]", `${session.label} state 上报未成功，按已进入桌面继续返回`);
    }
  }

  buildClinkConfig(session) {
    const readyData =
      session.lastReadyData && typeof session.lastReadyData === "object"
        ? session.lastReadyData
        : {};
    const desktopInfo =
      readyData.desktopInfo && typeof readyData.desktopInfo === "object"
        ? readyData.desktopInfo
        : {};

    const desktopId = String(
      session.desktopId || readyData.desktopId || desktopInfo.desktopId || ""
    ).trim();
    const token = String(session.token || desktopInfo.token || "").trim();
    const internalIp = String(desktopInfo.internalIp || "").trim();
    const internalPort = String(desktopInfo.internalPort || "").trim();
    const clientCert = String(desktopInfo.clientCert || "").trim();
    const clientKey = String(desktopInfo.clientKey || "").trim();
    const caCert = String(desktopInfo.caCert || "").trim();

    if (!desktopId || !token) {
      throw new Error(`${session.label} clink 缺少 desktopId 或 token`);
    }
    if (!internalIp || !internalPort) {
      throw new Error(`${session.label} clink 缺少内网地址`);
    }
    if (!clientCert || !clientKey || !caCert) {
      throw new Error(`${session.label} clink 缺少证书信息`);
    }

    const endpoint = resolveClinkEndpoint(desktopId, desktopInfo, session.device);
    const tokenPayload = decodeJwtPayload(token) || {};
    const serverName = `${formatIpv6Host(internalIp)}:${internalPort}`;

    this.debug(`clink 源数据 ${session.label}`, {
      host: desktopInfo.host || "",
      port: desktopInfo.port || "",
      internalIp,
      internalPort,
      subject: desktopInfo.subject || "",
      ipAddr: desktopInfo.ipAddr || "",
      extranetIpAddr: desktopInfo.extranetIpAddr || "",
      clinkLvsOutHost: desktopInfo.clinkLvsOutHost || "",
      clinkLvsOutHostBak: desktopInfo.clinkLvsOutHostBak || "",
      clinkLvsInHost: desktopInfo.clinkLvsInHost || "",
      clinkLvsInHostBak: desktopInfo.clinkLvsInHostBak || "",
      tokenPayloadKeys: Object.keys(tokenPayload || {}),
    });

    return {
      uri: String(endpoint.uri || "").replace(/\/+$/, ""),
      host: internalIp,
      port: internalPort,
      serverName,
      desktopId: Number(tokenPayload.d1 || desktopId),
      token,
      deviceType: String(tokenPayload.ty || this.config.deviceType),
      deviceCode: String(
        tokenPayload.c || desktopInfo.deviceCode || this.deviceCode
      ).trim(),
      userAccount: String(
        this.auth?.userAccount || this.account.username
      ).trim(),
      userName: String(this.auth?.userName || this.account.username).trim(),
      userId: Number(this.auth?.userId || 0),
      internalIp,
      internalPort,
      clientCert,
      clientKey,
      caCert,
      oqs: Number(desktopInfo.desktopCertCategory || 0) === 2 ? 1 : 0,
    };
  }

  shouldRetryClinkAttach(error) {
    const text = buildErrorMessage(error);
    return /等待消息 103 超时|WebSocket|握手|鉴权确认|等待消息|已关闭|建连/i.test(text);
  }

  async refreshSessionForClink(session) {
    try {
      const status = await this.getDesktopStatus(session);
      this.updateSession(session, status, "status-clink-refresh");
      if (this.summarizeConnection(status).ready) {
        return;
      }
    } catch (error) {
      this.debug(`status-clink-refresh ${session.label} 失败`, buildErrorMessage(error));
    }

    const connect = await this.connectDevice(session.device);
    this.updateSession(session, connect, "connect-clink-refresh");
    if (!this.summarizeConnection(connect).ready) {
      await this.waitUntilReady(session, connect);
    }
  }

  async attachDesktopChannels(session) {
    const maxAttempts = Math.max(1, this.config.clinkAttachRetries + 1);
    let lastError = null;

    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      if (attempt > 1) {
        line(
          "[通道]",
          `${session.label} 第 ${attempt}/${maxAttempts} 次建立桌面通道前刷新连接信息`
        );
        await sleep(this.config.clinkRetryDelayMs);
        await this.refreshSessionForClink(session);
      }

      try {
        const clinkConfig = this.buildClinkConfig(session);
        const attacher = new ClinkDesktopAttacher(this, session, clinkConfig);
        await attacher.attach();
        return;
      } catch (error) {
        lastError = error;
        const canRetry =
          attempt < maxAttempts && this.shouldRetryClinkAttach(error);
        if (!canRetry) {
          throw error;
        }
        line(
          "[通道]",
          `${session.label} 通道建立失败，准备重试: ${shorten(buildErrorMessage(error), 120)}`
        );
      }
    }

    throw lastError || new Error(`${session.label} 通道建立失败`);
  }

  async keepAliveOne(device, index, total) {
    const label = this.deviceLabel(device);
    line("[设备]", `${index + 1}/${total} ${label}`);

    try {
      await this.warmupDevice(device);

      const session = {
        device,
        label,
        desktopId: String(device.desktopId || device.objId || "").trim(),
        token: "",
        lastReadyData: null,
      };

      const first = await this.connectDevice(device);
      this.updateSession(session, first, "connect");

      const stateAfterConnect = await this.touchDesktopState(session, "连接后");
      if (stateAfterConnect) {
        line("  ->", "已发起连接，正在等待桌面加载完成");
      } else {
        line("  ->", "已发起连接，继续等待进入桌面");
      }

      await this.waitUntilReady(session, first);
      await this.finishDesktopEntry(session);
      await this.attachDesktopChannels(session);
      line("  ->", "保活成功");
      return { ok: true, label };
    } catch (error) {
      line("  ->", `保活失败: ${shorten(buildErrorMessage(error), 150)}`);
      return { ok: false, label, error };
    }
  }

  async run() {
    line(
      "[设备号]",
      `${this.maskedAccount} 使用${this.deviceCodeSource === "cache" ? "缓存" : "固定"} deviceCode`
    );
    if (this.authSource === "cache") {
      line("[登录]", `${this.maskedAccount} 优先使用缓存凭证`);
    } else {
      line("[登录]", `${this.maskedAccount} 未命中缓存凭证，使用账密登录`);
      await this.login();
    }

    let devices;
    if (this.authSource === "cache") {
      try {
        devices = await this.listDevices(false);
        line("[登录]", `${this.maskedAccount} 缓存凭证可用`);
      } catch (error) {
        if (!this.canRetryCachedAuthProbe(error)) {
          throw error;
        }
        devices = await this.retryWithFreshLogin(() => this.listDevices(), error);
      }
    } else {
      devices = await this.listDevices();
    }

    if (devices.length === 0) {
      throw new Error(`${this.maskedAccount} 未查询到云手机`);
    }

    line("[设备]", `${this.maskedAccount} 共查询到 ${devices.length} 台云手机`);

    const results = await runWithConcurrency(
      devices,
      this.config.maxParallel,
      async (device, index) => this.keepAliveOne(device, index, devices.length)
    );

    const success = results.filter((item) => item && item.ok).length;
    const failed = results.length - success;

    line("[小结]", `${this.maskedAccount} 成功 ${success} 台，失败 ${failed} 台`);

    return {
      ok: failed === 0,
      maskedAccount: this.maskedAccount,
      total: results.length,
      success,
      failed,
      results,
    };
  }
}

function buildConfig() {
  const accountsRaw = readEnv("CTYUN_PHONE_ACCOUNTS", "CTYUN_ACCOUNTS");
  if (!accountsRaw) {
    throw new Error("请设置 CTYUN_PHONE_ACCOUNTS，格式为 账号1#密码1&账号2#密码2");
  }

  const ocrServer = readEnv("OCR_SERVER");
  if (!ocrServer) {
    throw new Error("请设置 OCR_SERVER，指向 ddddocr 服务地址。");
  }

  const stateFile = path.resolve(
    readEnv("CTYUN_STATE_FILE") || path.join(__dirname, "ctyun_state.json")
  );

  return {
    ocrServer,
    accounts: parseAccounts(accountsRaw),
    stateFile,
    state: loadState(stateFile),
    appModel: DEFAULTS.appModel,
    deviceType: DEFAULTS.deviceType,
    osType: DEFAULTS.osType,
    appVersion: DEFAULTS.appVersion,
    version: DEFAULTS.version,
    deviceName: DEFAULTS.deviceName,
    deviceModel: DEFAULTS.deviceModel,
    sysVersion: DEFAULTS.sysVersion,
    userAgent: DEFAULTS.userAgent,
    requestTimeoutMs: readIntEnv("CTYUN_REQUEST_TIMEOUT_MS", DEFAULTS.requestTimeoutMs),
    networkRetryCount: readIntEnv("CTYUN_NETWORK_RETRY_COUNT", DEFAULTS.networkRetryCount),
    networkRetryDelayMs: readIntEnv(
      "CTYUN_NETWORK_RETRY_DELAY_MS",
      DEFAULTS.networkRetryDelayMs
    ),
    maxParallel: readIntEnv("CTYUN_MAX_PARALLEL", DEFAULTS.maxParallel),
    statusPollIntervalMs: readIntEnv(
      "CTYUN_STATUS_POLL_INTERVAL_MS",
      DEFAULTS.statusPollIntervalMs
    ),
    bootWaitMs: readIntEnv("CTYUN_BOOT_WAIT_MS", DEFAULTS.bootWaitMs),
    enterWaitMs: readIntEnv("CTYUN_ENTER_WAIT_MS", DEFAULTS.enterWaitMs),
    postEnterHoldMs: readIntEnv(
      "CTYUN_POST_ENTER_HOLD_MS",
      DEFAULTS.postEnterHoldMs
    ),
    clinkHoldMs: readIntEnv("CTYUN_CLINK_HOLD_MS", DEFAULTS.clinkHoldMs),
    clinkAttachRetries: readIntEnv(
      "CTYUN_CLINK_ATTACH_RETRIES",
      DEFAULTS.clinkAttachRetries
    ),
    clinkRetryDelayMs: readIntEnv(
      "CTYUN_CLINK_RETRY_DELAY_MS",
      DEFAULTS.clinkRetryDelayMs
    ),
    stateRefreshIntervalMs: readIntEnv(
      "CTYUN_STATE_REFRESH_INTERVAL_MS",
      DEFAULTS.stateRefreshIntervalMs
    ),
    maxCaptchaRetries: readIntEnv(
      "CTYUN_MAX_CAPTCHA_RETRIES",
      DEFAULTS.maxCaptchaRetries
    ),
    debug: readBoolEnv("CTYUN_DEBUG", DEFAULTS.debug),
  };
}

async function main() {
  const startedAt = nowText();
  const config = buildConfig();

  section("天翼云手机账密保活");
  line("[开始]", startedAt);
  line("[账号]", `共 ${config.accounts.length} 个账号`);
  line("[并发]", `单账号同时保活 ${config.maxParallel} 台`);

  const summaries = [];

  for (let index = 0; index < config.accounts.length; index += 1) {
    const account = config.accounts[index];
    const masked = maskAccount(account.username);

    section(`账号 ${index + 1}/${config.accounts.length}  ${masked}`);

    try {
      const runner = new CtyunPhoneKeepAliveRunner(account, config);
      const summary = await runner.run();
      summaries.push(summary);
    } catch (error) {
      line("[小结]", `${masked} 执行失败: ${shorten(buildErrorMessage(error), 150)}`);
      summaries.push({
        ok: false,
        maskedAccount: masked,
        total: 0,
        success: 0,
        failed: 0,
        error,
      });
    }
  }

  const totalAccounts = summaries.length;
  const okAccounts = summaries.filter((item) => item.ok).length;
  const totalDevices = summaries.reduce((sum, item) => sum + (item.total || 0), 0);
  const successDevices = summaries.reduce((sum, item) => sum + (item.success || 0), 0);
  const failedDevices = summaries.reduce((sum, item) => sum + (item.failed || 0), 0);

  section("执行汇总");
  line("[结束]", nowText());
  line("[账号]", `成功 ${okAccounts} 个，失败 ${totalAccounts - okAccounts} 个，共 ${totalAccounts} 个`);
  line("[设备]", `成功 ${successDevices} 台，失败 ${failedDevices} 台，共 ${totalDevices} 台`);

  const failedAccounts = summaries.filter((item) => !item.ok);
  if (failedAccounts.length > 0) {
    line("[异常]", "以下账号未全部成功:");
    for (const item of failedAccounts) {
      const reason = item.error ? shorten(buildErrorMessage(item.error), 120) : "存在失败设备";
      line("  -", `${item.maskedAccount} -> ${reason}`);
    }
    process.exitCode = 1;
    return;
  }

  line("[结果]", "全部账号保活完成");
}

main().catch((error) => {
  section("执行失败");
  line("[时间]", nowText());
  line("[原因]", buildErrorMessage(error));
  process.exit(1);
});
