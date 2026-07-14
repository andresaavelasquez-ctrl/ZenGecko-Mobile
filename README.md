# Zen Browser v0.1.11 — Zen Home & Mechanical Key

Navegador Android experimental basado en **GeckoView real**, con una interfaz móvil inspirada en Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. No es una distribución oficial de Zen Browser.

## Corrección de la v0.1.10

La v0.1.10 incorporó Favoritos, Historial, Descargas, espacios, perfiles y una configuración ampliada, pero la compilación quedó detenida porque `MainActivity` llamaba a dos métodos que no habían sido definidos:

- `createSidebarShortcutGrid()`
- `sidebarBottomAction(...)`

La v0.1.11 incorpora ambas implementaciones, conserva todos los bloques A–E y deja funcional el menú lateral nuevo.

## Novedades de v0.1.11

### Pantalla Zen

- La imagen de bonsái proporcionada para el proyecto se usa como fondo de la nueva pestaña.
- No se ha generado una imagen alternativa: el recurso se limita a conversión WebP y compresión.
- En vertical se usa recorte centrado.
- En horizontal y tablet se adapta con `FIT_CENTER` sobre fondo AMOLED para conservar el bonsái completo.
- Capa oscura de contraste para mantener legibles:
  - el nombre;
  - el subtítulo;
  - la búsqueda;
  - los accesos rápidos.
- Aparición progresiva y acercamiento ambiental muy lento.

### Tecla Z

- El antiguo cuadro blanco se convierte en una tecla:
  - superficie blanca;
  - bisel;
  - base inferior;
  - sombra;
  - desplazamiento al presionar;
  - compresión vertical corta;
  - retorno suavizado.
- Sonido mecánico corto con `SoundPool`.
- El sonido respeta el modo silencioso o vibración del teléfono.
- Respuesta háptica con `HapticFeedbackConstants.KEYBOARD_TAP`.
- Una animación de despertar se reproduce una sola vez por sesión.
- El recurso de audio se libera correctamente al destruir la actividad.

### Configuración adicional

Se conservan todas las opciones de v0.1.10 y se agregan:

- Fondo bonsái en Inicio.
- Movimiento ambiental del fondo.
- Sonido de tecla mecánica.
- Respuesta háptica de la tecla.

## Funciones conservadas de v0.1.10

- Favoritos en lugar de Esenciales.
- Historial persistente con búsqueda y papelera.
- Botón Atrás para:
  1. salir de contenido fullscreen;
  2. regresar de paneles secundarios;
  3. cerrar menús;
  4. retroceder en la página;
  5. cambiar a la pestaña anterior;
  6. confirmar antes de salir.
- Espacios Personal, Trabajo y Educación.
- Perfiles locales separados de los espacios.
- Barra de dirección visible al navegar.
- Inicio desde el icono de casa.
- Descargas con aviso visual, progreso, cancelar y reintentar.
- Recuperación de superficies negras de GeckoView.
- Configuración desplegable de DuckDuckGo, Perplexity, Brave Search y Google.
- Tema AMOLED y modo fullscreen web compatible.

## Versión

```text
versionCode 12
versionName 0.1.11
```

Artefacto esperado:

```text
ZenBrowser-v0.1.11-debug
```

## Compilación

```bash
gradle :app:assembleDebug --stacktrace
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Política de novedades

Cada actualización modifica README y CHANGELOG con la versión, correcciones y artefacto esperado.

## Pendiente antes de una versión estable

1. Bloqueador de anuncios y rastreadores configurable.
2. Aislamiento completo de cookies y almacenamiento por perfil.
3. Base de datos para historial y favoritos extensos.
4. Caché de favicons.
5. Restauración binaria de `GeckoSession.SessionState`.
6. Pruebas instrumentadas y de proceso muerto.

## Licencias

El código original se entrega bajo MPL-2.0. GeckoView conserva su licencia. Las marcas de terceros pertenecen a sus propietarios.
