# JNI: Methoden werden aus C per Name aufgerufen
-keep class de.elias.launcher.airplay.NativeBridge { *; }
-keep interface de.elias.launcher.airplay.RaopCallbackHandler { *; }
-keep class * implements de.elias.launcher.airplay.RaopCallbackHandler { *; }
