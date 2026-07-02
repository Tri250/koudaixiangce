package com.rapidraw.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 联机拍摄管理器 — USB/网络联机相机拍摄支持。
 *
 * 功能框架：
 * - 通过 USB PTP/MTP 检测连接的相机
 * - 远程触发拍摄
 * - 自动导入拍摄图像到图库
 * - 实时预览支持（如果相机支持）
 * - 相机设置控制（光圈、快门、ISO）如果支持
 * - 会话管理（创建、保存、加载拍摄会话）
 * - 导入时自动应用预设调整
 *
 * 注意：此框架为接口层 — 实际相机支持取决于设备的 PTP 实现。
 * 主流相机（Canon、Nikon、Sony 等）通过 USB PTP 协议支持联机拍摄。
 */
class TetheredCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "TetheredCaptureManager"
        private const val PTP_CLASS = 6 // USB PTP 设备类
        private const val MTP_SUBCLASS = 1 // MTP 子类
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_RETRIES = 3
    }

    /** 连接状态 */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }

    /** 相机信息 */
    data class CameraInfo(
        val deviceId: String,
        val manufacturer: String,
        val model: String,
        val serialNumber: String?,
        val supportedSettings: List<CameraSetting>,
        val usbDevice: UsbDevice?,
    )

    /** 相机设置类型 */
    enum class CameraSetting(val displayName: String) {
        APERTURE("光圈"),
        SHUTTER_SPEED("快门速度"),
        ISO("ISO"),
        WHITE_BALANCE("白平衡"),
        EXPOSURE_COMPENSATION("曝光补偿"),
        FOCUS_MODE("对焦模式"),
        IMAGE_FORMAT("图像格式"),
        IMAGE_QUALITY("图像质量"),
    }

    /** 相机设置值 */
    data class SettingValue(
        val setting: CameraSetting,
        val currentValue: String,
        val availableValues: List<String>,
    )

    /** 拍摄会话 */
    data class CaptureSession(
        val id: String = UUID.randomUUID().toString(),
        val name: String = "新会话",
        val createdAt: Long = System.currentTimeMillis(),
        val images: List<String> = emptyList(),
        val presetAdjustments: Map<String, Float> = emptyMap(),
    )

    /** 捕获的图像 */
    data class CaptureResult(
        val localPath: String,
        val fileName: String,
        val fileSize: Long,
        val timestamp: Long,
    )

    /** 进度事件 */
    data class ProgressEvent(
        val message: String,
        val progress: Float,
    )

    // ── 状态 ──────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedCameras = MutableStateFlow<List<CameraInfo>>(emptyList())
    val connectedCameras: StateFlow<List<CameraInfo>> = _connectedCameras.asStateFlow()

    private val _activeSession = MutableStateFlow<CaptureSession?>(null)
    val activeSession: StateFlow<CaptureSession?> = _activeSession.asStateFlow()

    private val _capturedImages = MutableStateFlow<List<CaptureResult>>(emptyList())
    val capturedImages: StateFlow<List<CaptureResult>> = _capturedImages.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _lastProgress = MutableStateFlow<ProgressEvent?>(null)
    val lastProgress: StateFlow<ProgressEvent?> = _lastProgress.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var pollJob: Job? = null
    private val captureQueue = mutableListOf<() -> Unit>()
    private val sessionHistory = mutableListOf<CaptureSession>()

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 检测连接的相机（通过 USB PTP/MTP）。
     *
     * @return 检测到的相机列表
     */
    fun detectCameras(): List<CameraInfo> {
        return try {
            val detected = mutableListOf<CameraInfo>()

            val deviceList = usbManager.deviceList
            for ((_, device) in deviceList) {
                // 检查设备是否为 PTP/MTP 相机
                if (isCameraDevice(device)) {
                    val info = CameraInfo(
                        deviceId = device.deviceName,
                        manufacturer = device.manufacturerName ?: "未知",
                        model = device.productName ?: "未知",
                        serialNumber = device.serialNumber,
                        supportedSettings = getCameraSupportedSettings(device),
                        usbDevice = device,
                    )
                    detected.add(info)
                    Log.i(TAG, "Detected camera: ${info.manufacturer} ${info.model}")
                }
            }

            _connectedCameras.value = detected

            if (detected.isNotEmpty()) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            detected
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect cameras: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            emptyList()
        }
    }

    /**
     * 连接到指定相机。
     */
    fun connectToCamera(camera: CameraInfo): Boolean {
        _connectionState.value = ConnectionState.CONNECTING

        return try {
            val device = camera.usbDevice
            if (device == null) {
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            // 请求 USB 权限
            if (!usbManager.hasPermission(device)) {
                Log.w(TAG, "No USB permission for device ${camera.deviceId}")
                // 在实际应用中，这里需要请求权限
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.w(TAG, "Failed to open USB device ${camera.deviceId}")
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            // 初始化 PTP 会话
            val ptpInitialized = initPtpSession(connection, camera)
            if (!ptpInitialized) {
                connection.close()
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            _connectionState.value = ConnectionState.CONNECTED
            Log.i(TAG, "Connected to camera: ${camera.model}")

            // 启动自动导入轮询
            startAutoImportPolling()

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect camera: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            false
        }
    }

    /**
     * 断开连接。
     */
    fun disconnect() {
        pollJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
        _isCapturing.value = false
        Log.i(TAG, "Disconnected from camera")
    }

    /**
     * v1.10.6: 关闭联机拍摄管理器，取消所有协程并释放资源。
     */
    fun shutdown() {
        disconnect()
        scope.cancel()
        Log.i(TAG, "TetheredCaptureManager shutdown")
    }

    /**
     * 触发拍摄。
     */
    suspend fun capture(): CaptureResult? {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Not connected to camera")
            return null
        }

        _isCapturing.value = true
        _lastProgress.value = ProgressEvent("触发拍摄...", 0f)

        return try {
            val result = withContext(Dispatchers.IO) {
                triggerCaptureInternal()
            }

            if (result != null) {
                _capturedImages.update { it + result }
                _lastProgress.value = ProgressEvent("导入完成: ${result.fileName}", 1f)

                // 更新会话
                _activeSession.value?.let { session ->
                    _activeSession.value = session.copy(
                        images = session.images + result.localPath,
                    )
                }
            }

            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed: ${e.message}", e)
            _lastProgress.value = ProgressEvent("拍摄失败: ${e.message}", 0f)
            null
        } finally {
            _isCapturing.value = false
        }
    }

    /**
     * 批量拍摄。
     */
    suspend fun captureBurst(
        count: Int,
        intervalMs: Long = 500,
        onEachCapture: (CaptureResult) -> Unit = {},
    ): List<CaptureResult> {
        val results = mutableListOf<CaptureResult>()
        for (i in 0 until count) {
            val result = capture()
            if (result != null) {
                results.add(result)
                onEachCapture(result)
            }
            if (i < count - 1) {
                delay(intervalMs)
            }
        }
        return results
    }

    /**
     * 获取相机设置。
     */
    fun getCameraSettings(camera: CameraInfo): List<SettingValue> {
        return camera.supportedSettings.map { setting ->
            val currentValue = getCurrentSettingValue(camera, setting)
            val availableValues = getAvailableSettingValues(camera, setting)
            SettingValue(setting, currentValue, availableValues)
        }
    }

    /**
     * 设置相机参数。
     */
    fun setCameraSetting(camera: CameraInfo, setting: CameraSetting, value: String): Boolean {
        return try {
            applyCameraSetting(camera, setting, value)
            Log.i(TAG, "Set camera setting: ${setting.displayName} = $value")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set camera setting: ${e.message}", e)
            false
        }
    }

    /**
     * 获取实时预览（如果相机支持）。
     * 返回预览帧数据或 null。
     */
    fun getLiveViewFrame(): ByteArray? {
        if (_connectionState.value != ConnectionState.CONNECTED) return null
        return try {
            fetchLiveViewFrame()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get live view: ${e.message}")
            null
        }
    }

    // ── 会话管理 ──────────────────────────────────────────────────

    /**
     * 创建新拍摄会话。
     */
    fun createSession(name: String = "新会话"): CaptureSession {
        val session = CaptureSession(name = name)
        _activeSession.value = session
        sessionHistory.add(session)
        return session
    }

    /**
     * 获取所有会话。
     */
    fun getSessions(): List<CaptureSession> = sessionHistory.toList()

    /**
     * 加载会话。
     */
    fun loadSession(sessionId: String): CaptureSession? {
        val session = sessionHistory.find { it.id == sessionId }
        if (session != null) {
            _activeSession.value = session
        }
        return session
    }

    /**
     * 保存当前会话。
     */
    fun saveSession(): Boolean {
        val session = _activeSession.value ?: return false
        val existing = sessionHistory.indexOfFirst { it.id == session.id }
        if (existing >= 0) {
            sessionHistory[existing] = session
        } else {
            sessionHistory.add(session)
        }
        return true
    }

    /**
     * 设置预设调整（导入时自动应用）。
     */
    fun setPresetAdjustments(adjustments: Map<String, Float>) {
        _activeSession.value?.let { session ->
            _activeSession.value = session.copy(presetAdjustments = adjustments)
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private fun isCameraDevice(device: UsbDevice): Boolean {
        // 检查设备接口是否为 PTP 或 MTP
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == PTP_CLASS ||
                (intf.interfaceClass == 6 && intf.interfaceSubclass == MTP_SUBCLASS) ||
                intf.interfaceClass == 0xFF // 厂商特定类
            ) {
                // 检查端点是否支持批量传输
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun getCameraSupportedSettings(device: UsbDevice): List<CameraSetting> {
        // 默认所有相机都支持这些基本设置
        // 实际可用性取决于具体设备
        return listOf(
            CameraSetting.APERTURE,
            CameraSetting.SHUTTER_SPEED,
            CameraSetting.ISO,
            CameraSetting.WHITE_BALANCE,
            CameraSetting.EXPOSURE_COMPENSATION,
            CameraSetting.FOCUS_MODE,
            CameraSetting.IMAGE_FORMAT,
            CameraSetting.IMAGE_QUALITY,
        )
    }

    private fun getCurrentSettingValue(camera: CameraInfo, setting: CameraSetting): String {
        return try {
            // PTP 命令: GetDevicePropValue
            val propCode = settingToPropCode(setting)
            sendPtpCommand(camera, 0x1015, listOf(propCode)) // GetDevicePropValue
            "PTP_VALUE"
        } catch (e: Exception) {
            "未知"
        }
    }

    private fun getAvailableSettingValues(camera: CameraInfo, setting: CameraSetting): List<String> {
        return try {
            val propCode = settingToPropCode(setting)
            sendPtpCommand(camera, 0x1014, listOf(propCode)) // GetDevicePropDesc
            listOf("自动", "手动", "默认")
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun applyCameraSetting(camera: CameraInfo, setting: CameraSetting, value: String): Boolean {
        val propCode = settingToPropCode(setting)
        sendPtpCommand(camera, 0x1016, listOf(propCode)) // SetDevicePropValue
        return true
    }

    private fun settingToPropCode(setting: CameraSetting): Int {
        return when (setting) {
            CameraSetting.APERTURE -> 0xD401
            CameraSetting.SHUTTER_SPEED -> 0xD402
            CameraSetting.ISO -> 0xD403
            CameraSetting.WHITE_BALANCE -> 0xD405
            CameraSetting.EXPOSURE_COMPENSATION -> 0xD40A
            CameraSetting.FOCUS_MODE -> 0xD40C
            CameraSetting.IMAGE_FORMAT -> 0xD40F
            CameraSetting.IMAGE_QUALITY -> 0xD410
        }
    }

    private fun initPtpSession(connection: android.hardware.usb.UsbDeviceConnection, camera: CameraInfo): Boolean {
        return try {
            // 发送 PTP OpenSession 命令
            sendPtpRawCommand(connection, 0x1002, listOf(1)) // OpenSession
            Log.i(TAG, "PTP session initialized for ${camera.model}")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PTP session init failed: ${e.message}", e)
            false
        }
    }

    /**
     * 发送 PTP 命令（简化版框架）。
     *
     * 完整的 PTP 实现需要：
     * 1. 构建 PTP 命令包（Operation Code, Session ID, Transaction ID, Parameters）
     * 2. 通过 USB 批量端点发送
     * 3. 读取响应包
     * 4. 解析响应数据
     */
    private fun sendPtpCommand(camera: CameraInfo, operationCode: Int, params: List<Int>): String {
        val device = camera.usbDevice ?: throw Exception("No USB device")
        val connection = usbManager.openDevice(device) ?: throw Exception("Cannot open device")

        try {
            sendPtpRawCommand(connection, operationCode, params)
            return "OK"
        } finally {
            connection.close()
        }
    }

    private fun sendPtpRawCommand(
        connection: android.hardware.usb.UsbDeviceConnection,
        operationCode: Int,
        params: List<Int>,
    ) {
        // 构建 PTP 命令包 (12 bytes header + data)
        val packet = ByteArray(12 + params.size * 4)
        val buffer = java.nio.ByteBuffer.wrap(packet)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // PTP 容器头
        buffer.putInt(12 + params.size * 4) // 包长度
        buffer.putShort(1.toShort()) // 类型: 命令
        buffer.putShort(operationCode.toShort()) // 操作码
        buffer.putInt(1) // Transaction ID

        // 参数
        for (p in params) {
            buffer.putInt(p)
        }

        // 发送到批量输出端点
        val outEndpoint = findBulkOutEndpoint(connection)
        if (outEndpoint != null) {
            connection.bulkTransfer(outEndpoint, packet, packet.size, 1000)
        }
    }

    private fun findBulkOutEndpoint(
        connection: android.hardware.usb.UsbDeviceConnection,
    ): android.hardware.usb.UsbEndpoint? {
        // 遍历设备接口找到批量输出端点
        // 这是框架代码，实际实现需要正确的接口描述符
        return null
    }

    private fun triggerCaptureInternal(): CaptureResult? {
        // 1. 发送 PTP InitiateCapture 命令
        // 2. 等待 Camera 完成拍摄
        // 3. 获取新图像对象句柄
        // 4. 下载图像到本地

        // 框架实现：模拟捕获流程
        val fileName = "capture_${System.currentTimeMillis()}.jpg"
        val outputDir = context.getExternalFilesDir("captures") ?: return null
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, fileName)

        return try {
            // 在完整实现中，这里会通过 PTP 协议下载图像
            // 当前返回框架结果
            CaptureResult(
                localPath = outputFile.absolutePath,
                fileName = fileName,
                fileSize = 0,
                timestamp = System.currentTimeMillis(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Trigger capture failed: ${e.message}", e)
            null
        }
    }

    private fun fetchLiveViewFrame(): ByteArray? {
        // 在完整实现中，发送 PTP GetLiveViewImage 命令
        // 并返回 JPEG 预览帧数据
        return null
    }

    private fun startAutoImportPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    // 检查相机是否有新图像
                    checkAndImportNewImages()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 轮询中忽略错误
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkAndImportNewImages() {
        // 在完整实现中：
        // 1. 发送 GetNumObjects 获取相机上的对象数量
        // 2. 比较上次记录的数量
        // 3. 如果有新对象，下载并导入
        withContext(Dispatchers.IO) {
            // 框架占位：实际实现通过 PTP 协议通信
        }
    }
}

/**
 * USB 常量（如果 android.hardware.usb.UsbConstants 不可用）。
 */
private object UsbConstants {
    const val USB_ENDPOINT_XFER_BULK = 2
    const val USB_ENDPOINT_XFER_CONTROL = 0
    const val USB_ENDPOINT_XFER_INT = 3
}