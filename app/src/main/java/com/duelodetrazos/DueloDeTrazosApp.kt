package com.duelodetrazos

import android.app.Application
import com.parse.Parse
import com.parse.ParseInstallation

// DueloDeTrazosApp: Clase principal de la aplicacion.
// Se ejecuta al iniciar la app y es responsable de la inicializacion de servicios globales.
class DueloDeTrazosApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializacion del SDK de Parse para la comunicacion con Back4App.
        Parse.initialize(
            Parse.Configuration.Builder(this)
                .applicationId("A4lODRoPJmp1awuWdNXrVDMmMmtGGEjSwtsqwVjy")
                .clientKey("bL3c4PquKN6GfHelqKfI3jIb3lxHeRAuAnSvE1kJ")
                .server("https://parseapi.back4app.com/")
                .build()
        )

        // Guarda la instalacion actual para permitir el envio de notificaciones push en el futuro.
        ParseInstallation.getCurrentInstallation().saveInBackground()
    }
}
