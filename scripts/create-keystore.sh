#!/usr/bin/env bash
# Crea la clave de firma de la app y (opcionalmente) la sube como secretos de GitHub.
# Requiere: keytool (viene con Java/Android Studio). Opcional: gh (GitHub CLI) autenticado.
#
# IMPORTANTE: guarda el archivo .jks y las contraseñas en un lugar seguro (gestor de contraseñas).
# Si se pierden, no podrás publicar actualizaciones que se instalen sobre la app existente.
set -euo pipefail

KEYSTORE="${1:-finanzas-release.jks}"
ALIAS="finanzas"

if [[ -e "$KEYSTORE" ]]; then
  echo "Ya existe $KEYSTORE; no lo sobrescribo." >&2
  exit 1
fi

read -rsp "Contraseña para la clave (mín. 6 caracteres): " PASS; echo
read -rsp "Repite la contraseña: " PASS2; echo
[[ "$PASS" == "$PASS2" ]] || { echo "No coinciden." >&2; exit 1; }

keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Finanzas, O=personal_finances, C=CL"

cat > keystore.properties <<PROPS
storeFile=$KEYSTORE
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS
echo "✓ Creado $KEYSTORE y keystore.properties (ambos ignorados por git)."

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
  read -rp "¿Subir como secretos al repo de GitHub? [s/N] " UP
  if [[ "$UP" =~ ^[sS]$ ]]; then
    base64 < "$KEYSTORE" | tr -d '\n' | gh secret set KEYSTORE_BASE64
    printf %s "$PASS" | gh secret set KEYSTORE_PASSWORD
    printf %s "$ALIAS" | gh secret set KEY_ALIAS
    echo "✓ Secretos subidos."
  fi
else
  echo "Para CI, agrega estos secretos en GitHub (Settings → Secrets and variables → Actions):"
  echo "  KEYSTORE_BASE64   = salida de: base64 < $KEYSTORE | tr -d '\\n'"
  echo "  KEYSTORE_PASSWORD = tu contraseña"
  echo "  KEY_ALIAS         = $ALIAS"
fi
