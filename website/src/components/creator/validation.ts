/**
 * Client-side validation of the editor state against the catalog: required
 * params, numeric ranges, enum membership, plus enchant-level sanity. Mirrors
 * (loosely) what the plugin compiler would reject, so an emitted SE1 code is
 * very likely to import clean.
 */

import type {EnchantState, CatalogParam} from './types';
import {findEffect, findSelector} from './catalog';
import {isValidKey} from '@site/src/lib/se-codec';

/** True if the string is an integer (no decimal point, parseable). */
function isInt(raw: string): boolean {
  return /^[-+]?\d+$/.test(raw.trim());
}

export interface Issue {
  /** dotted path for grouping, e.g. "level.1.effect.2.param.amount" */
  path: string;
  message: string;
}

function checkParam(
  param: CatalogParam,
  rawValue: string | undefined,
  path: string,
  out: Issue[],
): void {
  const raw = (rawValue ?? '').trim();
  if (raw === '') {
    if (param.required && (param.default === null || param.default === '')) {
      out.push({path, message: `"${param.name}" is required.`});
    }
    return; // empty optional -> default applies
  }
  switch (param.kind) {
    case 'INT':
    case 'TICKS': {
      const n = Number(raw);
      if (!Number.isFinite(n) || !isInt(raw)) {
        out.push({path, message: `"${param.name}" must be a whole number.`});
        return;
      }
      rangeCheck(param, n, path, out);
      break;
    }
    case 'DOUBLE': {
      const n = Number(raw);
      if (!Number.isFinite(n)) {
        out.push({path, message: `"${param.name}" must be a number.`});
        return;
      }
      rangeCheck(param, n, path, out);
      break;
    }
    case 'ENUM': {
      if (param.enum.length && !param.enum.includes(raw)) {
        out.push({
          path,
          message: `"${param.name}" must be one of: ${param.enum.join(', ')}.`,
        });
      }
      break;
    }
    case 'BOOL': {
      if (raw !== 'true' && raw !== 'false') {
        out.push({path, message: `"${param.name}" must be true or false.`});
      }
      break;
    }
    // HANDLE / STRING: free-form; the server resolves handles, so we only
    // require non-empty (handled above).
    default:
      break;
  }
}

function rangeCheck(
  param: CatalogParam,
  n: number,
  path: string,
  out: Issue[],
): void {
  if (param.min !== null && n < param.min) {
    out.push({path, message: `"${param.name}" must be >= ${param.min}.`});
  }
  if (param.max !== null && n > param.max) {
    out.push({path, message: `"${param.name}" must be <= ${param.max}.`});
  }
}

/** Validate the whole enchant; an empty array means "ready to emit". */
export function validate(state: EnchantState): Issue[] {
  const out: Issue[] = [];

  if (state.display.trim() === '') {
    out.push({path: 'meta.display', message: 'Display name is required.'});
  }
  if (!isValidKey(state.key)) {
    out.push({
      path: 'meta.key',
      message: 'Key must be lowercase letters, digits, or hyphens.',
    });
  }
  if (state.appliesTo.length === 0) {
    out.push({
      path: 'meta.appliesTo',
      message: 'Pick at least one item category for applies-to.',
    });
  }
  if (state.levels.length === 0) {
    out.push({path: 'meta.levels', message: 'Add at least one level.'});
  }

  state.levels.forEach((level, li) => {
    const lvBase = `level.${li}`;
    const chance = Number(level.chance);
    if (level.chance.trim() === '' || !Number.isFinite(chance)) {
      out.push({path: `${lvBase}.chance`, message: 'Chance must be a number.'});
    } else if (chance < 0 || chance > 100) {
      out.push({
        path: `${lvBase}.chance`,
        message: 'Chance must be between 0 and 100.',
      });
    }
    if (level.cooldown.trim() !== '') {
      const cd = Number(level.cooldown);
      if (!Number.isFinite(cd) || cd < 0 || !isInt(level.cooldown)) {
        out.push({
          path: `${lvBase}.cooldown`,
          message: 'Cooldown must be a non-negative whole number of seconds.',
        });
      }
    }
    if (level.souls.trim() !== '') {
      const s = Number(level.souls);
      if (!Number.isFinite(s) || s < 0 || !isInt(level.souls)) {
        out.push({
          path: `${lvBase}.souls`,
          message: 'Souls must be a non-negative whole number.',
        });
      }
    }
    if (level.effects.length === 0) {
      out.push({
        path: `${lvBase}.effects`,
        message: 'Each level needs at least one effect.',
      });
    }

    level.effects.forEach((effect, ei) => {
      const effBase = `${lvBase}.effect.${ei}`;
      const def = findEffect(effect.head);
      if (!def) {
        out.push({
          path: effBase,
          message: `Unknown effect "${effect.head}".`,
        });
        return;
      }
      for (const p of def.params) {
        checkParam(p, effect.params[p.name], `${effBase}.param.${p.name}`, out);
      }
      effect.targets.forEach((t) => {
        const sel = findSelector(t.selector);
        if (!sel) {
          out.push({
            path: `${effBase}.target.${t.slot}`,
            message: `Unknown selector "${t.selector}".`,
          });
          return;
        }
        for (const p of sel.params) {
          checkParam(
            p,
            t.params[p.name],
            `${effBase}.target.${t.slot}.${p.name}`,
            out,
          );
        }
      });
    });
  });

  return out;
}
