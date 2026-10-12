#!/usr/bin/env bash
# Compila la app Android firmada y la deja en descargas/TrenAR.apk, que GitHub
# Pages sirve en https://tapia.chiqui.ar/descargas/TrenAR.apk (botón del pie
# de la web y README). Necesita ~/Android/trenes-firma.properties; sin firma el
# APK de release no instala. Después: commitear descargas/ y subir a main.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz/android"
JAVA_HOME="${JAVA_HOME:-$HOME/Android/jdk}" ./gradlew assembleRelease -q
mkdir -p "$raiz/descargas"
cp app/build/outputs/apk/release/app-release.apk "$raiz/descargas/TrenAR.apk"
ls -lh "$raiz/descargas/TrenAR.apk"
