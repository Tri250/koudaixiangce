#!/bin/bash
# =============================================================================
# RapidRAW Release Keystore Generation Script
# =============================================================================
# Usage:
#   ./scripts/generate-release-keystore.sh
#
# This script generates a new release keystore for signing RapidRAW APKs.
# Required environment variables:
#   KEYSTORE_PASSWORD  - Password for the keystore
#   KEY_PASSWORD       - Password for the signing key (defaults to KEYSTORE_PASSWORD)
#   KEY_ALIAS          - Alias for the signing key (default: rapidraw)
#
# IMPORTANT:
#   1. Run this script ONCE and securely store the generated keystore
#   2. Add KEYSTORE_PASSWORD and KEY_PASSWORD to CI secrets (GitHub Actions)
#   3. Add RELEASE_KEYSTORE_BASE64 to CI secrets:
#      base64 -w0 app/release.keystore
#   4. NEVER commit the keystore file to version control
#   5. Keep a secure backup of the keystore and passwords
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
KEYSTORE_PATH="$PROJECT_DIR/app/release.keystore"

# Check environment variables
if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
  echo "ERROR: KEYSTORE_PASSWORD environment variable is required"
  echo "Usage: KEYSTORE_PASSWORD=xxx KEY_PASSWORD=xxx ./scripts/generate-release-keystore.sh"
  exit 1
fi

KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
KEY_ALIAS="${KEY_ALIAS:-rapidraw}"

# Check if keystore already exists
if [ -f "$KEYSTORE_PATH" ]; then
  echo "WARNING: $KEYSTORE_PATH already exists"
  read -rp "Overwrite? (y/N): " confirm
  if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "Aborted."
    exit 0
  fi
  rm -f "$KEYSTORE_PATH"
fi

echo "Generating release keystore..."

keytool -genkey -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=RapidRAW, OU=Development, O=RapidRAW, L=Beijing, ST=Beijing, C=CN"

echo ""
echo "============================================"
echo "  Release keystore generated successfully!"
echo "============================================"
echo "  Location: $KEYSTORE_PATH"
echo "  Alias:    $KEY_ALIAS"
echo ""
echo "  Next steps:"
echo "  1. Generate base64 for CI:"
echo "     base64 -w0 $KEYSTORE_PATH"
echo "  2. Add to GitHub Actions secrets:"
echo "     - RELEASE_KEYSTORE_BASE64"
echo "     - KEYSTORE_PASSWORD"
echo "     - KEY_PASSWORD"
echo "  3. Keep this keystore and passwords in a secure location"
echo "============================================"