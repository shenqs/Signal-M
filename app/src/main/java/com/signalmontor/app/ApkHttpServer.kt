package com.signalmontor.app

import android.content.Context
import android.net.wifi.WifiManager
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class ApkHttpServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newCachedThreadPool()
    private var port = 8080

    interface ServerCallback {
        fun onServerStarted(url: String)
        fun onServerStopped()
        fun onDownloadStart(clientIp: String)
        fun onDownloadProgress(clientIp: String, percent: Int)
        fun onDownloadComplete(clientIp: String)
        fun onError(message: String)
    }

    var callback: ServerCallback? = null

    fun start(port: Int = 8080): Boolean {
        if (isRunning) return true
        this.port = port

        return try {
            serverSocket = ServerSocket(port)
            isRunning = true

            val ip = getLocalIpAddress()
            callback?.onServerStarted("http://$ip:$port")

            thread(start = true, name = "HttpServer") {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            callback?.onError("接受连接失败: ${e.message}")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            callback?.onError("启动服务器失败: ${e.message}")
            false
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        executor.shutdownNow()
        callback?.onServerStopped()
    }

    private fun handleClient(client: java.net.Socket) {
        val clientIp = client.inetAddress.hostAddress ?: "unknown"
        var outputStream: OutputStream? = null

        try {
            val input = BufferedReader(InputStreamReader(client.getInputStream()))
            val output = client.getOutputStream()
            outputStream = output

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            var path = URLDecoder.decode(parts[1], "UTF-8")

            var rangeStart = -1L
            var rangeEnd = -1L
            var line: String?
            while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.startsWith("Range:", ignoreCase = true)) {
                    val rangeHeader = line!!.substring(6).trim()
                    if (rangeHeader.startsWith("bytes=")) {
                        val rangeSpec = rangeHeader.substring(6)
                        val rangeParts = rangeSpec.split("-")
                        if (rangeParts.isNotEmpty() && rangeParts[0].isNotEmpty()) {
                            rangeStart = rangeParts[0].toLongOrNull() ?: -1L
                        }
                        if (rangeParts.size > 1 && rangeParts[1].isNotEmpty()) {
                            rangeEnd = rangeParts[1].toLongOrNull() ?: -1L
                        }
                    }
                }
            }

            if (method == "GET") {
                when {
                    path == "/" || path == "/index.html" -> {
                        sendHtmlPage(output)
                    }
                    path == "/apk" || path == "/download" || path.startsWith("/apk?") -> {
                        sendApkFile(output, clientIp, rangeStart, rangeEnd)
                    }
                    path.startsWith("/files/") -> {
                        val fileName = path.substring(7)
                        sendSpecificFile(output, fileName, clientIp, rangeStart, rangeEnd)
                    }
                    path == "/status" -> {
                        sendStatus(output)
                    }
                    else -> {
                        send404(output)
                    }
                }
            } else if (method == "HEAD") {
                when {
                    path == "/apk" || path == "/download" -> {
                        sendApkHead(output)
                    }
                    else -> {
                        send404(output)
                    }
                }
            } else {
                send405(output)
            }
        } catch (e: Exception) {
            callback?.onError("处理请求失败: ${e.message}")
        } finally {
            try {
                outputStream?.close()
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendHtmlPage(output: OutputStream) {
        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SignalMonitor APK Download</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .container {
            background: white;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            max-width: 500px;
            width: 100%;
            padding: 40px;
            text-align: center;
        }
        .logo {
            width: 80px;
            height: 80px;
            background: linear-gradient(135deg, #4CAF50, #2196F3);
            border-radius: 20px;
            margin: 0 auto 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 40px;
        }
        h1 {
            color: #1a1a2e;
            font-size: 28px;
            margin-bottom: 8px;
        }
        .version {
            color: #666;
            font-size: 14px;
            margin-bottom: 32px;
        }
        .download-btn {
            display: inline-block;
            background: linear-gradient(135deg, #4CAF50, #45a049);
            color: white;
            padding: 16px 48px;
            border-radius: 50px;
            text-decoration: none;
            font-size: 18px;
            font-weight: 600;
            transition: transform 0.2s, box-shadow 0.2s;
            margin-bottom: 24px;
        }
        .download-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 25px rgba(76, 175, 80, 0.4);
        }
        .info {
            background: #f5f5f5;
            border-radius: 12px;
            padding: 20px;
            margin-top: 24px;
            text-align: left;
        }
        .info-item {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid #e0e0e0;
        }
        .info-item:last-child { border-bottom: none; }
        .info-label { color: #666; }
        .info-value { color: #333; font-weight: 500; }
        .warning {
            background: #fff3cd;
            border: 1px solid #ffc107;
            border-radius: 8px;
            padding: 12px;
            margin-top: 16px;
            font-size: 13px;
            color: #856404;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">📡</div>
        <h1>SignalMonitor</h1>
        <div class="version">信号监测 · APK下载</div>
        
        <a href="/apk" class="download-btn">📥 下载APK</a>
        
        <div class="info">
            <div class="info-item">
                <span class="info-label">应用名称</span>
                <span class="info-value">SignalMonitor</span>
            </div>
            <div class="info-item">
                <span class="info-label">最低系统</span>
                <span class="info-value">Android 8.0+</span>
            </div>
            <div class="info-item">
                <span class="info-label">权限要求</span>
                <span class="info-value">位置/电话状态</span>
            </div>
        </div>
        
        <div class="warning">
            ⚠️ 请在下载后允许安装未知来源应用
        </div>
    </div>
</body>
</html>
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${html.toByteArray(Charsets.UTF_8).size}\r\n" +
                "Connection: close\r\n" +
                "\r\n" + html
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun sendApkFile(output: OutputStream, clientIp: String, rangeStart: Long, rangeEnd: Long) {
        try {
            val apkFile = File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir)
            val apkLength = apkFile.length()
            val apkName = "SignalMonitor-${context.packageManager.getPackageInfo(context.packageName, 0).versionName}.apk"

            val actualStart = if (rangeStart >= 0 && rangeStart < apkLength) rangeStart else 0L
            val actualEnd = if (rangeEnd > actualStart && rangeEnd < apkLength) rangeEnd else apkLength - 1
            val contentLength = actualEnd - actualStart + 1

            callback?.onDownloadStart(clientIp)

            val header = if (rangeStart >= 0) {
                "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Range: bytes $actualStart-$actualEnd/$apkLength\r\n"
            } else {
                "HTTP/1.1 200 OK\r\n"
            }

            val fullHeader = header +
                    "Content-Type: application/vnd.android.package-archive\r\n" +
                    "Content-Disposition: attachment; filename=\"$apkName\"\r\n" +
                    "Content-Length: $contentLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            output.write(fullHeader.toByteArray())
            output.flush()

            FileInputStream(apkFile).use { fis ->
                if (actualStart > 0) {
                    fis.skip(actualStart)
                }
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int
                var lastPercent = if (rangeStart >= 0) ((actualStart * 100) / apkLength).toInt() else 0

                while (totalRead < contentLength) {
                    val toRead = minOf(buffer.size.toLong(), contentLength - totalRead).toInt()
                    bytesRead = fis.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                    totalRead += bytesRead

                    val percent = (((actualStart + totalRead) * 100) / apkLength).toInt()
                    if (percent != lastPercent && percent % 10 == 0) {
                        lastPercent = percent
                        callback?.onDownloadProgress(clientIp, percent)
                    }
                }
            }

            callback?.onDownloadComplete(clientIp)

        } catch (e: Exception) {
            callback?.onError("发送APK失败: ${e.message}")
            send500(output)
        }
    }

    private fun sendApkHead(output: OutputStream) {
        try {
            val apkFile = File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir)
            val apkLength = apkFile.length()
            val apkName = "SignalMonitor-${context.packageManager.getPackageInfo(context.packageName, 0).versionName}.apk"

            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/vnd.android.package-archive\r\n" +
                    "Content-Disposition: attachment; filename=\"$apkName\"\r\n" +
                    "Content-Length: $apkLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            output.write(header.toByteArray())
            output.flush()
        } catch (e: Exception) {
            send500(output)
        }
    }

    private fun sendSpecificFile(output: OutputStream, fileName: String, clientIp: String, rangeStart: Long, rangeEnd: Long) {
        try {
            val apkFile = File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir)
            val apkLength = apkFile.length()

            val actualStart = if (rangeStart >= 0 && rangeStart < apkLength) rangeStart else 0L
            val actualEnd = if (rangeEnd > actualStart && rangeEnd < apkLength) rangeEnd else apkLength - 1
            val contentLength = actualEnd - actualStart + 1

            callback?.onDownloadStart(clientIp)
            callback?.onDownloadProgress(clientIp, 0)

            val header = if (rangeStart >= 0) {
                "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Range: bytes $actualStart-$actualEnd/$apkLength\r\n"
            } else {
                "HTTP/1.1 200 OK\r\n"
            }

            val fullHeader = header +
                    "Content-Type: application/vnd.android.package-archive\r\n" +
                    "Content-Disposition: attachment; filename=\"$fileName\"\r\n" +
                    "Content-Length: $contentLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            output.write(fullHeader.toByteArray())
            output.flush()

            FileInputStream(apkFile).use { fis ->
                if (actualStart > 0) {
                    fis.skip(actualStart)
                }
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int
                while (totalRead < contentLength) {
                    val toRead = minOf(buffer.size.toLong(), contentLength - totalRead).toInt()
                    bytesRead = fis.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                    totalRead += bytesRead
                }
            }

            callback?.onDownloadComplete(clientIp)

        } catch (e: Exception) {
            send404(output)
        }
    }

    private fun sendStatus(output: OutputStream) {
        val status = """{"running": $isRunning, "port": $port}"""
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${status.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n" + status
        output.write(response.toByteArray())
        output.flush()
    }

    private fun send404(output: OutputStream) {
        val html = "<html><body><h1>404 Not Found</h1></body></html>"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${html.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n" + html
        output.write(response.toByteArray())
        output.flush()
    }

    private fun send405(output: OutputStream) {
        val html = "<html><body><h1>405 Method Not Allowed</h1></body></html>"
        val response = "HTTP/1.1 405 Method Not Allowed\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${html.length}\r\n" +
                "Allow: GET\r\n" +
                "Connection: close\r\n" +
                "\r\n" + html
        output.write(response.toByteArray())
        output.flush()
    }

    private fun send500(output: OutputStream) {
        val html = "<html><body><h1>500 Internal Server Error</h1></body></html>"
        val response = "HTTP/1.1 500 Internal Server Error\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${html.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n" + html
        output.write(response.toByteArray())
        output.flush()
    }

    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            if (ipAddress != 0) {
                return String.format("%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun getServerUrl(): String {
        return if (isRunning) {
            "http://${getLocalIpAddress()}:$port"
        } else {
            ""
        }
    }

    fun isRunning(): Boolean = isRunning
}