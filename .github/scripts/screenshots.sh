#!/usr/bin/env bash
# Recorre la app en un emulador y guarda capturas en ./screenshots.
# Nunca falla el job: si un paso no encuentra algo, sigue con el siguiente.
set -u
PKG=com.brandon.videodownloader
APK=$(ls app/build/outputs/apk/release/*x86_64*.apk | head -1)
OUT=screenshots
mkdir -p "$OUT"
# Videos de prueba de dominio público / CC-BY alojados en archive.org (no requieren cuenta).
URL1="https://archive.org/details/BigBuckBunny_124"
URL2="https://archive.org/details/ElephantsDream"

dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1; }
find_xy() { dump; python3 .github/scripts/ui.py /tmp/ui.xml "$@"; }
# Espera hasta N segundos a que aparezca un texto. Devuelve 0 si apareció.
wait_for() {
  local text=$1 secs=${2:-30}; shift 2 || true
  for _ in $(seq 1 $((secs / 2))); do
    [ -n "$(find_xy "$text" "$@")" ] && return 0
    sleep 2
  done
  echo "  (no apareció: $text)"; return 1
}
wait_gone() {
  local text=$1 secs=${2:-60}
  for _ in $(seq 1 $((secs / 3))); do
    [ -z "$(find_xy "$text" --contains)" ] && return 0
    sleep 3
  done
  return 1
}
tap() {
  local xy; xy=$(find_xy "$@")
  if [ -n "$xy" ]; then adb shell input tap $xy; sleep 1.5; return 0; fi
  echo "  (no se encontró para tocar: $1)"; return 1
}
shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "📸 $1"; }
share() { adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'$1'" -n "$PKG/.ui.MainActivity" >/dev/null; }
scroll_down() { adb shell input swipe 540 1700 540 700 400; sleep 1; }

echo "== Instalando $APK"
adb install -r -g "$APK"
adb shell cmd uimode night yes
adb shell settings put system screen_off_timeout 1800000

echo "== Inicio"
adb shell am start -n "$PKG/.ui.MainActivity" >/dev/null
wait_for "Descarga sin anuncios" 60
wait_gone "Preparando el motor" 120
shot 01-inicio

echo "== Compartir enlace desde otra app (hoja rápida)"
share "$URL1"
wait_for "Descarga rápida" 30
sleep 3; wait_gone "Analizando enlace" 120; sleep 3
shot 02-hoja-rapida 2

echo "== Pantalla principal con análisis"
tap "Cerrar"
shot 03-analisis
scroll_down
shot 04-calidades

echo "== Descargar (video 720p) + segundo enlace como audio"
tap "480p"
tap "Descargar video"
share "$URL2"
wait_for "Descarga rápida" 30
sleep 3; wait_gone "Analizando enlace" 120; sleep 2
tap "Solo audio"
tap "Descargar audio"
sleep 4
shot 05-cola-descargando 1

echo "== Esperando a que terminen las descargas"
wait_for "Nada en la cola" 400
tap "Biblioteca"
shot 06-biblioteca 4
tap "Cambiar vista"
shot 07-biblioteca-lista 3
tap "Cambiar vista"

echo "== Reproductor"
tap "Big Buck Bunny" --contains || adb shell input tap 300 900
shot 08-reproductor 4
adb shell input keyevent KEYCODE_BACK
sleep 2

echo "== Ajustes y cuentas"
tap "Ajustes"
shot 09-ajustes 2
scroll_down
shot 10-ajustes-2
tap "Cuentas y sesiones"
shot 11-cuentas 2
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "== Tema claro"
adb shell cmd uimode night no
sleep 4
tap "Biblioteca"
shot 12-biblioteca-claro 3
tap "Descargar"
shot 13-inicio-claro 2

echo "== Listo"
ls -la "$OUT"
exit 0
