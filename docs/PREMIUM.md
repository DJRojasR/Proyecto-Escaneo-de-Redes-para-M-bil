# Lupa de Red Premium: cómo activarlo en Google Play

La app ya trae todo el código de las compras. Para que funcione de verdad tienes que crear
los productos en Play Console **con los mismos IDs** que usa el código
(`app/src/main/java/com/lupadered/app/premium/PremiumManager.kt`).

## 1. Requisitos previos

1. **Perfil de comerciante (payments profile)**: Play Console › Configuración › Perfil de pagos.
   Sin él no puedes vender nada. Tus ingresos llegan a tu cuenta bancaria desde aquí.
2. Subir al menos **una versión de la app que ya incluya la librería de facturación**
   (esta versión) a un track de prueba (prueba interna). Play Console no deja crear
   productos hasta que detecta la librería en un AAB subido.

## 2. Crear la suscripción mensual

Play Console › tu app › **Monetizar › Productos › Suscripciones › Crear suscripción**

| Campo | Valor |
|---|---|
| ID del producto | `premium_mensual` |
| Nombre | Lupa de Red Premium |
| Plan base: ID | `mensual` |
| Plan base: tipo | Renovación automática, período de facturación **1 mes** |
| Precio | S/ 4.90 (Perú). Usa "Establecer precios" para que Google calcule los demás países |

Luego, dentro del plan base, **Agregar oferta**:

| Campo | Valor |
|---|---|
| ID de la oferta | `prueba-7-dias` |
| Elegibilidad | Adquisición de clientes nuevos |
| Fase | Prueba gratuita · 7 días |

Activa el plan base y la oferta.

## 3. Crear el pago único "Pro de por vida"

Play Console › **Monetizar › Productos › Productos únicos (In-app) › Crear producto**

| Campo | Valor |
|---|---|
| ID del producto | `premium_de_por_vida` |
| Nombre | Lupa de Red Pro de por vida |
| Precio | S/ 14.90 |

Actívalo.

> Los IDs **no se pueden cambiar** después de crearlos. Si quieres otros, cámbialos primero
> en `PremiumManager.kt` (constantes `SUB_MONTHLY`, `BASE_PLAN_MONTHLY` y `LIFETIME`).

## 4. Probar sin pagar de verdad

- **Durante el desarrollo** (versión debug, la que instala Android Studio): en
  *Ajustes y privacidad* aparece el interruptor **"Simular Premium (solo pruebas)"**.
  Activa todas las funciones Premium sin Google Play. **No existe en la versión que subes a Play.**
- **Compras reales de prueba**: Play Console › Configuración › **Pruebas de licencias** › agrega tu
  correo de Gmail. Instala la app desde el enlace de **prueba interna** (no desde Android Studio).
  Verás "Tarjeta de prueba, siempre se aprueba" y no se te cobrará. En pruebas, una suscripción
  mensual se renueva cada 5 minutos para que veas renovaciones y vencimientos rápido.

## 5. Qué hace la app con las compras

- Al abrir la app y al volver a ella, pregunta a Google Play qué compras tiene el usuario
  (así se recupera Premium al reinstalar o cambiar de celular).
- Confirma (*acknowledge*) cada compra nueva. Si una compra no se confirma en 3 días,
  Google la reembolsa automáticamente.
- Guarda el estado en el teléfono para que Premium funcione sin Internet.
- Si la suscripción se cancela y vence, Premium se desactiva en la siguiente comprobación,
  pero **no se borra ningún dato**: grupos, historial y redes siguen ahí si vuelve a suscribirse.

> Mejora futura recomendada: verificar las compras en un servidor propio con la
> Google Play Developer API para evitar fraudes. Para empezar no es obligatorio.

## 6. Funciones Premium y dónde están en el código

