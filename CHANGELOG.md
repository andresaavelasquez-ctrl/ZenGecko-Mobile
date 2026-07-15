# Changelog

## 0.1.4

- Added the Zen Shell mobile interface without a bottom navigation bar.
- Added a focused floating address/search surface.
- Added open-tab and recent-navigation suggestions.
- Added a native new-tab surface with quick destinations.
- Added short, low-cost transitions for tab and workspace switching.
- Added animated sidebar opening and closing.
- Added swipe-to-close tab behavior and real undo restoration.
- Added vector navigation icons and clearer tab loading states.
- Added a small persisted recent URL list.
- Bumped Android versionCode to 5.

## 0.1.3

- Improved mobile tab management and popup refresh behavior.
- Added stable signing for continuous APK updates.
- Added the inverted Zen-inspired launcher icon and attribution notice.
- Bumped Android versionCode to 4.

## 0.1.2

- Added Android system-bar and display-cutout safe areas.
- Improved address-bar focus and landscape behavior.
- Added launcher icon resources.

## 0.1.1

- Fixed GeckoView/GeckoSession attachment lifecycle during configuration changes.
- Avoided redundant UI reconstruction on HyperOS startup configuration callbacks.
- Removed eager GeckoRuntime warm-up and switched to lazy initialization.
- Added debug lifecycle and Gecko logging.
- Bumped Android versionCode to 2.

## 0.1.0

- Initial GeckoView browser prototype with tabs and workspaces.

## 0.1.5 — Compact Search

- Fondo degradado estático en la página de nueva pestaña.
- Selector persistente para DuckDuckGo, Perplexity, Brave Search y Google.
- Eliminación de las flechas laterales redundantes de la paleta de búsqueda.
- DuckDuckGo continúa como motor predeterminado.
- Cobertura hasta la primera pintura de GeckoView para evitar páginas grises.
- Protección de seguridad para que la capa de transición nunca quede bloqueada.

## 0.1.6 — Compact Zen

- Barra vertical compacta permanente en horizontal y tabletas.
- Panel expandido desde el borde en teléfonos verticales.
- Gesto desde el borde izquierdo para abrir la navegación.
- Esenciales convertidas en cuadrícula visual.
- Espacios integrados en la barra compacta.
- Buscador incorporado dentro del panel lateral.
- Contenido web sobre un fondo ambiental con márgenes.
- Barra superior reducida en modo compacto.
- Sin barra inferior y sin recrear GeckoView al expandir el panel.

## 0.1.7 — AMOLED Immersive Zen

- Tema AMOLED negro con contraste alto.
- Edge-to-edge y modo inmersivo real.
- Fullscreen web mediante GeckoSession.ContentDelegate.onFullScreen.
- Fullscreen manual desde el panel.
- Un único GeckoView durante rotación.
- Recuperación al volver desde segundo plano.
- Rail de 58 dp y panel lateral inspirado en Zen.
- Conservación del selector de motores.

## 0.1.8 — Minimal Immersive Zen

- Barras Android ocultas por defecto sin ocultar la interfaz del navegador.
- Fullscreen web separado y salida mediante Atrás.
- Omnibox retraíble y botón de nueva pestaña a la izquierda.
- Buscador inicial sin recortes laterales.
- Migración de Espacios a una colección única.
- Pestañas compactas.
- Configuración, Perfil y administrador de Descargas.
- Logos visuales en accesos rápidos.
- README actualizado automáticamente con cada versión.

## 0.1.9 — Zen Sidebar & Stability

- Barra superior AMOLED unificada.
- Barra de carga redondeada con gradiente Zen.
- Configuración, perfiles y descargas como paneles internos.
- Menú lateral adaptado a la referencia visual.
- Configuraciones funcionales de motor, animaciones, pestañas y descargas.
- Perfiles locales desplegables.
- Menor reconstrucción durante el progreso de carga.
- README actualizado automáticamente.

## 0.1.10 — Navigation, History & Settings

- Favoritos reemplaza a Esenciales.
- Historial con búsqueda y papelera.
- Botón Atrás navega páginas y pestañas antes de confirmar la salida.
- Espacios Personal, Trabajo y Educación restaurados.
- Barra de dirección visible en páginas y más redondeada.
- Menú lateral sin buscador, con logo, nombre e Inicio.
- Paneles secundarios vuelven al menú principal.
- Aviso visual mejorado para descargas.
- Corrección de superficies negras de GeckoView.
- Nombre e icono actualizados a Zen Browser.
- Motor de búsqueda desplegable y más de veinte configuraciones reales.

## 0.1.11 — Zen Home & Mechanical Key

- Corrige los métodos faltantes `createSidebarShortcutGrid()` y `sidebarBottomAction()`.
- Conserva Favoritos, Historial, Descargas, perfiles y espacios de v0.1.10.
- Nuevo fondo bonsái en la pantalla de inicio.
- Fondo adaptable a vertical y horizontal.
- Tecla Z con volumen, bisel, desplazamiento y animación física.
- Clic mecánico corto mediante SoundPool.
- Respuesta háptica respetando la configuración de Android.
- Animación de despertar una vez por sesión.
- Movimiento ambiental lento y configurable.
- Cuatro ajustes nuevos para fondo, movimiento, sonido y vibración.
- Artefacto: `ZenBrowser-v0.1.11-debug`.

