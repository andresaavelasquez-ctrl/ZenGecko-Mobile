# ZenGecko Mobile v0.1.4 — Zen Shell

Prototipo Android de navegador basado en **GeckoView real**, con una interfaz móvil inspirada en la organización vertical, los espacios y el modo compacto de Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. El diseño se adapta a Android y no pretende ser una distribución oficial de Zen Browser.

## Cambios principales de v0.1.4

- Nueva barra superior compacta sin barra inferior.
- Buscador flotante de estilo Zen que mantiene la página actual en segundo plano.
- Resultados de búsqueda con pestañas abiertas, navegación reciente y búsqueda web.
- Página de nueva pestaña nativa con acceso inmediato al buscador y sitios rápidos.
- Transición corta y limpia al cambiar de pestaña o espacio, sin reconstruir GeckoView.
- Apertura y cierre animados del panel lateral.
- Cierre de pestañas mediante botón o deslizamiento lateral.
- Restauración real de la última pestaña cerrada con la acción **DESHACER**.
- Pestañas verticales con marca visual, dominio y estado de carga.
- Iconos vectoriales consistentes para la navegación.
- Historial reciente ligero, almacenado localmente y limitado para conservar rendimiento.
- `versionCode 5` y `versionName 0.1.4`.

## Principios de esta versión

- No existe barra inferior.
- Las animaciones son breves y no utilizan desenfoque continuo.
- El cambio entre pestañas conserva las sesiones Gecko abiertas.
- El panel móvil se crea bajo demanda.
- La barra lateral fija se reserva para tabletas.
- La personalización profunda se pospone hasta consolidar estabilidad y funciones básicas.

## Funciones actuales

- Motor GeckoView, Java 17 y Android API 26+.
- Sesiones independientes por pestaña.
- Pestañas verticales, esenciales y espacios de trabajo.
- Atrás, adelante, recargar y detener.
- Barra de búsqueda con DuckDuckGo.
- Restauración básica de pestañas, URLs, títulos y espacios.
- Apertura de enlaces `http` y `https` desde Android.
- Firma estable de compilaciones debug para actualizar sin desinstalar desde v0.1.3.

## Compilación

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

APK resultante:

```text
app/build/outputs/apk/debug/app-debug.apk
```

El workflow de GitHub Actions publica el artefacto:

```text
ZenGeckoMobile-v0.1.4-debug
```

## Pendiente antes de una versión estable

1. Descargas y selector de archivos.
2. Permisos web con interfaz propia.
3. Historial y marcadores completos mediante una base de datos.
4. Favicons reales y almacenamiento en caché.
5. Restauración binaria de `GeckoSession.SessionState`.
6. Protección de rastreo configurable.
7. Pruebas instrumentadas y de proceso muerto.
8. Personalización visual y Zen Mods después de consolidar la base.

## Licencias

El código original se entrega bajo MPL-2.0. GeckoView conserva su licencia. El aviso del icono modificado se encuentra en `NOTICE_ZEN_ICON.md`.
