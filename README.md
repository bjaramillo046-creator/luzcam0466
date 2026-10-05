# LuzCam — prototipo 0.1

Dos aplicaciones para enviar la cámara Android por la red local y recibirla en Windows con salida a OBS Virtual Camera.

**Este paquete contiene código fuente, no un APK ni un EXE ya compilados.** Se verificaron ocho pruebas del protocolo, incluida comunicación real entre Java y Python en Linux. La app Android completa, la interfaz Windows, la compilación de sus paquetes y Discord todavía requieren pruebas en dispositivos reales. No se promete compatibilidad con todos los programas.

## Qué incluye

- Android: cámara frontal o trasera, vista previa, botón Conectar/Detener y reconexión automática cada dos segundos.
- Windows: interfaz, direcciones IPv4 candidatas, código de emparejamiento, vista previa, contador de imágenes y diagnóstico separado de red/cámara virtual.
- Captura objetivo 640 × 480, JPEG calidad 65, hasta unos 15 FPS. Si el teléfono no ofrece esa resolución, elige una cercana y Windows ajusta a 640 × 480.
- Video cifrado con AES-GCM y autenticación mediante un secreto aleatorio de 128 bits. El código cambia al abrir el receptor. Sin cuentas, nube, grabación, telemetría ni micrófono.
- La imagen sale por **OBS Virtual Camera**, no por un dispositivo llamado LuzCam. Es una dependencia explícita: este proyecto no implementa ni instala un controlador de cámara propio.

## Lo necesario

| Equipo | Requisito previsto |
| --- | --- |
| PC | Windows de 64 bits; objetivo Windows 10/11, pendiente de validación |
| Teléfono | Android 8 o superior, con permiso de cámara; pendiente de validación |
| Red | Ambos en la misma LAN; el PC también puede estar por cable al mismo router |
| Cámara virtual | OBS Studio instalado en el PC, con su componente de cámara virtual |

Instala OBS desde https://obsproject.com/. La versión de OBS que uses también debe ser compatible con tu versión de Windows. No necesitas configurar escenas ni iniciar su cámara virtual: LuzCam envía directamente a ese componente mediante pyvirtualcam. Deja OBS cerrado durante la primera prueba para evitar que ambos intenten producir video a la vez.

## Obtener el APK y el EXE

### Compilación manual en GitHub Actions (configuración incluida, no ejecutada aquí)

1. Crea un repositorio, privado si prefieres, y sube **el contenido** de esta carpeta. `android`, `windows` y `.github` deben quedar en la raíz. No subas tus códigos de conexión ni claves.
2. En Actions abre **Compilar LuzCam** y pulsa **Run workflow**. Esto usa servicios de GitHub y descarga dependencias de compilación. No publica ninguna aplicación.
3. Si los dos trabajos terminan correctamente, descarga los artifacts `LuzCam-Windows-prototipo` y `LuzCam-Android-prototipo`.
4. Extrae el paquete Windows conservando toda la carpeta y sus archivos de soporte. Busca `LuzCam.exe` dentro de ella.
5. Copia `app-debug.apk` al teléfono e instálalo autorizando esa instalación desde la fuente elegida. Es un APK de desarrollo, no una distribución firmada para una tienda.

El flujo no ha sido ejecutado en esta entrega; un fallo de compilación necesita revisar su registro. Las claves de depuración pueden cambiar entre ejecuciones de CI: para actualizar un APK con firma distinta, desinstala antes la versión anterior. Para una distribución estable hace falta configurar una clave de firma propia y conservarla.

### Compilar en tu PC

**Windows:** instala Python **3.11 de 64 bits** desde https://www.python.org/downloads/windows/ incluyendo el lanzador `py`. En la carpeta `windows`, abre `Preparar.bat`; necesita internet para instalar dependencias. Después usa `Iniciar.bat`. Si quieres crear el ejecutable, abre `Crear-EXE.bat`: el resultado está en `windows/dist/LuzCam/LuzCam.exe`. Distribuye la carpeta completa, no solo el EXE. El componente de OBS sigue siendo necesario en el equipo de destino.

**Android:** necesitas JDK 17, Android SDK plataforma 35 y Gradle 8.9. El proyecto usa Android Gradle Plugin 8.7.3. Con `ANDROID_HOME` apuntando al SDK y `gradle` en PATH, ejecuta desde `android`:

```text
gradle --no-daemon assembleDebug lintDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`. No se incluye Gradle Wrapper; usa Gradle 8.9 instalado o el flujo de compilación anterior, que prepara esa versión automáticamente. También puedes importar el proyecto en Android Studio configurando esa distribución local de Gradle.

## Conectar

