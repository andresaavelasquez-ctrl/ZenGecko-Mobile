# Instalación desde Termux

Este paquete agrega:

- `docs/index.html`
- `docs/styles.css`
- `docs/app.js`
- `docs/.nojekyll`
- `.github/workflows/release-apk.yml`
- `scripts/publicar-version.sh`

La web obtiene automáticamente la última GitHub Release mediante la API pública de GitHub.
El workflow compila la APK firmada y la adjunta a una Release.
El script de Termux crea la etiqueta de versión que inicia la publicación.

## 1. Preparar Termux

```bash
pkg update -y
pkg install git gh unzip -y
termux-setup-storage
gh auth status
```

Si todavía no has iniciado sesión:

```bash
gh auth login
```

Selecciona:

1. GitHub.com
2. HTTPS
3. Login with a web browser

## 2. Entrar o clonar el repositorio

Si ya lo tienes:

```bash
cd ~/ZenGecko-Mobile
git switch main
git pull --rebase origin main
```

Si no lo tienes:

```bash
cd ~
git clone https://github.com/andresaavelasquez-ctrl/ZenGecko-Mobile.git
cd ZenGecko-Mobile
```

## 3. Copiar este paquete

Después de descargar el ZIP en la carpeta Descargas del teléfono:

```bash
cd ~/ZenGecko-Mobile
unzip -o ~/storage/downloads/ZenGecko-GitHub-Pages-AutoRelease.zip -d .
chmod +x scripts/publicar-version.sh
```

## 4. Subir los archivos

```bash
git add docs .github/workflows/release-apk.yml scripts/publicar-version.sh
git commit -m "Add automatic APK releases and download website"
git push origin main
```

## 5. Activar GitHub Pages desde Termux

Primero intenta crear la página:

```bash
gh api \
  --method POST \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2026-03-10" \
  repos/andresaavelasquez-ctrl/ZenGecko-Mobile/pages \
  --input - <<'JSON'
{
  "source": {
    "branch": "main",
    "path": "/docs"
  }
}
JSON
```

Si GitHub responde `409 Conflict`, la página ya existe. Actualiza su origen:

```bash
gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2026-03-10" \
  repos/andresaavelasquez-ctrl/ZenGecko-Mobile/pages \
  --input - <<'JSON'
{
  "build_type": "legacy",
  "source": {
    "branch": "main",
    "path": "/docs"
  }
}
JSON
```

Comprueba el estado:

```bash
gh api repos/andresaavelasquez-ctrl/ZenGecko-Mobile/pages \
  --jq '{status, html_url, source}'
```

La dirección esperada es:

```text
https://andresaavelasquez-ctrl.github.io/ZenGecko-Mobile/
```

## 6. Publicar cada nueva APK

Actualiza en `app/build.gradle`:

```gradle
versionCode 18
versionName '0.1.17'
```

Guarda y sube la nueva versión:

```bash
git add .
git commit -m "Release v0.1.17"
git push origin main
./scripts/publicar-version.sh
```

Al subir la etiqueta `v0.1.17`, GitHub Actions:

1. Compila la APK.
2. La firma con los secrets existentes.
3. La renombra como `ZenBrowser-v0.1.17-debug.apk`.
4. Crea la Release `v0.1.17`.
5. Adjunta la APK.
6. La web detecta la Release automáticamente.

## 7. Publicación manual sin crear la etiqueta desde Termux

También puedes iniciar el workflow manualmente:

```bash
gh workflow run release-apk.yml --ref main
```

Ver ejecuciones:

```bash
gh run list --workflow release-apk.yml --limit 5
```

Ver la ejecución más reciente:

```bash
gh run watch
```

## v0.1.18 — Web Compatibility & Android Integration

- Añade selección nativa, copiar, cortar, pegar y seleccionar todo en la dirección.
- Corrige el icono de Apariencia con una paleta vectorial y tintado explícito.
- Reconstruye el icono adaptativo con fondo, primer plano y capa monocromática separados.
- Elimina bordes negros del icono mostrado dentro del panel lateral.
- Aplica Noche/Día a sugerencias, recientes, diálogos y superficies temporales.
- Comunica a Gecko el esquema de color preferido para páginas compatibles.
- Usa un lienzo web acorde al tema y retrasa la cubierta hasta una pintura estable.
- Registra target=_blank y window.open() como nuevas pestañas Gecko.
- El editor de accesos conserva únicamente Nombre y Dirección.
- El favicon se resuelve automáticamente en segundo plano.
- Completa ACTION_VIEW, WEB_SEARCH, APP_BROWSER y el rol de navegador.
- Añade publicación automática mediante install.sh --release.
- Artefacto de Actions: `ZenBrowser-v0.1.18-debug`.
- APK de Release: `ZenBrowser-v0.1.18-debug.apk`.

## v0.1.19 — Navigation, Landscape & Day Polish

- Restaura la identidad de la Z con un primer plano adaptativo fiel al icono-tecla.
- Unifica launcher, icono monocromático y marca interna sin esquinas negras.
- Sustituye la dirección por un campo nativo que conserva doble toque, pulsación larga,
  seleccionar, seleccionar todo, copiar, cortar, pegar y controles de selección.
- Elimina el procesamiento doble de ACTION_VIEW que podía crear pestañas repetidas.
- Entrega la GeckoSession hija antes de actualizar la interfaz y mantiene su foco durante
  la cadena inicial de redirecciones.
- Protege solicitudes emergentes duplicadas sin recargar la pestaña de origen.
- Mantiene la captura o cubierta anterior cuando una navegación falla temporalmente.
- Verifica el fotograma antes de retirar definitivamente la transición.
- Reemplaza el rail horizontal por un panel fijo con pestañas, título, URL, cierre,
  espacios, Configuración y Descargas.
- Integra los fondos claros proporcionados por el usuario: vertical y horizontal.
- Conserva el fondo oscuro original para Noche.
- Actualiza README, CHANGELOG, documentación de Termux y matriz de pruebas.
- Artefacto: `ZenBrowser-v0.1.19-debug`.
- Release: `ZenBrowser-v0.1.19-debug.apk`.

