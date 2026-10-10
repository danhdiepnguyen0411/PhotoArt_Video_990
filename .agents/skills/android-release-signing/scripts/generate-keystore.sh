#!/usr/bin/env bash
# generate-keystore.sh — Script tạo Release Keystore chuẩn Android cho các dự án CODE12
# Usage:
#   ./generate-keystore.sh <APP_ID_OR_KEY_NAME> [PASSWORD]
# Example:
#   ./generate-keystore.sh code12_893 "Code12@2026"

set -euo pipefail

KEY_NAME="${1:-}"
PASSWORD="${2:-Code12@2026}"

if [ -z "$KEY_NAME" ]; then
    echo "❌ Lỗi: Vui lòng cung cấp tên key hoặc mã app!"
    echo "👉 Ví dụ: ./generate-keystore.sh code12_893 \"Code12@2026\""
    exit 1
fi

KEYSTORE_FILE="${KEY_NAME}.jks"
ALIAS="${KEY_NAME}"
VALIDITY_DAYS=10000

if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️ Cảnh báo: File $KEYSTORE_FILE đã tồn tại!"
    read -p "Bạn có muốn ghi đè không? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Đã hủy thao tác."
        exit 0
    fi
    rm -f "$KEYSTORE_FILE"
fi

echo "🚀 Đang tạo Release Keystore: $KEYSTORE_FILE (Alias: $ALIAS)..."

keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=CODE12 Studio, OU=Mobile App Development, O=CODE12, L=Hanoi, ST=Hanoi, C=VN"

echo "✅ Đã tạo thành công file: $KEYSTORE_FILE"
echo "--------------------------------------------------------"
echo "📋 THÔNG TIN KEYSTORE:"
echo "• File: $KEYSTORE_FILE"
echo "• Alias: $ALIAS"
echo "• Password: $PASSWORD"
echo "• Validity: $VALIDITY_DAYS ngày (~28 năm)"
echo "--------------------------------------------------------"
echo "🔍 TRÍCH XUẤT FINGERPRINTS (SHA-1 / SHA-256):"
keytool -list -v -keystore "$KEYSTORE_FILE" -alias "$ALIAS" -storepass "$PASSWORD" | grep -E "(SHA1|SHA256):"
echo "--------------------------------------------------------"
echo "📝 CẤU HÌNH VÀO local.properties:"
echo "RELEASE_STORE_FILE=$KEYSTORE_FILE"
echo "RELEASE_STORE_PASSWORD=$PASSWORD"
echo "RELEASE_KEY_ALIAS=$ALIAS"
echo "RELEASE_KEY_PASSWORD=$PASSWORD"
echo "--------------------------------------------------------"
