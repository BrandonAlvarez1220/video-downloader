# Video Downloader (Android)

App Android de uso personal para descargar videos de casi cualquier sitio sin anuncios:
YouTube, TikTok, Instagram, Facebook, X/Twitter, Vimeo, Twitch, Reddit y [+1800 sitios](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

**Funciones**
- Pegas uno o varios enlaces, o usas **Compartir → Video Downloader** desde cualquier app.
  Al compartir se abre una **hoja rápida**: eliges calidad y descargas sin salir de lo que veías.
- Al pegar un enlace, la app lo **analiza sola**: miniatura, duración, canal y las resoluciones
  disponibles con el **tamaño estimado** de cada una.
- Formato **Video** (Máxima, 4K, 2K, 1080p, 720p, 480p, 360p) o **Solo audio** (MP3 con carátula).
- **Descargas múltiples**: de 1 a 5 a la vez (configurable). Puedes cancelar, reintentar o deslizar para quitar.
- Las descargas siguen con la app cerrada, con su notificación de progreso.
- Las **listas de reproducción** se separan en un video por descarga.
- **SponsorBlock**: recorta los patrocinios que el youtuber mete *dentro* del video.
- **Biblioteca**: cuadrícula o lista, búsqueda, filtros por tipo y sitio, orden y **reproductor integrado**.
  Los archivos también van a la galería: `Movies/VideoDownloader` (video) y `Music/VideoDownloader` (audio).
- **Ajustes**: calidad predeterminada, descargas simultáneas, solo Wi-Fi, tema claro/oscuro/sistema.
- yt-dlp se actualiza solo una vez al día, y también desde Ajustes.

## Diseño

- Sistema de diseño propio en `ui/theme/Theme.kt`: paleta oscura de marca (violeta → rosa),
  esquinas redondeadas generosas y tipografía con jerarquía clara.
- Componentes en `ui/components/Components.kt`: botón con degradado, mosaicos de calidad,
  insignias por plataforma, pastillas de estado, barras de progreso animadas y *skeleton loading*.
- Material 3 + Jetpack Compose: hoja inferior, botones segmentados, chips de filtro,
  deslizar para quitar, transiciones animadas y pantalla completa inmersiva en el reproductor.

## ¿Qué enlaces acepta?

| Tipo | Ejemplos | ¿Funciona? |
|---|---|---|
| Redes y plataformas de video | YouTube (incl. Shorts y listas), TikTok, Instagram (Reels/posts), Facebook, X/Twitter, Threads, Reddit, Pinterest, Vimeo, Dailymotion, Bilibili | ✅ |
| Streaming y clips | Twitch (VODs y clips), Kick, YouTube en vivo (desde el momento actual) | ✅ |
| Audio | SoundCloud, Bandcamp, Mixcloud, audio de cualquier video | ✅ |
| Archivos directos | Enlaces que terminan en `.mp4`, `.webm`, `.mov`, `.mp3`… | ✅ |
| Streams "en trozos" | `.m3u8` (HLS) y `.mpd` (DASH): muchas páginas de noticias, cursos, TV en línea | ✅ |
| Páginas con un reproductor incrustado | Blogs y sitios que embeben video con `<video>`, JW Player, etc. | ✅ casi siempre |
| Contenido privado o con login | Videos privados, +18 de YouTube, Instagram/Facebook privados | ⚠️ requiere iniciar sesión (cookies), aún no integrado |
| Plataformas con DRM | Netflix, Prime Video, Disney+, HBO Max, Spotify, Apple Music | ❌ protegido por cifrado; ninguna herramienta legal lo descarga |

La lista oficial completa (+1800 sitios) está en Ajustes → *Sitios compatibles*.

## Instalarla en tu celular (sin Android Studio)

1. Cada `push` dispara GitHub Actions (`.github/workflows/build-apk.yml`), que compila la app en la nube.
2. Desde el celular, abre **Releases** del repo → **latest** → descarga `VideoDownloader-arm64-v8a.apk`.
   - Si no instala, prueba `armeabi-v7a` (celulares muy viejos de 32 bits).
3. Android pedirá permitir "instalar apps de fuentes desconocidas" para tu navegador. Acéptalo.
4. Para actualizar, instala el APK nuevo encima. Se conserva tu biblioteca porque todos los APK
   se firman con la misma llave (`app/signing.keystore`).

> La llave de firma está en el repo a propósito para que las compilaciones en la nube sean
> instalables encima de la anterior. Como es pública, no la uses para publicar en tiendas.

## Cómo funciona (el "por qué")

No hay backend: todo corre en el teléfono.

```
┌──────────── APK ────────────────────────────────────────────┐
│ UI (Jetpack Compose) ──► ViewModel ──► DownloadController   │
│                                           │ WorkManager      │
│                                           ▼                  │
│                         DownloadWorker (1 por descarga)      │
│                                           │ ejecuta proceso  │
│                                           ▼                  │
│            python + yt-dlp + ffmpeg (binarios ARM nativos)   │
│                                           │                  │
│      carpeta privada ──copia──► MediaStore (galería)         │
│                     Room/SQLite = cola + biblioteca          │
└──────────────────────────────────────────────────────────────┘
```

- **yt-dlp** es quien "sabe" descargar de cada sitio. La librería
  [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) mete Python y ffmpeg
  compilados para ARM dentro del APK. Así cada descarga es un proceso `python yt-dlp …` local.
- **ffmpeg** une video + audio: YouTube sirve todo lo mayor a 720p en pistas separadas.
- **WorkManager**: Android mata los hilos de apps en segundo plano para ahorrar batería.
  WorkManager corre la descarga como servicio en primer plano y la retoma si se interrumpe.
- **MediaStore**: desde Android 10, las apps no escriben libremente en carpetas públicas.
  Se descarga en la carpeta privada y al final se copia a la galería, sin pedir permisos.
- Si un sitio deja de funcionar, casi siempre se arregla con **⋮ → Actualizar yt-dlp**.

| Pieza | Equivalente en .NET |
|---|---|
| Kotlin coroutines (`suspend`, `launch`) | `async/await`, `Task` |
| Jetpack Compose | Blazor / XAML, pero declarativo en código |
| ViewModel + StateFlow | MVVM con `INotifyPropertyChanged` |
| Room (`@Entity`, `@Dao`) | Entity Framework Core |
| WorkManager | Hangfire / `BackgroundService` |
| Gradle (`build.gradle.kts`) | `.csproj` + NuGet |

## Estructura

```
app/src/main/java/com/brandon/videodownloader/
├── App.kt                      # arranque e inyección de dependencias manual
├── data/                       # Room: entidad Download, DAO, base de datos
├── engine/                     # yt-dlp: init, actualizar, analizar URL, calidades
├── download/                   # worker, controller, guardado en galería, notificaciones
└── ui/                         # Activity, ViewModel, pantallas Descargar / Cola / Biblioteca
```

## Compilar localmente (opcional)

Con Android Studio o el SDK instalado: `./gradlew assembleRelease`.

## Aviso

Para uso personal. Respeta los derechos de autor y los términos de cada sitio.
