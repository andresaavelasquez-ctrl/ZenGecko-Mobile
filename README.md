# Zen Browser v0.1.15 — Surface Recovery & Downloads

Zen Browser para Android basado en **GeckoView real**, con interfaz AMOLED, espacios de navegación y administración de pestañas.

> Proyecto comunitario no oficial. No es una distribución oficial de Zen Browser.

## Objetivo de esta versión

La v0.1.13 reúne en un solo paquete las mejoras de navegación, accesos rápidos, configuración, menús contextuales web y descargas que se planificaron después de la v0.1.12.

La prioridad es reducir reconstrucciones visibles, conservar la sesión de cada pestaña y retirar las cubiertas de transición únicamente cuando GeckoView confirma contenido pintado.

## Navegación y carga

- Elimina el círculo central permanente y los textos normales de “Cargando” o “Renderizando”.
- Integra una línea de progreso de 3 dp debajo de la barra superior.
- Añade un resplandor morado discreto dentro de la barra de dirección.
- Mantiene una cubierta AMOLED durante el intervalo sin pintura válida.
- Retira la cubierta mediante un `crossfade` al recibir `onFirstContentfulPaint`.
- Muestra un aviso pequeño solamente cuando la carga supera aproximadamente seis segundos.
- El aviso lento ofrece **REINTENTAR** y **CANCELAR**.
- Conserva la misma `GeckoSession` de una pestaña existente.
- Usa una captura temporal de la superficie anterior durante cambios de pestaña.
- Evita mostrar Inicio durante recargas, redirecciones y cambios entre páginas.
- El botón Atrás cierra primero los menús contextuales antes de navegar.

## Pestañas y espacios

- Filas de pestaña más compactas.
- Título y dominio cuando existe espacio.
- Solo se anima la pestaña seleccionada.
- Cambio entre Personal, Trabajo y Educación mediante desplazamiento lateral y opacidad.
- La transición utiliza únicamente `translationX` y `alpha`.
- Conserva los iconos vectoriales propios de cada espacio.
- Seleccionar una pestaña abierta no vuelve a cargar su URL.

## Accesos rápidos editables

- Accesos separados por espacio.
- Persistencia después de reiniciar la aplicación.
- Toque para abrir.
- Pulsación larga para editar.
- Arrastre para reorganizar.
- Editor con:
  - nombre;
  - dirección;
  - URL de icono;
  - detección automática;
  - vista previa;
  - guardar;
  - eliminar.
- Máximo de ocho accesos por espacio.
- Iconos locales para sitios conocidos.
- Detección de iconos desde el manifiesto web y `/favicon.ico`.
- Caché visual limitada para evitar crecimiento descontrolado.

## Configuración categorizada

La pantalla de Ajustes se organiza en:

1. General
2. Apariencia
3. Búsqueda
4. Pestañas y espacios
5. Descargas
6. Privacidad y datos
7. Rendimiento y almacenamiento
8. Accesibilidad
9. Avanzado

También incorpora:

- búsqueda de categorías;
- icono y resumen por categoría;
- navegación secundaria dentro del mismo panel;
- botón Atrás hacia la portada de Ajustes;
- restablecimiento independiente por categoría;
- modo escritorio por pestaña;
- limpieza controlada de caché visual y temporal.

## Menú contextual web

Al mantener pulsado un elemento compatible, Zen Browser puede mostrar opciones para:

### Imágenes

- Abrir imagen en pestaña nueva.
- Vista previa.
- Descargar imagen.
- Copiar dirección.
- Compartir imagen.
- Copiar imagen.

### Imágenes enlazadas y enlaces

- Abrir enlace.
- Abrir enlace en pestaña nueva.
- Copiar enlace.

### Video y audio

- Abrir multimedia.
- Descargar multimedia.
- Copiar dirección.
- Compartir dirección.

El menú usa un panel inferior en vertical y un panel compacto cercano al punto pulsado en horizontal. La descarga reutiliza el administrador existente y conserva `Referer` y datos de sesión mediante GeckoWebExecutor.

