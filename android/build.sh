#!/bin/bash
# 手动打包（不用 Gradle）：javac → d8 → aapt → zipalign → apksigner
set -e
cd "$(dirname "$0")"

AJ=/usr/lib/android-sdk/platforms/android-33/android.jar
R8=tools/r8.jar
KS=keystore.jks
OUT=build

rm -rf "$OUT" gen classes
mkdir -p gen classes "$OUT/dex"

echo "[1/6] 复制网页资源"
# 本地开发目录是 ../app.html，仓库里是 ../web/app.html
if [ -f ../app.html ]; then SRC=../app.html; else SRC=../web/app.html; fi
mkdir -p assets && cp "$SRC" assets/app.html

echo "[2/6] 生成 R.java"
aapt package -f -m -J gen -M AndroidManifest.xml -S res -I "$AJ"

echo "[3/6] 编译 Java"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d classes \
  $(find src gen -name '*.java')

echo "[4/6] 转 dex"
java -cp "$R8" com.android.tools.r8.D8 --release --min-api 21 --lib "$AJ" --output "$OUT/dex" \
  $(find classes -name '*.class')

echo "[5/6] 组装 APK"
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$AJ" -F "$OUT/app-unsigned.apk"
(cd "$OUT/dex" && aapt add "../app-unsigned.apk" classes.dex >/dev/null)
zipalign -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "[6/6] 签名"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias lifelog -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass lifelog2026 -keypass lifelog2026 \
    -dname "CN=LifeLog, O=Personal, C=CN" >/dev/null 2>&1
fi
APK="$OUT/lifelog.apk"
apksigner sign --ks "$KS" --ks-key-alias lifelog \
  --ks-pass pass:lifelog2026 --key-pass pass:lifelog2026 \
  --out "$APK" "$OUT/app-aligned.apk"
apksigner verify --print-certs "$APK" | head -3
ls -lh "$APK"
python3 -c "import shutil;shutil.copy(\"$APK\",\"$OUT/今日待办.apk\")"
echo "完成: $(pwd)/$APK"
