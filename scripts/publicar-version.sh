#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"

if [ -z "$REPO_ROOT" ]; then
  echo "Ejecuta este script dentro del repositorio ZenGecko-Mobile."
  exit 1
fi

cd "$REPO_ROOT"

CURRENT_BRANCH="$(git branch --show-current)"
if [ "$CURRENT_BRANCH" != "main" ]; then
  echo "La publicación debe hacerse desde main. Rama actual: $CURRENT_BRANCH"
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "Hay cambios sin guardar."
  echo "Haz commit antes de publicar la versión."
  exit 1
fi

VERSION="$(sed -n "s/^[[:space:]]*versionName[[:space:]]*'\([^']*\)'.*/\1/p" app/build.gradle | head -n 1)"
if [ -z "$VERSION" ]; then
  echo "No se pudo leer versionName desde app/build.gradle."
  exit 1
fi

TAG="v${VERSION}"

echo "Actualizando main…"
git pull --rebase origin main
git push origin main

if git ls-remote --exit-code --tags origin "refs/tags/${TAG}" >/dev/null 2>&1; then
  echo "La etiqueta ${TAG} ya existe."
  echo "Aumenta versionCode y versionName antes de publicar otra versión."
  exit 1
fi

echo "Creando ${TAG}…"
git tag -a "$TAG" -m "Zen Browser ${TAG}"
git push origin "$TAG"

echo
echo "Publicación iniciada correctamente."
echo "Actions: https://github.com/andresaavelasquez-ctrl/ZenGecko-Mobile/actions"
echo "Releases: https://github.com/andresaavelasquez-ctrl/ZenGecko-Mobile/releases"
echo "Web: https://andresaavelasquez-ctrl.github.io/ZenGecko-Mobile/"
