package com.twinpane.app

import java.io.OutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

object LocalWebServer {

    private var serverSocket: ServerSocket? = null
    var isRunning = false
        private set

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress
                        if ((host != null) && (!host.contains(':')) && (host != "127.0.0.1")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun start(port: Int = 8080, htmlContentProvider: () -> String): Boolean {
        stop()
        return try {
            val ss = ServerSocket(port)
            serverSocket = ss
            isRunning = true

            thread {
                while (isRunning && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        handleClient(client, htmlContentProvider)
                    } catch (_: Exception) {}
                }
            }
            true
        } catch (_: Exception) {
            isRunning = false
            false
        }
    }

    private fun handleClient(client: Socket, htmlContentProvider: () -> String) {
        thread {
            try {
                val html = htmlContentProvider()
                val bytes = html.toByteArray(Charsets.UTF_8)
                val os: OutputStream = client.getOutputStream()
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                os.write(header.toByteArray(Charsets.UTF_8))
                os.write(bytes)
                os.flush()
                client.close()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