1. Pon ambos equipos en la misma red local. Evita la red de invitados.
2. Abre LuzCam en Windows. Si el firewall pregunta, permite acceso en **redes privadas** de confianza.
3. En Android escribe una IPv4 del PC que aparezca en la ventana, por ejemplo `192.168.1.20`, y el código completo. No uses la dirección del teléfono ni la del router. Puede haber varias direcciones si tienes VPN/adaptadores virtuales; `ipconfig` identifica la del adaptador conectado a tu router.
4. Elige cámara frontal/trasera y pulsa Conectar. Permite el acceso a la cámara. Mantén la app visible: al minimizarla, bloquear o salir se detiene la transmisión y se libera la cámara.
5. Comprueba que Windows muestra imagen, aumenta el contador y dice **Cámara virtual activa**.
6. Abre la configuración de cámara de Discord y selecciona **OBS Virtual Camera**. Usa primero su prueba de video y luego una llamada.
7. Para terminar pulsa Detener en Android o Detener y cerrar en Windows. Tras perder señal, el receptor sustituye la imagen por una pantalla sin video; no mantiene indefinidamente el último fotograma.

La app Android usa orientación horizontal. Para cambiar entre frontal/trasera detén la transmisión, cambia la selección y reconecta. La previsualización frontal del teléfono puede verse espejada; el video transmitido no añade espejo.

## Si falla

| Síntoma | Qué revisar |
| --- | --- |
| Android no conecta y Windows no recibe imágenes | IP del adaptador correcto, código actual, red de invitados/aislamiento entre clientes, VPN y firewall |
| Conecta, pero luego falla | Señal Wi-Fi, app Android visible, permiso de cámara, ausencia de otra app usando la cámara |
| Windows ve imagen, pero no activa cámara virtual | Instalación del componente OBS Virtual Camera; cerrar OBS y otros productores de esa cámara |
| Cámara virtual activa, pero no aparece en Discord | Reiniciar Discord después de instalar OBS; seleccionar OBS Virtual Camera y revisar permisos de cámara para aplicaciones de escritorio en Windows |
| Pantalla negra solo en Discord | Probar primero la vista previa de LuzCam y otra aplicación; verificar que no esté seleccionada otra cámara |
| Imagen lenta | Acercarse al router; probar 5 GHz si tiene buena señal; detener descargas. El prototipo codifica JPEG por CPU y no promete baja latencia |
| Puerto ocupado | Cerrar otra instancia de LuzCam; usa TCP 47990 |

No desactives el firewall entero. Si necesitas una regla explícita, puedes abrir PowerShell como administrador y autorizar únicamente el programa receptor en la red privada y la subred local (sustituye la ruta por la real):

```powershell
New-NetFirewallRule -DisplayName "LuzCam local" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 47990 -RemoteAddress LocalSubnet -Profile Private -Program "C:\ruta\LuzCam\LuzCam.exe"
```

Si ejecutas el código sin EXE, la ruta es la de `windows\.venv\Scripts\python.exe`. Para retirar esa regla:

```powershell
Remove-NetFirewallRule -DisplayName "LuzCam local"
```

No necesitas abrir puertos del router ni usar internet durante la transmisión. El receptor escucha en todas las interfaces IPv4: úsalo en una LAN de confianza con firewall privado. El cifrado es una implementación de prototipo, no una auditoría de seguridad ni un servicio pensado para exponer a internet.

## Verificación realizada y pendiente

Se ejecutaron ocho pruebas: imagen normal; rechazo de datos alterados; rechazo de repetición de fotograma cifrado; límite de tamaño; rotación inválida; paquete truncado; emparejamiento incorrecto; y cliente Java real enviando dos JPEG cifrados a Python con rotaciones diferentes. También se comprobó sintaxis Python.

Para repetirlas necesitas Java 17 con el módulo de compilación, Python y las dependencias Pillow/cryptography:

```text
python tests/test_protocol.py
```

Pendiente: compilar APK y EXE, comprobar lint Android, permisos y ciclo de vida en teléfono, orientación de ambas cámaras, salida OBS en Windows, detección en Discord y prueba de estabilidad de 30 minutos. El flujo CI compila, pero no sustituye estas pruebas físicas.

## Alcance de esta versión

No incluye sonido, servicio de captura en segundo plano, QR, descubrimiento automático, USB, 720p/1080p, instalador propio ni controlador de cámara independiente de OBS. Es una primera implementación para continuar y validar, no un producto terminado listo para instalar.

## Licencia y referencias

El código original de este proyecto se ofrece bajo GPL-2.0-or-later; consulta LICENSE. Las dependencias conservan sus propias licencias. Al distribuir binarios hay que conservar sus avisos y cumplir las obligaciones correspondientes, incluida la disponibilidad del código fuente aplicable. OBS no se redistribuye en este paquete.

- pyvirtualcam y dependencia de cámara virtual: https://github.com/letmaik/pyvirtualcam
- Cámara virtual OBS: https://obsproject.com/kb/virtual-camera-troubleshooting
- Android Camera: https://developer.android.com/reference/android/hardware/Camera
- Compatibilidad AGP 8.7 / Gradle: https://developer.android.com/build/releases/agp-8-7-0-release-notes
