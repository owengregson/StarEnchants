/**
 * SE1 import-code codec — the byte-for-byte contract with `/se import` (ADR 0029).
 *
 *   SE1:<base64url-nopad( zlib-deflate( utf8( JSON.stringify(envelope) ) ) )>
 *
 * The JS encoder here MUST round-trip with the Java decoder:
 *   - compression: zlib DEFLATE with the standard zlib header + adler32
 *     (pako.deflate <-> java.util.zip.Deflater/Inflater at their defaults).
 *   - transport: url-safe base64 WITHOUT padding ('-'/'_', no '=')
 *     (Base64.getUrlEncoder().withoutPadding() / getUrlDecoder()).
 */

import pako from 'pako';

export const SE1_PREFIX = 'SE1:';

export type EnvelopeKind = 'enchant';

/** The decoded payload. `content` is the on-disk enchant def map verbatim. */
export interface Envelope {
  v: 1;
  kind: EnvelopeKind;
  key: string;
  content: Record<string, unknown>;
}

/** Thrown on any decode failure so the UI can show one friendly message. */
export class SeCodecError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SeCodecError';
  }
}

// ---------------------------------------------------------------------------
// base64url (no padding) over raw bytes. Looped, never spread into
// String.fromCharCode(...bytes) — that blows the call stack on large inputs.
// ---------------------------------------------------------------------------

const B64_ALPHABET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

// Reverse lookup: char code -> 6-bit value (255 = not a base64url char).
const B64_LOOKUP: Uint8Array = (() => {
  const table = new Uint8Array(256).fill(255);
  for (let i = 0; i < B64_ALPHABET.length; i++) {
    table[B64_ALPHABET.charCodeAt(i)] = i;
  }
  return table;
})();

function bytesToBase64Url(bytes: Uint8Array): string {
  let out = '';
  const len = bytes.length;
  let i = 0;
  for (; i + 2 < len; i += 3) {
    const n = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
    out +=
      B64_ALPHABET[(n >>> 18) & 63] +
      B64_ALPHABET[(n >>> 12) & 63] +
      B64_ALPHABET[(n >>> 6) & 63] +
      B64_ALPHABET[n & 63];
  }
  const rem = len - i;
  if (rem === 1) {
    const n = bytes[i] << 16;
    out += B64_ALPHABET[(n >>> 18) & 63] + B64_ALPHABET[(n >>> 12) & 63];
  } else if (rem === 2) {
    const n = (bytes[i] << 16) | (bytes[i + 1] << 8);
    out +=
      B64_ALPHABET[(n >>> 18) & 63] +
      B64_ALPHABET[(n >>> 12) & 63] +
      B64_ALPHABET[(n >>> 6) & 63];
  }
  return out;
}

function base64UrlToBytes(text: string): Uint8Array {
  // Tolerate accidental padding / standard-alphabet chars on input.
  const clean = text.replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const len = clean.length;
  const fullGroups = len >> 2;
  const remBytes = len & 3; // 0, 2, or 3 valid trailing chars
  if (remBytes === 1) {
    throw new SeCodecError('Import code is malformed (bad base64 length).');
  }
  const outLen = fullGroups * 3 + (remBytes === 2 ? 1 : remBytes === 3 ? 2 : 0);
  const out = new Uint8Array(outLen);

  const sextet = (ch: number): number => {
    const v = B64_LOOKUP[ch];
    if (v === 255) {
      throw new SeCodecError('Import code contains invalid characters.');
    }
    return v;
  };

  let oi = 0;
  let ci = 0;
  for (let g = 0; g < fullGroups; g++) {
    const a = sextet(clean.charCodeAt(ci++));
    const b = sextet(clean.charCodeAt(ci++));
    const c = sextet(clean.charCodeAt(ci++));
    const d = sextet(clean.charCodeAt(ci++));
    const n = (a << 18) | (b << 12) | (c << 6) | d;
    out[oi++] = (n >>> 16) & 255;
    out[oi++] = (n >>> 8) & 255;
    out[oi++] = n & 255;
  }
  if (remBytes === 2) {
    const a = sextet(clean.charCodeAt(ci++));
    const b = sextet(clean.charCodeAt(ci++));
    const n = (a << 18) | (b << 12);
    out[oi++] = (n >>> 16) & 255;
  } else if (remBytes === 3) {
    const a = sextet(clean.charCodeAt(ci++));
    const b = sextet(clean.charCodeAt(ci++));
    const c = sextet(clean.charCodeAt(ci++));
    const n = (a << 18) | (b << 12) | (c << 6);
    out[oi++] = (n >>> 16) & 255;
    out[oi++] = (n >>> 8) & 255;
  }
  return out;
}

// ---------------------------------------------------------------------------
// UTF-8 <-> bytes. Use TextEncoder/Decoder where present (browser + modern
// Node); fall back to a manual codec so the module is importable under SSR
// or bare node --test without DOM globals.
// ---------------------------------------------------------------------------

function utf8Encode(text: string): Uint8Array {
  if (typeof TextEncoder !== 'undefined') {
    return new TextEncoder().encode(text);
  }
  const bytes: number[] = [];
  for (let i = 0; i < text.length; i++) {
    let cp = text.charCodeAt(i);
    if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < text.length) {
      const lo = text.charCodeAt(i + 1);
      if (lo >= 0xdc00 && lo <= 0xdfff) {
        cp = 0x10000 + ((cp - 0xd800) << 10) + (lo - 0xdc00);
        i++;
      }
    }
    if (cp < 0x80) {
      bytes.push(cp);
    } else if (cp < 0x800) {
      bytes.push(0xc0 | (cp >> 6), 0x80 | (cp & 0x3f));
    } else if (cp < 0x10000) {
      bytes.push(0xe0 | (cp >> 12), 0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
    } else {
      bytes.push(
        0xf0 | (cp >> 18),
        0x80 | ((cp >> 12) & 0x3f),
        0x80 | ((cp >> 6) & 0x3f),
        0x80 | (cp & 0x3f),
      );
    }
  }
  return Uint8Array.from(bytes);
}

