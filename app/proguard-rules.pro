# Reglas de R8 para la versión de Google Play.
# La app no usa reflexión propia; las librerías (Compose, AdMob, UMP) traen sus reglas.

# Conserva nombres de archivo y líneas para que los informes de errores de
# Play Console sean legibles (sube el mapping.txt al publicar).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Quita los logs de diagnóstico (no sensibles) en la build de release; conserva
# warning/error para que los informes de Play Console sigan siendo útiles.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
