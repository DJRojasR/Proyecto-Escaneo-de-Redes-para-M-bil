# Publicar "Lupa de Red" en Google Play

Lista de pasos en orden. Los requisitos de Google cambian; revisa la Play Console en el momento de publicar.

## 1. Antes de compilar la versión final

- [ ] **Decide el `applicationId`** en `app/build.gradle.kts` (ahora `com.lupadered.app`). Es permanente: no
      se puede cambiar después de publicar.
- [ ] **Nivel de API**: el proyecto ya apunta a Android 16 (API 36), que Google Play exige desde el 31 de agosto
      de 2026 para apps nuevas y actualizaciones.
- [ ] **AdMob**: crea la app en <https://admob.google.com>, crea un bloque de anuncios *Intersticial* y pon los IDs
      en `gradle.properties` (`ADMOB_APP_ID`, `ADMOB_INTERSTITIAL_ID`). Vincula AdMob con tu app de Play cuando
      ya esté publicada.
- [ ] **Mensaje de consentimiento**: en AdMob > *Privacidad y mensajes*, crea un mensaje GDPR (y si quieres, de
      estados de EE. UU.). La app ya muestra ese formulario automáticamente.
- [ ] **Política de privacidad**: publica `docs/POLITICA_DE_PRIVACIDAD.md` en una página pública (por ejemplo
      Google Sites o GitHub Pages) y pon la URL en `gradle.properties` (`PRIVACY_POLICY_URL`).
- [ ] **app-ads.txt**: AdMob te pedirá publicar un archivo `app-ads.txt` en el sitio web que indiques en la ficha.
- [ ] Sube `versionCode` en cada actualización.

## 2. Llave de firma

1. Android Studio > **Build > Generate Signed App Bundle / APK…** > *Android App Bundle* > *Create new…*
2. Guarda el `.jks` en esta carpeta (ya está en `.gitignore`) y copia `keystore.properties.example` como
   `keystore.properties` con tus datos.
3. **Guarda una copia de la llave y las contraseñas fuera del computador.**
4. En Play Console usa **Firma de apps de Google Play** (recomendado): tu llave será solo la "llave de subida".

Compila con **Build > Generate Signed App Bundle** (variante `release`). Resultado: `app/release/app-release.aab`.
Sube también el archivo `mapping.txt` (Play lo pide para descifrar los informes de errores).

## 3. Play Console

- [ ] Crea la cuenta de desarrollador y completa la **verificación de identidad**.
- [ ] Si tu cuenta es **personal y nueva**, Google exige una **prueba cerrada** con un grupo mínimo de testers
      durante un tiempo antes de poder publicar en producción. Revisa el requisito vigente en la Play Console
      (en los últimos años ha sido 12 testers durante 14 días). Puedes invitar a familiares y amigos.
- [ ] Crea la app: idioma predeterminado *Español (Latinoamérica)*, tipo *App*, *Gratis*.

### Contenido de la app (Política)

| Sección | Qué responder |
|---|---|
| Política de privacidad | Tu URL pública. |
| Anuncios | **Sí, contiene anuncios.** |
| Acceso a la app | Todas las funciones están disponibles sin acceso especial. |
| Clasificación de contenido | Categoría *Utilidades / Herramientas*. Sin violencia, sexo, apuestas ni contenido generado por usuarios. |
| Público objetivo | **18 años o más** (evita la política de Familias; la app no está pensada para niños). |
| App de noticias / salud / financiera | No. |
| ID de publicidad | **Sí**, la app usa el ID de publicidad (lo usa AdMob para anuncios y medición). |

### Seguridad de los datos

