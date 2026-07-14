# ZenGecko Mobile v0.1.8 — Minimal Immersive Zen

Navegador Android experimental basado en **GeckoView real**, con una interfaz móvil inspirada en Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. ZenGecko adapta ideas de organización visual de Zen Browser a Android y no es una distribución oficial.

## Novedades de v0.1.8

- Modo inmersivo activado de forma predeterminada: las barras de Android permanecen ocultas, pero los controles del navegador siguen disponibles.
- Pantalla completa web independiente para videos, juegos y páginas compatibles; Atrás sale primero del fullscreen web.
- Barra superior minimalista con búsqueda retraíble.
- Botón de nueva pestaña trasladado al lado izquierdo.
- Corrección del recorte lateral del buscador de la página de inicio.
- Eliminación visual y migración interna de Espacios a una colección única de pestañas.
- Pestañas laterales más pequeñas, con una sola línea y cierre compacto.
- Menú inferior con Configuración, Perfil y Descargas.
- Administrador inicial de descargas con progreso, apertura, cancelación, reintento y eliminación del historial.
- Accesos rápidos con marcas visuales de GitHub, YouTube y Wikipedia.
- Reducción general del tamaño de toolbar, portada, buscadores y tarjetas.
- Tema AMOLED negro conservado.
- Selector de DuckDuckGo, Perplexity, Brave Search y Google conservado.
- `versionCode 9` y `versionName 0.1.8`.

## Comportamiento de pantalla completa

ZenGecko se inicia en modo inmersivo de aplicación: ocupa toda la pantalla y oculta las barras del sistema sin ocultar el menú, la lupa ni la navegación.

Cuando una página solicita fullscreen, GeckoView oculta temporalmente la interfaz del navegador. Pulsar Atrás restaura primero la interfaz de ZenGecko sin cerrar la aplicación.

## Funciones actuales

- GeckoView, Java 17 y Android API 26+.
- Sesiones independientes por pestaña.
- Pestañas verticales y esenciales.
- Navegación atrás, adelante, recargar y detener.
- Omnibox retraíble y selector de buscador.
- Restauración básica de pestañas, URLs y títulos.
- Apertura de enlaces HTTP/HTTPS desde Android.
- Firma estable de compilaciones debug para actualizar sin desinstalar.
- Descargas gestionadas mediante respuestas externas de GeckoView y almacenamiento Android.

## Compilación

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

APK resultante:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Artefacto de GitHub Actions:

```text
ZenGeckoMobile-v0.1.8-debug
```

## Historial reciente

- **0.1.8:** interfaz mínima inmersiva, búsqueda retraíble, colección única y descargas.
- **0.1.7:** tema AMOLED, fullscreen web y GeckoView persistente.
- **0.1.6:** primera implementación de Compact Zen.
- **0.1.5:** selector de DuckDuckGo, Perplexity, Brave Search y Google.
- **0.1.4:** Zen Shell, panel lateral y página inicial nativa.

## Política de novedades

A partir de v0.1.8, cada actualizador debe modificar este README con la versión, cambios principales, artefacto esperado e historial reciente.

## Pendiente antes de una versión estable

1. Bloqueador de anuncios y protección de rastreo configurable.
2. Selector de archivos y subida desde páginas web.
3. Historial y marcadores completos mediante base de datos.
4. Caché real de favicons de sitios visitados.
5. Restauración binaria de `GeckoSession.SessionState`.
6. Sincronización opcional y cifrada.
7. Pruebas instrumentadas y de proceso muerto.

## Licencias

El código original se entrega bajo MPL-2.0. GeckoView conserva su licencia. Las marcas de terceros pertenecen a sus respectivos propietarios y se usan únicamente para identificar accesos directos.
