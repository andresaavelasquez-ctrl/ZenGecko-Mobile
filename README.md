# ZenGecko Mobile v0.1.9 — Zen Sidebar & Stability

Navegador Android experimental basado en **GeckoView real**, con una interfaz móvil inspirada en Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. ZenGecko no es una distribución oficial de Zen Browser.

## Novedades de v0.1.9

- Barra superior AMOLED unificada, sin bloques de color notorios donde antes estaba la omnibox.
- Barra de carga nueva: fina, redondeada y con gradiente Zen.
- Configuración, Perfiles y Descargas dejan de abrir Activities separadas; ahora se muestran como paneles internos sin desmontar GeckoView.
- Configuración funcional:
  - motor de búsqueda;
  - animaciones;
  - accesos rápidos en pestaña nueva;
  - descargas mediante datos móviles;
  - borrado de direcciones recientes.
- Menú desplegable de perfiles locales.
- Administrador de descargas integrado con abrir, cancelar, reintentar y quitar.
- Panel de privacidad y esenciales.
- Menú lateral rediseñado según la referencia:
  - engrane, atrás, adelante y recargar;
  - omnibox;
  - cuadrícula de ocho accesos;
  - selector de perfil;
  - pestañas abiertas;
  - nueva pestaña;
  - esenciales, descargas, privacidad y perfiles.
- Menor reconstrucción de interfaz durante la carga.
- Notificaciones de progreso de GeckoView limitadas para reducir tirones.
- Renderizado de UI agrupado por fotogramas.
- El rail y el menú ya no se reconstruyen por cada cambio de progreso.
- `versionCode 10` y `versionName 0.1.9`.

## Compilación

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Artefacto de GitHub Actions:

```text
ZenGeckoMobile-v0.1.9-debug
```

## Historial reciente

- **0.1.9:** panel lateral Zen, paneles internos y optimización de renderizado.
- **0.1.8:** modo inmersivo predeterminado, búsqueda retraíble y descargas iniciales.
- **0.1.7:** tema AMOLED, fullscreen web y GeckoView persistente.
- **0.1.6:** primera implementación de Compact Zen.
- **0.1.5:** selector de DuckDuckGo, Perplexity, Brave Search y Google.

## Política de novedades

Cada actualizador modifica este README y CHANGELOG con la versión, cambios principales y artefacto esperado.

## Pendiente antes de una versión estable

1. Bloqueador de anuncios y rastreadores configurable.
2. Aislamiento completo de datos por perfil.
3. Historial y marcadores mediante base de datos.
4. Caché de favicons.
5. Restauración binaria de `GeckoSession.SessionState`.
6. Pruebas instrumentadas y de proceso muerto.

## Licencias

El código original se entrega bajo MPL-2.0. GeckoView conserva su licencia. Las marcas de terceros pertenecen a sus propietarios y se usan para identificar accesos directos.
