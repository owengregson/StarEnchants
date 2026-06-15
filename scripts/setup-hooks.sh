#!/usr/bin/env bash
# Enable StarEnchants' shared git hooks (conventional commits + hygiene).
# Run once per clone.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

git config core.hooksPath .githooks
chmod +x .githooks/* 2>/dev/null || true

echo "✓ git hooks enabled (core.hooksPath=.githooks)"