function utf8Decode(bytes: Uint8Array): string {
  if (typeof TextDecoder !== 'undefined') {
    return new TextDecoder('utf-8', {fatal: false}).decode(bytes);
  }
  let out = '';
  let i = 0;
  while (i < bytes.length) {
    const b0 = bytes[i++];
    if (b0 < 0x80) {
      out += String.fromCharCode(b0);
    } else if (b0 >= 0xc0 && b0 < 0xe0) {
      const b1 = bytes[i++] & 0x3f;
      out += String.fromCharCode(((b0 & 0x1f) << 6) | b1);
    } else if (b0 >= 0xe0 && b0 < 0xf0) {
      const b1 = bytes[i++] & 0x3f;
      const b2 = bytes[i++] & 0x3f;
      out += String.fromCharCode(((b0 & 0x0f) << 12) | (b1 << 6) | b2);
    } else {
      const b1 = bytes[i++] & 0x3f;
      const b2 = bytes[i++] & 0x3f;
      const b3 = bytes[i++] & 0x3f;
      let cp = ((b0 & 0x07) << 18) | (b1 << 12) | (b2 << 6) | b3;
      cp -= 0x10000;
      out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
    }
  }
  return out;
}

// ---------------------------------------------------------------------------
// key sanitization — must match the plugin side; a key that escapes the
// charset is rejected (no path traversal into content/enchants/<key>.yml).
// ---------------------------------------------------------------------------

/** Coerce arbitrary text into a safe content key `[a-z0-9-]+` (lossy). */
export function sanitizeKey(raw: string): string {
  return (raw ?? '')
    .toLowerCase()
    .replace(/&[0-9a-fk-or]/g, '') // strip legacy &-colour codes first
    .replace(/[^a-z0-9-]+/g, '-') // anything else -> hyphen
    .replace(/-+/g, '-') // collapse runs
    .replace(/^-+|-+$/g, ''); // trim edges
}

/** True iff `key` is already a valid, traversal-safe content key. */
export function isValidKey(key: string): boolean {
  return /^[a-z0-9-]+$/.test(key);
}

/** Derive a key from an enchant display name (strips &-codes, sanitizes). */
export function keyFromDisplay(display: string): string {
  return sanitizeKey(display);
}

// ---------------------------------------------------------------------------
// the codec
// ---------------------------------------------------------------------------

/** encode(envelope) -> "SE1:" + base64url-nopad(zlib-deflate(utf8(JSON))). */
export function encode(envelope: Envelope): string {
  const json = JSON.stringify(envelope);
  const deflated = pako.deflate(utf8Encode(json)); // zlib header + adler32
  return SE1_PREFIX + bytesToBase64Url(deflated);
}

/** decode(code) -> envelope. Throws SeCodecError on any malformed input. */
export function decode(code: string): Envelope {
  if (typeof code !== 'string') {
    throw new SeCodecError('No import code provided.');
  }
  const trimmed = code.trim();
  if (!trimmed) {
    throw new SeCodecError('No import code provided.');
  }
  if (!trimmed.startsWith(SE1_PREFIX)) {
    throw new SeCodecError(
      "Unrecognized code — expected one starting with 'SE1:'. " +
        'This may be an older or unsupported format.',
    );
  }
  const body = trimmed.slice(SE1_PREFIX.length);
  if (!body) {
    throw new SeCodecError('Import code is empty after the SE1: prefix.');
  }

  let bytes: Uint8Array;
  try {
    bytes = base64UrlToBytes(body);
  } catch (e) {
    if (e instanceof SeCodecError) throw e;
    throw new SeCodecError('Import code is not valid base64url.');
  }

  let json: string;
  try {
    json = utf8Decode(pako.inflate(bytes));
  } catch {
    throw new SeCodecError('Import code could not be decompressed (corrupt?).');
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(json);
  } catch {
    throw new SeCodecError('Import code did not contain valid data.');
  }

  return assertEnvelope(parsed);
}

function assertEnvelope(value: unknown): Envelope {
  if (typeof value !== 'object' || value === null) {
    throw new SeCodecError('Import code is not a valid StarEnchants envelope.');
  }
  const obj = value as Record<string, unknown>;
  if (obj.v !== 1) {
    throw new SeCodecError(
      `Unsupported envelope version ${String(obj.v)} (this site reads v1).`,
    );
  }
  if (obj.kind !== 'enchant') {
    throw new SeCodecError(
      `This code is a '${String(obj.kind)}' — only 'enchant' codes can be loaded here.`,
    );
  }
  if (typeof obj.key !== 'string' || !isValidKey(obj.key)) {
    throw new SeCodecError(
      'Import code has a missing or invalid key (must be [a-z0-9-]).',
    );
  }
  if (typeof obj.content !== 'object' || obj.content === null) {
    throw new SeCodecError('Import code is missing its enchant definition.');
  }
  return {
    v: 1,
    kind: 'enchant',
    key: obj.key,
    content: obj.content as Record<string, unknown>,
  };
}
