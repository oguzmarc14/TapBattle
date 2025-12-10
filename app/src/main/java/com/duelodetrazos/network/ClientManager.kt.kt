package com.duelodetrazos.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class ClientManager(private val host: String) {

    fun connect(onConnected: () -> Unit) {
        thread {
            try {
                val socket = Socket(host, 6000)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val msg = input.readLine()

                if (msg == "connected") {
                    onConnected()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