| Requisito | Función | Archivo principal |
|---|---|---|
| RF-20 / RF-21 | Comprar y restaurar | `premium/PremiumManager.kt`, `ui/screens/PremiumScreen.kt` |
| RF-22 | Sin anuncios | `ads/AdsManager.kt` |
| RF-23 | Candados con vista previa | `ui/components/Premium.kt` |
| RF-24 / RF-26 | Escaneo automático y diario | `monitor/MonitorScheduler.kt`, `monitor/BackgroundScanWorker.kt` |
| RF-25 | Alerta de intrusos | `monitor/Notifier.kt` |
| RF-27 | Historial completo (gratis: 7 días) y línea de tiempo | `ui/MainViewModel.kt`, `ui/screens/DeviceDetailScreen.kt` |
| RF-28 | ~~Puertos extendidos y por rango~~ (retirado: basta con "Volver a analizar") | — |
| RF-29 | Revisión de seguridad | `knowledge/SecurityReview.kt` |
| RF-30 | Monitor de latencia | `net/LatencyProbe.kt`, `ui/screens/PremiumToolScreens.kt` |
| RF-32 | Grupos e íconos | `data/DeviceRepository.kt`, `ui/components/DeviceIcons.kt` |
| RF-33 | Mis redes | `ui/screens/PremiumToolScreens.kt` (`NetworksScreen`) |
| RF-34 | Exportar PDF / CSV | `export/ReportExporter.kt` |
| RF-46 | Prueba de velocidad (gratis 2 por día; Premium ilimitada con historial y gráfico) | `net/SpeedTest.kt`, `ui/screens/SpeedTestScreen.kt` |
| RF-35 | Widget y temas | `widget/DevicesWidget.kt`, `ui/theme/Theme.kt` |
| RF-47 | Vulnerabilidades del router (Telnet, UPnP, HTTP sin cifrar, firmware viejo, contraseña de fábrica) | `knowledge/RouterKnowledge.kt`, `net/RouterAuthProbe.kt`, `knowledge/SecurityReview.kt` |
| RF-48 | ~~Cámaras en tu WiFi~~ (retirada como herramienta; la revisión de seguridad sigue avisando de posibles cámaras) | `knowledge/CameraKnowledge.kt` |
| RF-49 | Alertas personalizadas por dispositivo (desconexión, puerto nuevo, reconexión, cambio de IP) | `monitor/AlertEngine.kt`, `data/DeviceRepository.kt` (tabla `alert_rules`), `ui/screens/DeviceDetailScreen.kt` |
| RF-50 | Mapa de calor WiFi por pisos: realidad aumentada si el teléfono la soporta; si no, modo caminata (pasos + brújula) o punto por punto | `ui/HeatMapViewModel.kt`, `ui/screens/HeatMapScreen.kt`, `ar/ArHeatScreen.kt`, `ar/ArCameraRenderer.kt`, `ar/ArHeatViewModel.kt`, `data/DeviceRepository.kt` (tablas `heat_floors`, `heat_points`, BD v6) |

RF-31 (analizador de canales WiFi) queda para más adelante: necesitaría el permiso de ubicación.

### RF-47: vulnerabilidades del router

Para el equipo marcado como `is_gateway`, la revisión de seguridad avisa si tiene Telnet (23),
FTP (21), panel sin HTTPS, UPnP activo, gestión remota TR-069 (7547) o firmware/servidores web
antiguos conocidos (`RouterKnowledge.outdatedFirmware`, a partir del encabezado `Server` de SSDP).
También sugiere revisar la contraseña si la marca del router suele traer una clave de fábrica
conocida (`RouterKnowledge.factoryDefaultBrand`), sin probar nada.

Además, con el **consentimiento explícito del usuario** (diálogo en `SecurityScreen`), se puede
probar un puñado de credenciales de fábrica comunes contra el panel del router
(`RouterAuthProbe.probeDefaultCredentials`, máx. 10 combinaciones, solo contra el gateway de la
red actual, por HTTP/HTTPS con Basic Auth). No se guarda ninguna contraseña, solo si alguna fue
aceptada.

### RF-48: cámaras (retirada como herramienta)

La pantalla dedicada se retiró para no prometer algo que una app no puede garantizar (solo ve
cámaras conectadas a la misma red). `CameraKnowledge` se mantiene: la revisión de seguridad
sigue avisando si un equipo de la red parece una cámara o un grabador (puertos 554, 8554,
37777, 34567, 9527 o fabricantes como Hikvision, Dahua, Tapo…).

