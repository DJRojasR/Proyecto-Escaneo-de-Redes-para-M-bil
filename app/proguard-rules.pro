# Reglas de R8 para la versión de Google Play.
# La app no usa reflexión propia; las librerías (Compose, AdMob, UMP) traen sus reglas.

# Conserva nombres de archivo y líneas para que los informes de errores de
# Play Console sean legibles (sube el mapping.txt al publicar).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