## 0.1.12 — Stable Paint & Live Downloads

- Reemplaza la recuperación visual basada en invalidaciones repetidas por eventos reales del compositor de GeckoView.
- Añade una cubierta de primera pintura que evita mostrar superficies grises o incompletas.
- Impide que la pantalla de Inicio aparezca brevemente durante recargas y redirecciones.
- Unifica el botón Atrás y el gesto Atrás moderno de Android.
- Añade progreso, porcentaje, velocidad y tamaño en tiempo real para descargas.
- Actualiza automáticamente el panel de Descargas mientras está abierto.
- Mantiene activa únicamente la pestaña visible y suspende multimedia en segundo plano.
- Añade mantenimiento manual y automático de cachés temporales.
- Añade modo escritorio por pestaña y como valor predeterminado opcional.
- Rediseña la barra superior y compacta la interfaz vertical.
- Reduce los accesos rápidos y centra el selector de espacios con iconos propios.
- Mejora el sonido mecánico de la tecla Z.
- Añade un fondo horizontal específico suministrado por el usuario.
- Incrementa `versionCode` a 13 y `versionName` a 0.1.12.
- Artefacto: `ZenBrowser-v0.1.12-debug`.

## 0.1.13 — Fluid Navigation, Editable Access & Web Context

- Sustituye el círculo central de carga por progreso inferior, resplandor y cubierta AMOLED.
- Conserva el fotograma anterior durante cambios de pestaña para evitar gris y destellos.
- Usa la primera pintura de GeckoView para retirar la transición.
- Muestra aviso de carga lenta solo después de varios segundos.
- Mantiene la misma GeckoSession y no recarga pestañas existentes al seleccionarlas.
- Añade accesos rápidos persistentes y separados por espacio.
- Permite crear, editar, eliminar y reorganizar accesos.
- Detecta favicons y manifiestos web con caché visual limitada.
- Añade transición lateral entre Personal, Trabajo y Educación.
- Reorganiza Configuración en nueve categorías con buscador y restablecimiento.
- Añade menú contextual AMOLED para imágenes, enlaces, video y audio.
- Permite previsualizar, descargar, copiar dirección, copiar archivo y compartir.
- Usa FileProvider para compartir contenido temporal de forma segura.
- Rediseña notificaciones y tarjetas de descargas con progreso en el borde inferior.
- Actualiza velocidad, porcentaje y tamaño sin reconstruir toda la lista.
- Suaviza la velocidad mostrada para evitar saltos bruscos.
- Reduce altura de pestañas y accesos rápidos.
- Artefacto: `ZenBrowser-v0.1.13-debug`.

## 0.1.13 hotfix — Safe Area

- Restaura el método `installSafeAreaInsets()` eliminado accidentalmente.
- Corrige la compilación de `MainActivity`.

## 0.1.14 — Stability & Rendering

- Corrige cierres en el menú contextual y en el editor de accesos.
- Mejora la retención de superficie durante navegación y cambios de pestaña.
- Reduce y contiene el aviso visual de descargas.
- Endurece la carga asíncrona de imágenes frente a vistas destruidas.

## 0.1.15 — Surface Recovery & Downloads

- Introduce una máquina de estados de renderizado verificable.
- Reactiva de forma determinista GeckoSession, GeckoView y el compositor.
- Detecta superficies uniformes negras, grises o blancas y ejecuta recuperación acotada.
- Evita retirar la captura anterior hasta confirmar una superficie válida.
- Elimina pestañas vacías duplicadas y bloquea dobles pulsaciones.
- Libera inmediatamente las capturas de transición.
- Reduce y limita cachés visuales y temporales sin borrar automáticamente toda la caché web.
- Compacta las tarjetas y la notificación de descargas.
- Añade soporte controlado para URI data: y mensajes claros para esquemas incompatibles.
- Añade trazas de onPageStart, onFirstComposite, onFirstContentfulPaint, onPageStop, onPause y onResume.
- Artefacto esperado: `ZenBrowser-v0.1.15-debug`.

## v0.1.16 — Sidebar, Settings Tab & Context Safety

- Rediseña el panel lateral como una tarjeta flotante con márgenes y esquinas completas.
- Mueve Configuración a la esquina inferior izquierda.
- Añade un selector segmentado Personal / Trabajo / Estudio en el centro inferior.
- Mantiene Descargas en la esquina inferior derecha.
- Mueve Favoritos, Historial y Perfil a una fila secundaria compacta.
- Elimina el selector desplegable grande de espacios.
- Convierte Configuración en una pestaña interna nativa `zen://settings`.
- Garantiza una sola pestaña de Configuración sin crear una GeckoSession.
- Hace interactiva toda la barra de búsqueda de la pantalla de Inicio.
- Elimina la miniatura automática del menú contextual.
- Filtra acciones no seguras, bloquea `blob:` y conserva descarga controlada para `data:`.
- Cancela diálogos contextuales al pausar la actividad.
- Endurece callbacks asíncronos de copiar y compartir imágenes.
- Artefacto esperado: `ZenBrowser-v0.1.16-debug`.

