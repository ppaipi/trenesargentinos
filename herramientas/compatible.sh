#!/bin/bash
# Arma js/compat/ (sintaxis de 2017: Chrome 55, Safari 11, Firefox 57) desde
# los módulos de la web, para navegadores sin `?.`/`??`. index.html elige solo.
# Correrlo después de cada cambio en js/ (paridad con la versión moderna).
set -e
cd "$(dirname "$0")/.."
OPC=(--bundle --format=iife --minify --target=chrome55,safari11,firefox57 --banner:js="$(cat herramientas/polyfills.js)")
npx --yes esbuild@0.24.0 js/compat-entrada.js "${OPC[@]}" --outfile=js/compat/app.js
npx --yes esbuild@0.24.0 js/v4/patrocinio.js "${OPC[@]}" --outfile=js/compat/patrocinio.js
