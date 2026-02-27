#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PKG_DIR="$ROOT_DIR/deploy/package"
OUT_DIR="$PKG_DIR/s1000d-viewer"

echo "[1/4] Building backend jar..."
(cd "$ROOT_DIR" && ./gradlew :application:bootJar -x test)

echo "[2/4] Building webapp dist..."
(cd "$ROOT_DIR/webapp" && npm ci && npm run build)

echo "[3/4] Assembling package..."
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/app" "$OUT_DIR/webapp" "$OUT_DIR/deploy"

cp "$ROOT_DIR"/application/build/libs/*.jar "$OUT_DIR/app/application.jar"
cp -r "$ROOT_DIR/webapp/dist" "$OUT_DIR/webapp/dist"
cp "$ROOT_DIR/deploy/.env.example" "$OUT_DIR/deploy/.env.example"

cat > "$OUT_DIR/start-prod.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

mkdir -p logs
nohup java -jar app/application.jar > logs/backend.log 2>&1 &
nohup npx serve -s webapp/dist -l 5173 > logs/webapp.log 2>&1 &
echo "Started backend on http://localhost:8080 and webapp on http://localhost:5173"
EOF
chmod +x "$OUT_DIR/start-prod.sh"

cat > "$OUT_DIR/start-prod.ps1" <<'EOF'
New-Item -ItemType Directory -Force logs | Out-Null
Start-Process java -ArgumentList '-jar','app/application.jar' -RedirectStandardOutput 'logs/backend.log' -RedirectStandardError 'logs/backend.err.log'
Start-Process npx -ArgumentList 'serve','-s','webapp/dist','-l','5173' -RedirectStandardOutput 'logs/webapp.log' -RedirectStandardError 'logs/webapp.err.log'
Write-Output "Started backend on http://localhost:8080 and webapp on http://localhost:5173"
EOF

echo "[4/4] Creating archive..."
rm -f "$PKG_DIR/s1000d-viewer-deploy.tar.gz"
tar -czf "$PKG_DIR/s1000d-viewer-deploy.tar.gz" -C "$PKG_DIR" s1000d-viewer

echo "Package ready:"
echo " - Folder: $OUT_DIR"
echo " - Archive: $PKG_DIR/s1000d-viewer-deploy.tar.gz"
