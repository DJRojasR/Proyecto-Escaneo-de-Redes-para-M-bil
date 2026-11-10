# Lupa de Red

App Android para descubrir los dispositivos de tu red WiFi y **entender** lo que estás viendo.

> *"No solo escanees tu red: entiende lo que pasa en ella."*

## Cómo abrir el proyecto

1. Usa **Android Studio Meerkat (2024.3.1) o más reciente**. El proyecto usa Android Gradle Plugin 8.9.1,
   Gradle 8.11.1 y Kotlin 2.2.10, y compila para Android 16 (API 36), como exige Google Play.
   Si Android Studio te ofrece "actualizar el Android Gradle Plugin", puedes rechazarlo: no es necesario.
2. `File > Open…` y elige esta carpeta (`Proyecto Escaneo de Redes`).
3. Espera a que termine la sincronización de Gradle (la primera vez descarga dependencias).
4. Conecta tu teléfono por USB con la *depuración USB* activada y pulsa **Run ▶**.

> Usa un **teléfono real conectado a tu WiFi**. En el emulador, la "red" es virtual y solo verás el router del emulador.

Para ejecutar las pruebas del motor de red: `gradlew test` (o clic derecho en `app/src/test` → *Run tests*).

## Qué hace

| Pantalla | Para qué sirve |
|---|---|
| **Red** | Escanea y muestra los dispositivos en línea y los que se vieron antes ("visto hace 2 h"). Avisa cuando se conecta uno nuevo. |
| **IPs** (IP Manager) | Todas las IPs del rango: en uso, vistas antes y sin respuesta. Toca una IP libre para analizarla. |
| **Historial** | Qué equipo apareció, se fue, volvió o cambió de IP, y cada escaneo realizado. |
| **Herramientas** | Analizar una IP manualmente, diagnóstico de conexión, glosario "¿Qué estoy viendo?" y ajustes. |
| **Detalle** | Nombre personalizado, tipo, "equipo conocido", cómo se detectó, servicios, análisis de puertos con explicación y nivel de riesgo. |

Cada dato técnico tiene un botón **(i)** con su explicación en lenguaje sencillo.

## Cómo evita "no detectar" dispositivos

El motor (`app/src/main/java/com/lupadered/app/net/`) combina varios métodos **a la vez**:

1. **TCP no bloqueante (NIO)**: hasta 1.024 conexiones en paralelo a 16 puertos comunes. Si un puerto
   está cerrado, el equipo responde "rechazado" al instante, y eso también prueba que existe
   (así se encuentran PCs con el ping bloqueado).
2. **Ping (ICMP)**, 128 en paralelo.
3. **NetBIOS**: nombre de PCs con Windows y NAS.
4. **mDNS / Bonjour**: iPhone, Mac, Chromecast, impresoras y parlantes anuncian su nombre y servicios.
5. **SSDP / UPnP**: TVs, consolas y routers, y su ficha (marca y modelo).
6. **Segunda pasada** con más paciencia solo para las IPs que no respondieron (celulares en reposo).
7. **Nombres**: DNS inverso del router y consulta mDNS directa a cada equipo.

Los resultados aparecen **en vivo** mientras se escanea, y los dispositivos conocidos se muestran **al instante**
al abrir la app (desde la base de datos), así que no hay que esperar a ver la red.

Sin MAC (Android 10+ no lo permite), los equipos se reconocen por su **nombre en la red** aunque cambien de IP,
y por el nombre que les pone el usuario.

## Anuncios (AdMob)

Lógica en `ads/AdsManager.kt`:

- Los primeros 3 escaneos, sin anuncios.
- Después, máximo **1 anuncio cada 3 escaneos** y con un mínimo de 3 minutos entre uno y otro.
- El anuncio aparece al tocar *Escanear* y **el escaneo corre detrás**: al cerrarlo, los resultados ya están.
- Nunca interrumpe resultados ni detalles. Si no hay anuncio cargado, no se espera.
- Incluye el formulario de consentimiento (UMP) obligatorio en Europa.

Mientras desarrollas, se usan los **IDs de prueba** de Google. Los builds *debug* siempre usan anuncios de prueba.
Para publicar, pon tus IDs reales en `gradle.properties` (ver comentarios allí).

## Estructura

```
app/src/main/java/com/lupadered/app/
├── net/          Motor de escaneo (TCP NIO, ping, mDNS, SSDP, NetBIOS, DNS, diagnóstico)
├── data/         Base de datos SQLite: dispositivos, escaneos y eventos
├── knowledge/    Explicaciones: puertos, tipos de dispositivo, glosario
├── ads/          Anuncios y consentimiento
└── ui/           Pantallas en Jetpack Compose (Material 3)
```

## Publicar en Google Play

Mira **[docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md)**: lista de pasos, textos para la ficha, respuestas para
"Seguridad de los datos" y una plantilla de política de privacidad en `docs/POLITICA_DE_PRIVACIDAD.md`.
Hay imágenes para la ficha en `docs/play-store/`.

## Próximas ideas

- Mapa visual de la red (router al centro).
- Exportar el inventario a CSV/PDF.
- Escaneo periódico en segundo plano con notificación de "dispositivo nuevo" (WorkManager).
- Versión Pro sin anuncios (Google Play Billing).


## Lupa de Red Premium (versión 1.1.0)

La app es gratis con anuncios y ofrece **Premium** (suscripción mensual con 7 días gratis o pago único
"Pro de por vida"): sin anuncios, vigilancia automática con alerta de intrusos, escaneo diario programado,
historial completo, análisis de puertos extendido, revisión de seguridad, monitor de latencia, grupos e
íconos, varias redes, reportes PDF/CSV, widget y temas de color.

Cómo crear los productos en Play Console y probar las compras: **[docs/PREMIUM.md](docs/PREMIUM.md)**.
En la versión debug, *Ajustes y privacidad › Simular Premium* activa todo sin pagar.