La app **no envía** la información de tu red a ningún servidor; todo se guarda en el teléfono.
Lo que se declara es lo que recopila el SDK de anuncios de Google (AdMob). Según la guía de Google para el SDK
de anuncios (verifícala en <https://developers.google.com/admob/android/privacy/play-data-disclosure>):

| Tipo de dato | ¿Se recopila? | ¿Se comparte? | Para qué |
|---|---|---|---|
| Ubicación aproximada (derivada de la IP pública) | Sí | Sí | Publicidad, análisis, prevención de fraude |
| Interacciones con la app | Sí | Sí | Publicidad, análisis |
| Registros de fallos y diagnóstico | Sí | Sí | Análisis, prevención de fraude |
| ID del dispositivo u otros (ID de publicidad) | Sí | Sí | Publicidad, análisis, prevención de fraude |

- Los datos se transmiten cifrados: **Sí**.
- El usuario puede pedir que se borren: los datos locales se borran en *Ajustes > Borrar todos los datos*
  o desinstalando la app.
- Los datos de la red local (IPs, nombres, puertos) **no** se recopilan en el sentido de Play, porque nunca salen
  del dispositivo.

### Permisos

Ninguno es "peligroso" ni requiere declaración especial: `INTERNET`, `ACCESS_NETWORK_STATE`,
`ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`. La app **no pide ubicación** (por eso no muestra el nombre
del WiFi; leer el SSID exige permiso de ubicación en Android, y eso complica la revisión de Play).

## 4. Ficha de Play Store (borrador)

**Nombre (máx. 30):** `Lupa de Red: escáner WiFi`

**Descripción breve (máx. 80):**
`Mira quién está en tu WiFi y entiende tu red. Escaneos ilimitados y rápidos.`

**Descripción completa:**

```
¿Quién está conectado a tu WiFi? Lupa de Red lo descubre en segundos y, a diferencia de otras apps, te explica lo que estás viendo en palabras sencillas.

DETECTA MÁS DISPOSITIVOS
Combinamos varios métodos a la vez (conexiones, ping, Bonjour, UPnP y NetBIOS) y revisamos dos veces a los equipos que no responden, como celulares en reposo o PCs con el ping bloqueado.

ENTIENDE TU RED
Cada dato tiene su explicación: qué es una IP, qué significa un puerto abierto, si la latencia es normal y qué hacer si algo merece revisión.

IP MANAGER
Todas las direcciones de tu red en una tabla: en uso, vistas antes y sin respuesta. Ideal para elegir una IP fija sin choques.

NOMBRES QUE NO SE PIERDEN
Ponle nombre a tus equipos ("TV de la sala", "Celular de mamá"). Se guardan aunque vuelvas a escanear y aunque el equipo cambie de IP.

AVISO DE DISPOSITIVOS NUEVOS E HISTORIAL
Te mostramos qué equipo se conectó por primera vez, cuál se fue, cuál volvió y cuál cambió de IP.

ANALIZA UNA IP
¿Conoces una dirección? Escríbela y revisa si responde, cuánto tarda, cómo se llama y qué puertos tiene abiertos, con su nivel de riesgo explicado.

DIAGNÓSTICO DE CONEXIÓN
¿Internet va lento? Descubre si el problema es la señal WiFi, el router, tu proveedor o el DNS.

SIN LÍMITES Y SIN ANUNCIOS QUE INTERRUMPAN
Escaneos ilimitados. Como máximo un anuncio cada tres escaneos, y el escaneo sigue trabajando detrás.

PRIVADA
La información de tu red se queda en tu teléfono.

Usa la app solo en redes propias o en las que tengas permiso.
```

**Categoría:** Herramientas. **Etiquetas sugeridas:** WiFi, red, seguridad.

**Gráficos** (en `docs/play-store/`):
- Ícono 512×512 → `icono-512.png`
- Gráfico de funciones 1024×500 → `grafico-funciones-1024x500.png`
- Capturas de pantalla: toma al menos 4 desde tu teléfono (pantallas Red, IP Manager, Detalle con puertos y
  Diagnóstico). Antes de capturar, ponles nombres de ejemplo a tus equipos para no mostrar datos reales.

## 5. Antes de enviar a revisión

- [ ] Prueba la versión *release* (no debug) en un teléfono real: anuncios reales cargando, consentimiento,
      escaneo, detalle, borrar datos.
- [ ] Revisa en *Play Console > Informe previo al lanzamiento* que no haya bloqueos.
- [ ] **No hagas clic en tus propios anuncios reales** (AdMob puede suspender la cuenta). Para probar, registra tu
      teléfono como *dispositivo de prueba* en AdMob.
