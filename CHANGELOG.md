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

## v0.1.17 — Theme & Interface Refinement

- Corrige la apertura y el envío de búsquedas desde la barra superior y la pantalla de Inicio.
- Acepta las acciones IME Buscar, Ir, Hecho y Enter sin crear pestañas vacías.
- Rediseña Configuración como una lista limpia, sin tarjetas pesadas ni recuadro en el encabezado.
- Añade selector Noche / Día en Apariencia y un acceso Luna/Sol en el panel lateral.
- Incorpora una paleta clara global para barras, paneles, pestañas, diálogos y páginas internas.
- Reorganiza Inicio, Favoritos, Historial, Perfil y tema en la zona superior del panel.
- Reduce el dock inferior y deja recuadro únicamente alrededor de Personal / Trabajo / Estudio.
- Escala el espacio activo y reduce visualmente los dos espacios inactivos.
- Cambia entre espacios actualizando solo la etiqueta, la lista de pestañas y el selector inferior.
- Rediseña por completo el editor Añadir/Editar acceso con un diálogo propio y sin fondo gris exterior.
- Conserva las protecciones del menú contextual introducidas en v0.1.16.
- Artefacto esperado: `ZenBrowser-v0.1.17-debug`.

## v0.1.18 — Web Compatibility & Android Integration

- Añade selección nativa, copiar, cortar, pegar y seleccionar todo en la dirección.
- Corrige el icono de Apariencia con una paleta vectorial y tintado explícito.
- Reconstruye el icono adaptativo con fondo, primer plano y capa monocromática separados.
- Elimina bordes negros del icono mostrado dentro del panel lateral.
- Aplica Noche/Día a sugerencias, recientes, diálogos y superficies temporales.
- Comunica a Gecko el esquema de color preferido para páginas compatibles.
- Usa un lienzo web acorde al tema y retrasa la cubierta hasta una pintura estable.
- Registra target=_blank y window.open() como nuevas pestañas Gecko.
- El editor de accesos conserva únicamente Nombre y Dirección.
- El favicon se resuelve automáticamente en segundo plano.
- Completa ACTION_VIEW, WEB_SEARCH, APP_BROWSER y el rol de navegador.
- Añade publicación automática mediante install.sh --release.
- Artefacto de Actions: `ZenBrowser-v0.1.18-debug`.
- APK de Release: `ZenBrowser-v0.1.18-debug.apk`.

## v0.1.19 — Navigation, Landscape & Day Polish

- Restaura la identidad de la Z con un primer plano adaptativo fiel al icono-tecla.
- Unifica launcher, icono monocromático y marca interna sin esquinas negras.
- Sustituye la dirección por un campo nativo que conserva doble toque, pulsación larga,
  seleccionar, seleccionar todo, copiar, cortar, pegar y controles de selección.
- Elimina el procesamiento doble de ACTION_VIEW que podía crear pestañas repetidas.
- Entrega la GeckoSession hija antes de actualizar la interfaz y mantiene su foco durante
  la cadena inicial de redirecciones.
- Protege solicitudes emergentes duplicadas sin recargar la pestaña de origen.
- Mantiene la captura o cubierta anterior cuando una navegación falla temporalmente.
- Verifica el fotograma antes de retirar definitivamente la transición.
- Reemplaza el rail horizontal por un panel fijo con pestañas, título, URL, cierre,
  espacios, Configuración y Descargas.
- Integra los fondos claros proporcionados por el usuario: vertical y horizontal.
- Conserva el fondo oscuro original para Noche.
- Actualiza README, CHANGELOG, documentación de Termux y matriz de pruebas.
- Artefacto: `ZenBrowser-v0.1.19-debug`.
- Release: `ZenBrowser-v0.1.19-debug.apk`.

## v0.1.19 — Resource Linking Hotfix

- Corrige la compilación de `bg_landscape_tab.xml`.
- Añade `zen_surface_selected` a la paleta Noche.
- Añade `zen_surface_selected` a la paleta Día.
- Valida referencias locales `@color/zen_*` antes del commit.
- No cambia `versionCode` ni `versionName` porque la APK v0.1.19
  no llegó a generarse.

## v0.1.20 — Search, Selection, Rotation & Redirect Stability