## Zen Edge Downloads

- Notificación AMOLED con icono independiente.
- Nombre del archivo.
- Velocidad suavizada.
- Tamaño descargado y tamaño total.
- Porcentaje.
- Progreso animado en el borde inferior.
- Estados:
  - en espera;
  - descargando;
  - completada;
  - cancelada;
  - error.
- Acciones **VER**, **CANCELAR** y **REINTENTAR**.
- Panel de descargas actualizado cada 400 ms.
- Reutilización de tarjetas para evitar reconstruir la lista completa.
- Pulsación larga para quitar un registro.

## Archivos y seguridad

- `FileProvider` para compartir archivos temporales sin exponer rutas internas.
- Caché de favicons limitada a 10 MB.
- Caché contextual limitada a 80 MB.
- Límite de tamaño para imágenes y multimedia temporales.
- README y CHANGELOG actualizados automáticamente.

## Versión

```text
versionCode 16
versionName 0.1.15
```

Artefacto esperado:

```text
ZenBrowser-v0.1.15-debug
```

## Compilación

```bash
gradle :app:assembleDebug --stacktrace
```

APK generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Validación recomendada en el dispositivo

1. Abrir diez páginas consecutivas.
2. Cambiar rápidamente entre cinco pestañas.
3. Recargar sin que aparezca la pantalla de Inicio.
4. Cambiar entre los tres espacios.
5. Crear, editar, reorganizar y eliminar accesos.
6. Reiniciar y comprobar que los accesos siguen guardados.
7. Mantener pulsadas imágenes, enlaces, GIF, video y audio.
8. Descargar desde el menú contextual.
9. Mantener abierto el panel Descargas y observar el progreso.
10. Rotar el dispositivo con menús abiertos.
11. Verificar el comportamiento del botón Atrás.

## Licencias

El código original del proyecto se entrega bajo MPL-2.0. GeckoView y AndroidX conservan sus licencias. Las marcas e iconos de terceros pertenecen a sus propietarios.

## Hotfix de compilación v0.1.13

- Restaura `installSafeAreaInsets()` en `MainActivity`.
- Corrige el error `cannot find symbol` durante `compileDebugJavaWithJavac`.
- Mantiene los márgenes seguros para recortes de pantalla y modo inmersivo.

## v0.1.14 — Stability & Rendering

- Sustituye los PopupWindow contextuales por diálogos seguros y acotados.
- Copia los datos de imágenes y enlaces antes de abandonar el callback de GeckoView.
- Evita actualizar miniaturas o favicons después de destruir una vista.
- Corrige el cierre al mantener pulsadas imágenes y enlaces.
- Corrige el cierre al crear o editar accesos rápidos.
- El editor de accesos se abre después de cerrar el panel lateral.
- Mantiene una captura de la página anterior hasta el primer contenido pintado.
- Retrasa la cubierta AMOLED para evitar flashes negros y grises.
- Conserva activa brevemente la sesión anterior durante un cambio.
- Reduce la notificación de descarga a una tarjeta de 318 dp por 66 dp.
- La barra de descarga queda contenida dentro de la tarjeta.
- Añade registros defensivos para menús, vistas previas y cambios de sesión.
- Artefacto esperado: `ZenBrowser-v0.1.14-debug`.

## v0.1.15 — Surface Recovery & Downloads

Esta versión no incorpora funciones nuevas de navegación. Está dedicada a estabilidad y acabado:

- una sola `GeckoSession` queda activa;
- las superficies se verifican mediante captura antes de retirar cubiertas;
- una superficie uniforme atascada provoca una reanexión limitada, sin recargar la página;
- las pestañas vacías duplicadas quedan bloqueadas y se limpian al restaurar sesión;
- las capturas se reciclan al confirmar contenido visible;
- las cachés personalizadas se limitan y limpian por antigüedad;
- tarjetas y aviso de descarga son más compactos;
- las descargas `data:` compatibles se procesan internamente;
- los esquemas no descargables muestran un error legible.

Artefacto esperado: `ZenBrowser-v0.1.15-debug`.
