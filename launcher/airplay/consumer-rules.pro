# JNI: Methoden werden aus C per Name aufgerufen
-keep class de.jellydroid.launcher.airplay.NativeBridge { *; }
-keep interface de.jellydroid.launcher.airplay.RaopCallbackHandler { *; }
-keep class * implements de.jellydroid.launcher.airplay.RaopCallbackHandler { *; }
