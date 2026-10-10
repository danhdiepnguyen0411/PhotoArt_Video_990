#!/usr/bin/env bash
# ==============================================================================
# Script: migrate-bundle-id.sh
# Description: Tự động hóa đổi Bundle ID (applicationId) và Namespace/Package
# Usage:
#   Level 1 (Nhanh - chỉ đổi Bundle ID):
#     ./migrate-bundle-id.sh <OLD_ID> <NEW_ID>
#   Level 2 (Toàn diện - đổi cả thư mục, namespace, code, xml, proguard):
#     ./migrate-bundle-id.sh <OLD_ID> <NEW_ID> --full-refactor
# ==============================================================================

set -euo pipefail

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <OLD_PACKAGE> <NEW_PACKAGE> [--full-refactor]"
    echo "Example Level 1: $0 com.aiart.old com.aiart.new"
    echo "Example Level 2: $0 com.aiart.old com.aiart.new --full-refactor"
    exit 1
fi

OLD_PKG="$1"
NEW_PKG="$2"
MODE="${3:-}"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$PROJECT_ROOT"

echo "=== MIGRATION BUNDLE ID TOOL ==="
echo "Project Root: $PROJECT_ROOT"
echo "Old Package : $OLD_PKG"
echo "New Package : $NEW_PKG"

# 1. Luôn cập nhật applicationId trong app/build.gradle.kts
echo "--> [1/2] Cập nhật applicationId trong app/build.gradle.kts..."
sed -i '' "s/applicationId = \"$OLD_PKG\"/applicationId = \"$NEW_PKG\"/g" app/build.gradle.kts

# 2. Cập nhật targetPackage trong shortcuts.xml (nếu có)
if [ -f "app/src/main/res/xml/shortcuts.xml" ]; then
    echo "--> [2/2] Cập nhật targetPackage trong shortcuts.xml..."
    sed -i '' "s/android:targetPackage=\"$OLD_PKG\"/android:targetPackage=\"$NEW_PKG\"/g" app/src/main/res/xml/shortcuts.xml
fi

if [ "$MODE" != "--full-refactor" ]; then
    echo ""
    echo "✅ [HOÀN TẤT LEVEL 1]: Đã cập nhật Bundle ID sang '$NEW_PKG'."
    echo "ℹ️  Cấu trúc thư mục mã nguồn được giữ nguyên để tối ưu hiệu suất và tránh conflict."
    echo "👉 Kiểm tra lại bản build bằng: ./gradlew assembleDebug"
    exit 0
fi

echo ""
echo "=== BẮT ĐẦU LEVEL 2: REFACTOR TOÀN BỘ THƯ MỤC & NAMESPACE ==="

OLD_DIR="${OLD_PKG//.//}"
NEW_DIR="${NEW_PKG//.//}"

# 3. Cập nhật namespace trong app/build.gradle.kts
echo "--> Cập nhật namespace trong app/build.gradle.kts..."
sed -i '' "s/namespace = \"$OLD_PKG\"/namespace = \"$NEW_PKG\"/g" app/build.gradle.kts

# 4. Di dời các thư mục mã nguồn qua git mv (main, debug, test)
for SOURCE_SET in "main" "debug" "test"; do
    SRC_DIR="app/src/$SOURCE_SET/java"
    if [ -d "$SRC_DIR/$OLD_DIR" ]; then
        echo "--> Di dời $SRC_DIR/$OLD_DIR -> $SRC_DIR/$NEW_DIR..."
        NEW_PARENT="$(dirname "$SRC_DIR/$NEW_DIR")"
        mkdir -p "$NEW_PARENT"
        git mv "$SRC_DIR/$OLD_DIR" "$SRC_DIR/$NEW_DIR"
        
        # Xóa các thư mục rỗng cũ
        OLD_FIRST="$(echo "$OLD_DIR" | cut -d'/' -f1)"
        if [ -d "$SRC_DIR/$OLD_FIRST" ]; then
            find "$SRC_DIR/$OLD_FIRST" -type d -empty -delete 2>/dev/null || true
            rm -rf "$SRC_DIR/$OLD_FIRST" 2>/dev/null || true
        fi
    fi
done

# 5. Thay thế chuỗi package trong code Kotlin / Java
echo "--> Thay thế package trong tất cả file Kotlin/Java..."
python3 -c "
import os
count = 0
for root, _, files in os.walk('app/src'):
    for f in files:
        if f.endswith('.kt') or f.endswith('.java'):
            fp = os.path.join(root, f)
            with open(fp, 'r', encoding='utf-8') as file:
                c = file.read()
            if '$OLD_PKG' in c:
                c = c.replace('$OLD_PKG', '$NEW_PKG')
                with open(fp, 'w', encoding='utf-8') as file:
                    file.write(c)
                count += 1
print(f'    Đã cập nhật {count} file Kotlin/Java.')
"

# 6. Thay thế trong XML layout (Custom Views)
echo "--> Thay thế trong các file XML Layout..."
python3 -c "
import os
count = 0
for root, _, files in os.walk('app/src/main/res/layout'):
    for f in files:
        if f.endswith('.xml'):
            fp = os.path.join(root, f)
            with open(fp, 'r', encoding='utf-8') as file:
                c = file.read()
            if '$OLD_PKG' in c:
                c = c.replace('$OLD_PKG', '$NEW_PKG')
                with open(fp, 'w', encoding='utf-8') as file:
                    file.write(c)
                count += 1
print(f'    Đã cập nhật {count} file XML Layout.')
"

# 7. Thay thế trong shortcuts.xml và debug manifest
if [ -f "app/src/main/res/xml/shortcuts.xml" ]; then
    sed -i '' "s/$OLD_PKG/$NEW_PKG/g" app/src/main/res/xml/shortcuts.xml
fi
if [ -f "app/src/debug/AndroidManifest.xml" ]; then
    sed -i '' "s/$OLD_PKG/$NEW_PKG/g" app/src/debug/AndroidManifest.xml
fi

# 8. Cập nhật Proguard Rules
if [ -f "app/proguard-rules.pro" ]; then
    echo "--> Cập nhật app/proguard-rules.pro..."
    sed -i '' "s/$OLD_PKG/$NEW_PKG/g" app/proguard-rules.pro
fi

# 9. Cập nhật AGENTS.md
if [ -f "AGENTS.md" ]; then
    echo "--> Cập nhật AGENTS.md..."
    sed -i '' "s/$OLD_PKG/$NEW_PKG/g" AGENTS.md
fi

echo ""
echo "✅ [HOÀN TẤT LEVEL 2]: Đã refactor toàn bộ thư mục & namespace sang '$NEW_PKG'!"
echo "👉 Bắt đầu các bước kiểm tra:"
echo "   1) ./gradlew clean"
echo "   2) ./gradlew testDebugUnitTest"
echo "   3) ./gradlew assembleDebug"
echo "   4) ./gradlew assembleRelease"
