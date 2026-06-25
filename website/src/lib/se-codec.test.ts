/**
 * SE1 codec tests. Run with Node's built-in TS type-stripping (Node >= 22.6):
 *
 *   node --test --experimental-strip-types src/lib/se-codec.test.ts
 *
 * These pin the JS side of the SE1 wire contract (ADR 0029): encode<->decode
 * round-trips, a fixed envelope survives, and malformed input is rejected.
 */

import {test} from 'node:test';
import assert from 'node:assert/strict';

// Node's --experimental-strip-types loader requires the explicit `.ts`
// extension on relative imports, but the site's tsconfig forbids it
// (allowImportingTsExtensions off). A runtime-built specifier sidesteps the
// static check; the module loads once before the tests register.
const SPECIFIER = './se-codec' + '.ts';
const mod = (await import(SPECIFIER)) as typeof import('./se-codec');
const {encode, decode, sanitizeKey, isValidKey, keyFromDisplay, SeCodecError, SE1_PREFIX} =
  mod;
type Envelope = import('./se-codec').Envelope;

const FIXED: Envelope = {
  v: 1,
  kind: 'enchant',
  key: 'frostbite',
  content: {
    tier: 'uncommon',
    display: '&bFrostbite',
    description: 'Chill and slow the enemy you strike.',
    trigger: 'ATTACK',
    'applies-to': ['SWORD', 'AXE'],
    group: 'combat',
    levels: {
      '1': {
        chance: 25,
        effects: [
          {POTION: {effect: 'SLOWNESS', level: 1, duration: 60, who: '@Victim'}},
          {PARTICLE: {particle: 'CLOUD', count: 6}},
        ],
      },
    },
  },
};

test('encode produces an SE1: url-safe, unpadded code', () => {
  const code = encode(FIXED);
  assert.ok(code.startsWith(SE1_PREFIX), 'has SE1: prefix');
  assert.match(code, /^SE1:[A-Za-z0-9_-]+$/, 'url-safe alphabet, no padding');
});

test('encode<->decode round-trips the fixed envelope byte-identically', () => {
  const code = encode(FIXED);
  const back = decode(code);
  assert.deepEqual(back, FIXED);
});

test('round-trips a large payload without stack overflow', () => {
  const big: Envelope = {
    v: 1,
    kind: 'enchant',
    key: 'big',
    content: {blob: 'x'.repeat(250_000)},
  };
  assert.deepEqual(decode(encode(big)), big);
});

test('round-trips unicode and &-color codes in content', () => {
  const env: Envelope = {
    v: 1,
    kind: 'enchant',
    key: 'unicode',
    content: {display: '&démon ❄ 火', levels: {'1': {chance: 100}}},
  };
  assert.deepEqual(decode(encode(env)), env);
});

test('decode rejects an unknown prefix', () => {
  assert.throws(() => decode('SE9:whatever'), SeCodecError);
  assert.throws(() => decode('not-a-code'), SeCodecError);
});

test('decode rejects empty / whitespace input', () => {
  assert.throws(() => decode(''), SeCodecError);
  assert.throws(() => decode('   '), SeCodecError);
});

test('decode rejects garbage after the prefix', () => {
  assert.throws(() => decode('SE1:!!!'), SeCodecError);
  assert.throws(() => decode('SE1:'), SeCodecError);
});

test('sanitizeKey strips color codes, lowercases, and hyphenates', () => {
  assert.equal(sanitizeKey('&bFrost Bite!!'), 'frost-bite');
  assert.equal(sanitizeKey('  Lucky_Strike  '), 'lucky-strike');
  assert.equal(sanitizeKey('Already-Valid-9'), 'already-valid-9');
  assert.equal(keyFromDisplay('&6Mega &lSmash'), 'mega-smash');
});

test('isValidKey accepts safe keys and rejects traversal', () => {
  assert.equal(isValidKey('frostbite'), true);
  assert.equal(isValidKey('a-b-9'), true);
  assert.equal(isValidKey('../etc'), false);
  assert.equal(isValidKey('Has Space'), false);
  assert.equal(isValidKey(''), false);
});
