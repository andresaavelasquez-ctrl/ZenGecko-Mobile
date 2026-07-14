# Zen Browser v0.1.10 — Navigation, History & Settings

Navegador Android experimental basado en **GeckoView real**, con una interfaz móvil inspirada en Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. No es una distribución oficial de Zen Browser.

## Novedades de v0.1.10

### Navegación

- El botón Atrás sigue este orden:
  1. sale del fullscreen web;
  2. vuelve del panel secundario al menú lateral;
  3. cierra buscadores o menús;
  4. retrocede dentro de la página;
  5. cambia a la pestaña anterior;
  6. solicita confirmación antes de cerrar la aplicación.
- Botón Inicio añadido al menú lateral.
- Página de inicio configurable.

### Menú lateral

- Nuevo encabezado con icono y nombre **Zen Browser**.
- Se eliminó la barra de búsqueda redundante del panel.
- Selector desplegable de espacios:
  - Personal;
  - Trabajo;
  - Educación.
- Cambiar de espacio muestra sus propias pestañas.
- Los paneles Favoritos, Historial, Descargas, Configuración y Perfil incluyen un botón para volver al menú principal.
- Se eliminó la duplicación entre el selector Personal y el menú de perfiles.

### Favoritos e historial

- “Esenciales” pasa a ser **Favoritos**.
- Los antiguos esenciales se migran automáticamente la primera vez que se abre Favoritos.
- Añadir o quitar la página actual.
- Historial persistente con:
  - buscador superior;
  - título, URL y fecha;
  - límite de 100, 250 o 500 páginas;
  - papelera para borrar el historial.

### Dirección y carga

- Barra de dirección visible únicamente al navegar por una página.
- Omnibox más redondeada.
- La pantalla de nueva pestaña mantiene su buscador principal.
- Se eliminó `coverUntilFirstPaint` de los cambios de sesión para evitar cubiertas negras permanentes.
- Recuperación visual automática de GeckoView después de terminar una carga.
- Barra de progreso configurable.

### Descargas

- Confirmación opcional antes de descargar.
- Nueva tarjeta visual “Descarga iniciada”.
- Acceso directo al administrador desde la tarjeta.
- Carpeta de descargas nueva: `Download/ZenBrowser`.
- Abrir, cancelar, reintentar y quitar del historial.

### Configuración ampliada

- Motor de búsqueda mediante menú desplegable.
- Página de inicio personalizada.
- Restaurar sesión.
- Confirmar al salir.
- Accesos rápidos en pestaña nueva.
- Deslizar para cerrar pestañas.
- Mostrar u ocultar botón cerrar.
- Animaciones.
- Barra de dirección.
- Barra de progreso.
- Gesto desde el borde.
- Mantener la pantalla encendida.
- Recuperación visual automática.
- Modo ahorro de interfaz.
- JavaScript.
- Reproducción automática.
- Guardar historial.
- Límite del historial.
- Confirmación de descargas.
- Avisos visuales de descarga.
- Descargas mediante datos móviles.
- Borrar historial.
- Borrar favoritos.

Algunas preferencias del motor, como JavaScript y restauración de sesión, se aplican completamente en el siguiente inicio de la aplicación.

### Identidad

- Nombre visible actualizado a **Zen Browser**.
- Nuevo icono adaptativo y legado derivado del icono proporcionado para el proyecto.
- `versionCode 11`.
- `versionName 0.1.10`.

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
ZenBrowser-v0.1.10-debug
```

## Historial reciente

- **0.1.10:** navegación Atrás, favoritos, historial, tres espacios, branding y configuración ampliada.
- **0.1.9:** panel lateral Zen, paneles internos y optimización de renderizado.
- **0.1.8:** modo inmersivo predeterminado, búsqueda retraíble y descargas.
- **0.1.7:** tema AMOLED, fullscreen web y GeckoView persistente.
- **0.1.6:** primera implementación de Compact Zen.

## Política de novedades

Cada actualizador modifica este README y CHANGELOG con la versión, cambios principales y artefacto esperado.

## Pendiente antes de una versión estable

1. Bloqueador de anuncios y rastreadores configurable.
2. Aislamiento completo de cookies y almacenamiento por perfil.
3. Base de datos para historial y favoritos de gran tamaño.
4. Caché de favicons.
5. Restauración binaria de `GeckoSession.SessionState`.
6. Sincronización opcional y cifrada.
7. Pruebas instrumentadas y de proceso muerto.

## Licencias

El código original se entrega bajo MPL-2.0. GeckoView conserva su licencia. Las marcas de terceros pertenecen a sus propietarios y se usan para identificar accesos directos.
