# Video Downloader (Android)

App Android de uso personal para descargar videos de casi cualquier sitio sin anuncios:
YouTube, TikTok, Instagram, Facebook, X/Twitter, Vimeo, Twitch, Reddit y [+1800 sitios](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

**Funciones**
- Pegas uno o varios enlaces (o usas **Compartir → Video Downloader** desde cualquier app).
- Eliges la calidad: Máxima, 4K, 2K, 1080p, 720p, 480p, 360p o **solo audio (MP3)**.
- Antes de bajar, "Analizar" te muestra miniatura, duración y las resoluciones disponibles.
- **Descargas múltiples**: hasta 3 a la vez, el resto espera en cola. Puedes cancelar y reintentar.
- Las descargas siguen con la app cerrada, con su notificación de progreso.
- Las **listas de reproducción** se separan en un video por descarga.
- **Biblioteca**: buscar, reproducir, compartir y borrar. Los archivos van a la galería:
  `Movies/VideoDownloader` (video) y `Music/VideoDownloader` (audio).
- yt-dlp se actualiza solo una vez al día, y también desde el menú ⋮.

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
