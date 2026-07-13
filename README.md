# ZenGecko Mobile v0.1.3

Prototipo Android de navegador basado en **GeckoView real**, con una interfaz móvil inspirada en ideas de productividad de Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. Zen Browser, su nombre y su identidad visual pertenecen a sus respectivos titulares.

## Cambios de v0.1.3

- Corrige la actualización visual del panel de pestañas en teléfonos.
- Aumenta el área táctil del botón para cerrar pestañas a 48 dp.
- Añade cierre por deslizamiento horizontal sobre una pestaña.
- Añade capa oscura detrás del panel y cierre al tocar fuera.
- Unifica el comportamiento de “Nueva pestaña” desde la barra superior y el panel.
- Oculta `about:blank` en la barra de direcciones.
- Reserva la barra lateral fija para tabletas; los teléfonos en horizontal conservan el panel superpuesto.
- Mejora el diálogo para crear espacios y evita nombres vacíos.
- Sustituye el icono por una variante invertida: fondo blanco y símbolo “Z” negro.
- Introduce una clave estable de firma para las compilaciones de GitHub Actions.

## Actualizaciones instalables sobre la versión anterior

Las compilaciones anteriores de GitHub Actions usaban una clave de depuración temporal distinta en cada ejecución. Android exige que una actualización esté firmada con el mismo certificado que la aplicación ya instalada.

La versión **0.1.3** migra a una clave estable guardada como secretos del repositorio. Debido a ese cambio, es necesario desinstalar una última vez la versión 0.1.2 antes de instalar 0.1.3. A partir de 0.1.3, las siguientes versiones firmadas con esta misma clave podrán instalarse como actualizaciones normales.

## Incluido

- Motor GeckoView y Java 17.
- Android API 26+.
- Pestañas y sesiones Gecko independientes.
- Espacios de trabajo.
- Pestañas esenciales.
- Barra de direcciones y búsqueda con DuckDuckGo.
- Restauración básica de pestañas y espacios.
- Áreas seguras para Android/HyperOS.
- Interfaz adaptable para teléfono y tableta.

## Compilar

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Licencias

El código original de este prototipo se entrega bajo MPL-2.0. GeckoView conserva su licencia. La atribución y modificación del icono se documentan en `NOTICE_ZEN_ICON.md`.