### RF-49: alertas personalizadas por dispositivo

Desde la ficha de un equipo (`DeviceDetailScreen`) el usuario puede crear reglas: avisar si el
equipo se desconecta más de N minutos, si abre un puerto nuevo, si vuelve a conectarse o si cambia
de IP. Las reglas se guardan en la tabla `alert_rules` (migración v4 de la base) y se evalúan en
cada vuelta de la vigilancia automática (`BackgroundScanWorker`) con `AlertEngine`, que usa
`alert_state` para no repetir la misma alerta. La granularidad depende del intervalo de vigilancia
elegido (mínimo 15 minutos): una regla de desconexión más corta que el intervalo no puede dispararse
más seguido que eso. Las notificaciones usan el canal `alertas_dispositivo`.

## 7. Cambios en la ficha de Google Play

- **Seguridad de los datos**: agrega "Historial de compras" (lo procesa Google Play) si el formulario
  lo pide, y mantén "datos no compartidos" para la información de la red.
- **Descripción**: menciona que la app es gratis con compras dentro de la app.
- **Política de privacidad**: ya está actualizada en `docs/POLITICA_DE_PRIVACIDAD.md`; vuelve a publicarla.

## Prueba de velocidad y la ficha de Google Play

La prueba usa M-Lab, que publica la IP pública de quien hace la prueba. En el formulario
**Seguridad de los datos** de Play Console declara:
- **Datos recopilados:** "Dirección IP" no aparece como tipo propio; márcalo en **Ubicación aproximada**
  solo si Google lo pide para IPs. Indica que se **comparte con terceros (M-Lab)** con fines de
  **análisis/funcionalidad de la app**, y que es **opcional** (el usuario lo acepta antes de la primera prueba).
- Revisa el texto con calma al llenar el formulario: si tienes dudas, describe exactamente lo que hace la app.

### RF-50: mapa de calor WiFi

El plano es un recuadro 3:4 que representa la casa vista desde arriba. El usuario toca dónde está
parado y pulsa "Medir aquí": durante 4 segundos se lee la señal (RSSI) 8 veces y se hace ping al
router 3 veces; se guarda la mediana. El mapa se pinta interpolando los puntos por distancia
inversa (IDW) sobre una cuadrícula de 48×64 y se desvanece lejos de las mediciones, para no
inventar señal donde no se midió. Cada red tiene sus propios pisos; cada piso guarda sus puntos y,
opcionalmente, dónde está el router. Con varios pisos se muestra una comparación y se sugiere un
repetidor o nodo mesh en el piso más débil.

**Recorrido automático.** Al tocar el punto de partida y mirar hacia la parte de arriba del plano:

- *Realidad aumentada* (si `ArCoreApk.checkAvailability` dice que el teléfono es compatible):
  ARCore da la posición del teléfono en metros; cada ~30 cm se guarda la señal y se pinta un disco
  de color sobre el piso real (proyectando con las matrices de vista y proyección de la cámara).
  Las muestras se pasan al plano usando el ancho del piso (`width_m`). ARCore es opcional en el
  manifiesto (`com.google.ar.core = optional`), así que la app se instala en cualquier teléfono.
- *Vista del mapa*: el plano se ajusta solo para mostrar todo lo medido aunque el recorrido salga
  del recuadro de la casa (`ui/heat/HeatGeometry.kt`). Se pinta con baldosas de 25 cm, marca
  dónde está la mejor y la peor señal y tiene una escala en metros.
- *Exportar*: "Compartir mapa" y "Guardar imagen" generan una imagen PNG con el mapa, el router,
  la leyenda y el resumen (`ui/heat/HeatExport.kt`), compartida mediante `FileProvider`.
- *Modo caminata* (si no hay RA): pasos detectados con el acelerómetro (picos sobre 10,8 m/s²),
  pasos de 0,65 m y dirección con el vector de rotación del juego (giroscopio). Tocar el plano
  durante el recorrido corrige la posición si se desvía.
