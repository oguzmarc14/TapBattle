package com.duelodetrazos.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import kotlin.concurrent.thread

class ServerManager {

    private var serverSocket: ServerSocket? = null

    fun startServer(onClientConnected: () -> Unit) {
        thread {
            try {
                serverSocket = ServerSocket(6000)
                val client = serverSocket!!.accept()

                PrintWriter(client.getOutputStream(), true).println("connected")

                onClientConnected()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
