# Zen Browser v0.1.12 — Stable Paint & Live Downloads

Navegador Android experimental construido con **GeckoView real**, interfaz AMOLED y navegación por pestañas y espacios. No utiliza Android WebView.

> Proyecto comunitario no oficial. No es una distribución oficial de Zen Browser.

## Prioridad de esta versión

La v0.1.12 se concentra en corregir el fallo más importante detectado durante las pruebas: algunas páginas terminaban mostrando una superficie gris después de cargar y solo volvían a verse al recargarlas.

La recuperación anterior forzaba varias invalidaciones de `GeckoView`. Esa estrategia fue retirada. La interfaz ahora sigue el estado real del compositor mediante los eventos de primera composición, primera pintura de contenido y reinicio del estado de pintura.

## Novedades de v0.1.12

### Pintado estable de páginas

- Nueva protección de primera pintura, vinculada a la pestaña y navegación activas.
- La capa de espera desaparece al recibir la primera pintura real de contenido.
- Eliminadas las invalidaciones repetidas que podían dejar la superficie gris.
- Recuperación excepcional con límite de tiempo, sin recarga automática destructiva.
- Restauración de una pestaña si el proceso de contenido de Gecko falla.
- La pantalla de Inicio usa un estado explícito y ya no aparece durante recargas o redirecciones.

### Navegación Atrás

- Un solo controlador para el botón y el gesto Atrás de Android.
- Prioridad: cerrar paneles, salir de fullscreen, retroceder en la página, volver a la pestaña anterior y finalmente confirmar la salida.
- Integración con el dispatcher moderno de Android 13 o superior.

### Descargas en tiempo real

- Tarjeta flotante con:
  - barra de progreso;
  - porcentaje;
  - velocidad actual;
  - bytes descargados y tamaño total;
  - estados de espera, descarga, finalización y error.
- El panel de Descargas se actualiza automáticamente mientras permanece abierto.
- Cancelar, reintentar, abrir y quitar continúan disponibles.
- Menos registros históricos para evitar crecimiento innecesario de datos locales.

### Rendimiento y almacenamiento

- Solo la pestaña visible permanece activa.
- Multimedia suspendida automáticamente en pestañas inactivas.
- Recuperación de pestañas cuyo proceso haya fallado.
- Mantenimiento opcional de cachés temporales cuando superan un límite razonable.
- Acción manual para limpiar solo las cachés, sin borrar cookies ni sesiones.
- Menos reconstrucciones e invalidaciones de la superficie web.

### Interfaz

- Barra superior más integrada, redondeada y con menos divisiones visuales.
- Botones con estado presionado sutil en lugar de cuadrados permanentes.
- Barra de dirección suavizada.
- Panel vertical más compacto.
- Accesos rápidos más pequeños.
- Selector de espacios centrado y con iconos:
  - Personal;
  - Trabajo;
  - Estudio.

### Modo escritorio

- Modo escritorio individual para la pestaña actual.
- Opción para abrir nuevas pestañas en modo escritorio por defecto.
- Cambia el agente de usuario y el viewport antes de recargar la página.

### Inicio Zen adaptable

- Se conserva el fondo vertical de la v0.1.11.
- Se añade la imagen horizontal proporcionada por el usuario en `drawable-land-nodpi`.
- Android selecciona automáticamente el fondo correcto según la orientación.
- Ninguna imagen nueva fue generada; ambos fondos proceden de los archivos aportados al proyecto y solo fueron convertidos a WebP.

### Tecla Z

- Nuevo sonido mecánico más definido y audible.
- La reproducción manual al tocar la tecla tiene prioridad sobre la animación de despertar.
- Continúa respetando el modo silencioso y el ajuste independiente de vibración.

## Funciones conservadas

- Favoritos.
- Historial con búsqueda y papelera.
- Perfiles locales.
- Espacios Personal, Trabajo y Estudio.
- DuckDuckGo, Perplexity, Brave Search y Google.
- Fullscreen web.
- Barra de dirección durante la navegación.
- Restauración de sesión.
- Tema AMOLED.
- Configuración ampliada.

## Versión

```text
versionCode 13
versionName 0.1.12
```

Artefacto esperado:

```text
ZenBrowser-v0.1.12-debug
```

## Compilación

```bash
gradle :app:assembleDebug --stacktrace
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Política de actualizaciones

Cada actualización modifica README y CHANGELOG, incrementa `versionCode`, conserva el mismo `applicationId` y utiliza la clave estable configurada en GitHub Actions para permitir instalarla sobre la versión anterior.

## Pendiente antes de una versión estable

1. Bloqueador de anuncios y rastreadores configurable.
2. Aislamiento completo de cookies y almacenamiento por perfil.
3. Base de datos para historiales extensos.
4. Caché local de favicons.
5. Restauración binaria de `GeckoSession.SessionState`.
6. Pruebas instrumentadas en varios fabricantes y versiones de Android.

## Licencias

El código original se entrega bajo MPL-2.0. GeckoView conserva su licencia. Las marcas de terceros pertenecen a sus propietarios.