- Muestra los fondos Día vertical y horizontal proporcionados por el usuario.
- Corrige la condición que ocultaba siempre el fondo cuando el tema era Día.
- Añade una capa clara independiente para no oscurecer el bonsái.
- Separa definitivamente el menú flotante vertical del panel fijo horizontal.
- Reconstruye el panel cuando cambia el modo de orientación, no solo su anchura.
- Cierra popups antiguos antes de recalcular la ventana.
- Usa una cobertura del color del tema durante la transición de orientación.
- Conserva la misma GeckoSession y verifica el fotograma después de rotar.
- Mantiene un borrador de dirección por pestaña, incluido cursor y selección.
- Sincroniza en tiempo real Inicio, barra superior y editor de búsqueda.
- Conserva el texto al cerrar el buscador y al girar el dispositivo.
- Instala explícitamente el menú flotante de selección de GeckoView.
- Habilita copiar, cortar, pegar y seleccionar todo dentro de páginas.
- Reemplaza el temporizador de popups por estados de sesión y redirección.
- Reutiliza la misma GeckoSession ante solicitudes repetidas válidas.
- Evita devolver null durante una repetición legítima de window.open().
- La pestaña hija deja de robar el foco cuando el usuario cambia manualmente.
- Actualiza README, CHANGELOG, Termux y la matriz de pruebas.
- Artefacto: `ZenBrowser-v0.1.20-debug`.
- Release: `ZenBrowser-v0.1.20-debug.apk`.

## v0.1.20 — SearchInput Type Hotfix

- Corrige el error de compilación en `MainActivity.java`.
- Cambia el tipo del campo `searchInput` de `EditText` a
  `ZenAddressEditText`.
- Permite compilar la llamada a `setSelectionListener()`.
- Mantiene `versionCode 21` y `versionName 0.1.20` porque la APK anterior
  no llegó a generarse.

## v0.1.21 — Unopened GeckoSession Fix

Esta versión corrige exclusivamente el cierre al abrir una ventana, pestaña
secundaria o redirección que invoque `onNewSession`.

- La sesión hija se crea nueva y permanece sin abrir.
- Se configuran delegates y ajustes antes de entregarla a Gecko.
- Zen no llama `session.open()` ni `loadUri()` dentro de `onNewSession`.
- Se elimina la reutilización de una sesión secundaria ya abierta.
- La pestaña mantiene una referencia fuerte a la sesión devuelta.
- Una protección defensiva rechaza cualquier sesión abierta sin cerrar la app.
- No se modifican interfaz, temas, búsqueda, descargas ni paneles.
- Artefacto: `ZenBrowser-v0.1.21-debug`.
- Release: `ZenBrowser-v0.1.21-debug.apk`.

## v0.1.22 — Liquid Glass, diseño y utilidades

- Fix visual del panel lateral para evitar el recuadro negro.
- Ajuste de fondos horizontales del inicio para mejor centrado.
- Mejora de selección/copiar/pegar en barras de búsqueda y dirección.
- Base beta para estilo Cristal líquido.
- Permisos para micrófono y mejor preparación para subida de archivos.
- Nuevo icono adaptativo con el logo v0.1.22.

## v0.1.24 — Glass, carga de archivos y pulido responsive

- Cristal líquido unificado mediante una receta global de superficies.
- Desenfoque de captura única sin aplicar blur continuo sobre GeckoView.
- Carga web estable sin recargar o reemplazar la GeckoSession al volver del selector.
- Compatibilidad con PDF, ZIP, JSON, TXT, documentos, archivos desconocidos y selección múltiple.
- Protección contra callbacks antiguos que duplicaban el menú lateral al cambiar tema.
- Icono adaptativo centrado ópticamente y unificado con la marca interna.
- Fondo horizontal FOCAL_FIT para evitar zoom excesivo del bonsái.

## v0.1.25 — Liquid Glass & Search Reconstruction

- Material Liquid Glass recalibrado para Día y Noche.
- Eliminada la captura borrosa duplicada detrás del panel lateral.
- Reconstrucción del editor de dirección con selección nativa de Android.
- Limpieza atómica de paneles al cambiar tema/orientación.
- Icono adaptativo corregido con la Z exacta proporcionada.
- versionCode 26 / versionName 0.1.25.
