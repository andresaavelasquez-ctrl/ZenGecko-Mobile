# ZenGecko Mobile v0.1.2

Prototipo Android de navegador basado en **GeckoView real**, con una interfaz móvil inspirada en las ideas de productividad de Zen Browser. No utiliza Android WebView ni emulación.

> Proyecto comunitario no oficial. No está afiliado, patrocinado ni respaldado por el equipo de Zen Browser. El icono incluido es una adaptación geométrica para pruebas basada en la identidad visual pública de Zen.

## Cambios de v0.1.2

- Respeta automáticamente la barra de estado, la barra de navegación y los recortes de pantalla.
- Corrige los controles superiores que quedaban debajo del reloj, la batería y los indicadores de HyperOS.
- Recalcula el área segura al girar, usar pantalla dividida o cambiar el tamaño de la ventana.
- Ajusta el panel lateral superpuesto para que no invada las barras del sistema.
- Mejora el enfoque de la barra de direcciones y la apertura del teclado.
- Añade un icono de aplicación oscuro inspirado en el icono oficial de Zen Browser.
- Ignora `.zengecko-backups/` para que las copias locales no bloqueen futuras actualizaciones.
- Incrementa `versionCode` a 3 y `versionName` a `0.1.2`.

## Cambios de v0.1.1

- Corrige el ciclo de vida entre `GeckoView` y `GeckoSession` al cambiar la configuración de pantalla.
- Evita reconstruir toda la interfaz cuando HyperOS envía un cambio de configuración equivalente al iniciar.
- Libera la sesión antes de reemplazar un `GeckoView` y la vuelve a conectar de forma segura.
- Inicializa `GeckoRuntime` bajo demanda en lugar de ejecutar `warmUp()` durante `Application.onCreate()`.
- Añade registros de diagnóstico de Gecko y del ciclo de vida en compilaciones debug.

## Funciones actuales

- Motor GeckoView, Java 17 y Android API 26+.
- Sesiones Gecko independientes por pestaña.
- Barra de direcciones con búsqueda mediante DuckDuckGo.
- Atrás, adelante, recargar y detener.
- Pestañas con título, URL y progreso.
- Espacios de trabajo: Personal, Trabajo y Estudio.
- Creación de nuevos espacios.
- Pestañas esenciales visibles en todos los espacios.
- Restauración básica de pestañas, URLs, títulos y espacios.
- Barra lateral fija en pantallas de 720dp o más.
- Panel lateral superpuesto en teléfonos.
- Apertura de enlaces `http` y `https` enviados desde Android.
- Bloqueo conservador de permisos sensibles todavía no implementados en UI.

## Abrir en Android Studio

1. Instala Android Studio con Android SDK 36 y JDK 17 o superior.
2. Abre esta carpeta como proyecto.
3. Espera la sincronización de Gradle.
4. Ejecuta `app` en un dispositivo ARM64 o emulador.

El proyecto usa `GECKOVIEW_VERSION=152.+`. Conviene sustituirlo por una versión exacta antes de una publicación estable para obtener compilaciones reproducibles.

## Compilar

Linux/macOS:

```bash
./bootstrap-gradle.sh :app:assembleDebug
```

Windows:

```bat
bootstrap-gradle.bat :app:assembleDebug
```

APK resultante:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Próximas capas recomendadas

1. Prompts web completos: alertas, archivos, autenticación y permisos.
2. Descargas con notificación y selector de carpeta.
3. Historial y marcadores mediante Places.
4. Restauración binaria de `GeckoSession.SessionState`.
5. Protección de rastreo configurable.
6. Extensiones compatibles de Firefox mediante WebExtensionController.
7. Vista dividida, Glance y pestañas fijadas.
8. Migración gradual de estado y servicios a Mozilla Android Components.
9. Pruebas instrumentadas, pruebas de proceso muerto y CI firmado.

## Licencias y marca

El código original de este prototipo se entrega bajo MPL-2.0. GeckoView y Android Components pertenecen a sus respectivos proyectos y conservan sus licencias. Zen Browser, su nombre y su identidad visual pertenecen a sus respectivos titulares.
