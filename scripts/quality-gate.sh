#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "[quality-gate] headless unit + FXML smoke suite"
mvn -q clean test

echo "[quality-gate] XML syntax for every production FXML"
for fxml in src/main/resources/fxml/*.fxml; do
  xmllint --noout "$fxml"
done

echo "[quality-gate] release package compilation"
mvn -q -DskipTests -Prelease-artifacts package

echo "[quality-gate] working-tree diff check"
git diff --check

echo "[quality-gate] PASS"
