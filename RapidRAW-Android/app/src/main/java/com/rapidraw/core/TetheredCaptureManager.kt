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

    // ── PTP 协议层 ─────────────────────────────────────────────────

    private object PtpContainerType {
        const val COMMAND: Short = 0x0001
        const val DATA: Short = 0x0002
        const val RESPONSE: Short = 0x0003
        const val EVENT: Short = 0x0004
    }

    private object PtpResponseCode {
        const val OK = 0x2001
        const val GENERAL_ERROR = 0x2002
        const val SESSION_NOT_OPEN = 0x2003
        const val INVALID_TRANSACTION_ID = 0x2004
        const val OPERATION_NOT_SUPPORTED = 0x2005
        const val PARAMETER_NOT_SUPPORTED = 0x2006
        const val INCOMPLETE_TRANSFER = 0x2007
        const val INVALID_STORAGE_ID = 0x2008
        const val INVALID_OBJECT_HANDLE = 0x2009
        const val DEVICE_BUSY = 0x200A
        const val INVALID_OBJECT_FORMAT_CODE = 0x200B
        const val STORE_FULL = 0x200C
        const val OBJECT_WRITE_PROTECTED = 0x200D
        const val STORE_READ_ONLY = 0x200E
        const val ACCESS_DENIED = 0x200F
        const val NO_THUMBNAIL_PRESENT = 0x2010
        const val SELF_TEST_FAILED = 0x2011
        const val PARTIAL_DELETION = 0x2012
        const val STORE_NOT_AVAILABLE = 0x2014
    }

    private object PtpOpCode {
        const val GET_DEVICE_INFO = 0x1001
        const val OPEN_SESSION = 0x1002
        const val CLOSE_SESSION = 0x1003
        const val GET_STORAGE_IDS = 0x1004
        const val GET_NUM_OBJECTS = 0x1006
        const val GET_OBJECT_HANDLES = 0x1007
        const val GET_OBJECT_INFO = 0x1008
        const val GET_OBJECT = 0x1009
        const val DELETE_OBJECT = 0x100B
        const val INITIATE_CAPTURE = 0x100E
        const val GET_DEVICE_PROP_VALUE = 0x1015
        const val GET_DEVICE_PROP_DESC = 0x1014
        const val SET_DEVICE_PROP_VALUE = 0x1016
    }

    private data class PtpEndpoints(
        val `interface`: android.hardware.usb.UsbInterface,
        val inEndpoint: android.hardware.usb.UsbEndpoint,   // 批量输入（设备→主机，方向 0x80）
        val outEndpoint: android.hardware.usb.UsbEndpoint,  // 批量输出（主机→设备，方向 0x00）
        val interruptEndpoint: android.hardware.usb.UsbEndpoint?,  // 中断输入（事件）
    )

    private data class PtpContainer(
        val type: Short,
        val code: Int,
        val transactionId: Int,
        val params: IntArray = IntArray(0),
        val data: ByteArray = ByteArray(0),
    )

    private data class ObjectInfo(
        val storageId: Int,
        val formatCode: Int,
        val protectionStatus: Int,
        val objectCompressedSize: Long,
        val thumbFormat: Int,
        val thumbCompressedSize: Int,
        val thumbWidth: Int,
        val thumbHeight: Int,
        val imagePixWidth: Int,
        val imagePixHeight: Int,
        val imageBitDepth: Int,
        val parentObject: Int,
        val associationType: Int,
        val associationDesc: Int,
        val sequenceNumber: Int,
        val fileName: String,
        val captureDate: String,
        val modificationDate: String,
        val keywords: String,
    )

    private var ptpEndpoints: PtpEndpoints? = null
    private var ptpConnection: android.hardware.usb.UsbDeviceConnection? = null
    private var ptpSessionOpen: Boolean = false
    private var lastObjectCount: Int = 0
    private val knownObjectHandles: MutableSet<Int> = mutableSetOf()
    private val transactionCounter = java.util.concurrent.atomic.AtomicInteger(1)

    private class PtpException(val responseCode: Int, message: String) : Exception(message)

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 检测连接的相机（通过 USB PTP/MTP）。
     *
     * @return 检测到的相机列表
     */
    fun detectCameras(): List<CameraInfo> {
        return try {
            val detected = mutableListOf<CameraInfo>()

            val deviceList = try {
                usbManager.deviceList
            } catch (e: SecurityException) {
                // v1.10.6: USB 权限被拒绝（用户未授权或 OEM 限制），等同于 CameraAccessException
                Log.w(TAG, "USB permission denied while detecting cameras: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
                return emptyList()
            }

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
        } catch (e: SecurityException) {
            // v1.10.6: 相机访问被系统安全策略拒绝（等同于 CameraAccessException）
            Log.e(TAG, "Camera access denied by security policy: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            emptyList()
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
        } catch (e: SecurityException) {
            // v1.10.6: 相机访问被系统安全策略拒绝（等同于 CameraAccessException）
            Log.e(TAG, "Camera access denied: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            false
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
        try {
            if (ptpSessionOpen && ptpConnection != null) {
                runCatching { sendPtpCommandNoData(PtpOpCode.CLOSE_SESSION) }
            }
            ptpEndpoints?.let { ep ->
                ptpConnection?.releaseInterface(ep.`interface`)
            }
            ptpConnection?.close()
        } catch (_: Exception) {}
        ptpSessionOpen = false
        ptpEndpoints = null
        ptpConnection = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _isCapturing.value = false
        Log.i(TAG, "Disconnected from camera")
    }

    /**
     * v1.10.6: 关闭联机拍摄管理器，取消所有协程并释放资源。
     */
    fun shutdown() {
        disconnect()
        scope.coroutineContext[Job]?.cancel()
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
            val propCode = settingToPropCode(setting)
            val (resp, data) = sendPtpCommandReceiveData(PtpOpCode.GET_DEVICE_PROP_VALUE, propCode)
            if (resp.code != PtpResponseCode.OK) return "未知"
            parsePtpPropValue(data, setting)
        } catch (e: PtpException) {
            Log.w(TAG, "GetDevicePropValue failed: ${e.message}")
            "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    private fun getAvailableSettingValues(camera: CameraInfo, setting: CameraSetting): List<String> {
        return try {
            val propCode = settingToPropCode(setting)
            val (resp, data) = sendPtpCommandReceiveData(PtpOpCode.GET_DEVICE_PROP_DESC, propCode)
            if (resp.code != PtpResponseCode.OK) return emptyList()
            parsePtpPropDesc(data, setting)
        } catch (e: PtpException) {
            Log.w(TAG, "GetDevicePropDesc failed: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun applyCameraSetting(camera: CameraInfo, setting: CameraSetting, value: String): Boolean {
        return try {
            val propCode = settingToPropCode(setting)
            val data = encodePtpPropValue(value, setting)
            val resp = sendPtpCommandWithData(PtpOpCode.SET_DEVICE_PROP_VALUE, data, propCode)
            resp.code == PtpResponseCode.OK
        } catch (e: PtpException) {
            Log.w(TAG, "SetDevicePropValue failed: ${e.message}")
            false
        } catch (e: Exception) {
            false
        }
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

    /** 解析 PTP 属性值（不同数据类型）。
     *  PTP DevicePropValue 格式：[2 字节 dataType][值]。
     *  常见 dataType：0x0001=INT8, 0x0002=UINT8, 0x0003=INT16, 0x0004=UINT16,
     *                0x0005=INT32, 0x0006=UINT32, 0xFFFF=STR */
    private fun parsePtpPropValue(data: ByteArray, setting: CameraSetting): String {
        if (data.size < 2) return "未知"
        val bb = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val dataType = bb.short.toInt() and 0xFFFF
        return try {
            when (dataType) {
                0x0001 -> { val v = bb.get(); formatValue(v.toInt(), setting) }
                0x0002 -> { val v = bb.short.toInt() and 0xFF; formatValue(v, setting) }
                0x0003 -> { val v = bb.short.toInt(); formatValue(v, setting) }
                0x0004 -> { val v = bb.short.toInt() and 0xFFFF; formatValue(v, setting) }
                0x0005 -> { val v = bb.int; formatValue(v, setting) }
                0x0006 -> { val v = bb.int.toLong() and 0xFFFFFFFFL; formatValue(v.toInt(), setting) }
                0xFFFF -> readPtpString(bb)  // 字符串
                else -> "未知(type=$dataType)"
            }
        } catch (_: Exception) { "未知" }
    }

    /** 格式化数值为人类可读字符串（按设置类型） */
    private fun formatValue(value: Int, setting: CameraSetting): String {
        return when (setting) {
            CameraSetting.APERTURE -> {
                // PTP 光圈值是 APEX 格式：Av = 2 * log2(fNumber)
                // fNumber = 2 ^ (Av / 2)
                val fNumber = Math.pow(2.0, value / 2.0)
                "f/${String.format("%.1f", fNumber)}"
            }
            CameraSetting.SHUTTER_SPEED -> {
                // PTP 快门速度是 APEX 格式：Tv = -log2(exposureTime)
                // exposureTime = 2 ^ (-Tv)
                val tv = value / 100.0  // 部分相机用 1/100 秒为单位
                val exposureTime = Math.pow(2.0, -tv)
                if (exposureTime >= 1.0) "${exposureTime.toInt()}s"
                else "1/${(1.0 / exposureTime).toInt()}s"
            }
            CameraSetting.ISO -> "ISO $value"
            CameraSetting.WHITE_BALANCE -> when (value) {
                1 -> "自动"
                2 -> "手动"
                3 -> "日光"
                4 -> "荧光灯"
                5 -> "白炽灯"
                6 -> "闪光灯"
                7 -> "阴天"
                8 -> "阴影"
                else -> "未知($value)"
            }
            CameraSetting.EXPOSURE_COMPENSATION -> "${value / 100.0} EV"
            CameraSetting.FOCUS_MODE -> when (value) {
                1 -> "自动对焦"
                2 -> "手动对焦"
                else -> "未知($value)"
            }
            CameraSetting.IMAGE_FORMAT -> when (value) {
                0x3801 -> "JPEG"
                0x3802 -> "TIFF"
                0x3803 -> "BMP"
                0x3804 -> "CIFF"
                0x3805 -> "GIF"
                0x3806 -> "JFIF"
                0x3808 -> "PICT"
                0x3809 -> "PNG"
                0x380B -> "JPEG2000"
                0x380D -> "RAW"
                else -> "未知(0x${value.toString(16)})"
            }
            CameraSetting.IMAGE_QUALITY -> when (value) {
                1 -> "标准"
                2 -> "高"
                3 -> "精细"
                4 -> "RAW"
                else -> "未知($value)"
            }
        }
    }

    /** 解析 PTP DevicePropDesc（含可选值枚举）。
     *  格式：[2 dataType][1 getSet][2 defaultValue][2 current][2 formFlag][form data]
     *  formFlag=0x01 时为 ENUM，后接 [2 count][count × N 值] */
    private fun parsePtpPropDesc(data: ByteArray, setting: CameraSetting): List<String> {
        if (data.size < 10) return emptyList()
        val bb = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val dataType = bb.short.toInt() and 0xFFFF
        bb.get()  // getSet
        // 跳过 defaultValue（大小取决于 dataType）
        skipPropValue(bb, dataType)
        // 读取 currentValue
        skipPropValue(bb, dataType)
        if (!bb.hasRemaining()) return emptyList()
        val formFlag = bb.get().toInt() and 0xFF
        if (formFlag != 0x01) return emptyList()  // 非 ENUM 类型

        val count = bb.short.toInt() and 0xFFFF
        if (count <= 0 || count > 100) return emptyList()  // 防御异常值
        val result = mutableListOf<String>()
        for (i in 0 until count) {
            if (!bb.hasRemaining()) break
            val v = readPropValueRaw(bb, dataType)
            result.add(formatValue(v, setting))
        }
        return result
    }

    private fun skipPropValue(bb: java.nio.ByteBuffer, dataType: Int) {
        when (dataType) {
            0x0001, 0x0002 -> bb.position(bb.position() + 1)
            0x0003, 0x0004 -> bb.position(bb.position() + 2)
            0x0005, 0x0006 -> bb.position(bb.position() + 4)
            0xFFFF -> readPtpString(bb)  // 读取并丢弃
        }
    }

    private fun readPropValueRaw(bb: java.nio.ByteBuffer, dataType: Int): Int {
        return when (dataType) {
            0x0001 -> bb.get().toInt()
            0x0002 -> bb.short.toInt() and 0xFF
            0x0003 -> bb.short.toInt()
            0x0004 -> bb.short.toInt() and 0xFFFF
            0x0005 -> bb.int
            0x0006 -> bb.int
            else -> 0
        }
    }

    /** 编码字符串为 PTP 属性值（仅支持数值类型，字符串类型用 UTF-16） */
    private fun encodePtpPropValue(value: String, setting: CameraSetting): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // 根据设置类型选择 dataType
        val dataType = when (setting) {
            CameraSetting.WHITE_BALANCE, CameraSetting.FOCUS_MODE,
            CameraSetting.IMAGE_FORMAT, CameraSetting.IMAGE_QUALITY -> 0x0002  // UINT8
            CameraSetting.APERTURE, CameraSetting.SHUTTER_SPEED -> 0x0004      // UINT16
            CameraSetting.ISO, CameraSetting.EXPOSURE_COMPENSATION -> 0x0006   // UINT32
        }
        bb.putShort(dataType.toShort())
        // 解析数值（去掉前缀如 "f/", "ISO ", "1/100s" 等）
        val numericValue = parseNumericValue(value, setting)
        when (dataType) {
            0x0002 -> bb.put(numericValue.toByte())
            0x0004 -> bb.putShort(numericValue.toShort())
            0x0006 -> bb.putInt(numericValue)
        }
        val written = bb.position()
        return bb.array().copyOf(written)
    }

    /** 从用户可读字符串解析数值（如 "f/2.8" → APEX Av 值, "ISO 800" → 800） */
    private fun parseNumericValue(value: String, setting: CameraSetting): Int {
        return try {
            when (setting) {
                CameraSetting.APERTURE -> {
                    // "f/2.8" → Av = 2 * log2(2.8)
                    val fStr = value.removePrefix("f/").trim()
                    val fNumber = fStr.toDouble()
                    (2 * (Math.log(fNumber) / Math.log(2.0))).toInt()
                }
                CameraSetting.SHUTTER_SPEED -> {
                    // "1/100s" → Tv = -log2(1/100) = log2(100)
                    // "1s" → Tv = 0
                    if (value.contains("/")) {
                        val parts = value.removeSuffix("s").split("/")
                        val num = parts[0].toDouble()
                        val den = parts[1].toDouble()
                        val expTime = num / den
                        (-100 * (Math.log(expTime) / Math.log(2.0))).toInt()
                    } else {
                        val expTime = value.removeSuffix("s").toDouble()
                        (-100 * (Math.log(expTime) / Math.log(2.0))).toInt()
                    }
                }
                CameraSetting.ISO -> value.removePrefix("ISO").trim().toInt()
                CameraSetting.EXPOSURE_COMPENSATION -> (value.removeSuffix("EV").trim().toDouble() * 100).toInt()
                CameraSetting.WHITE_BALANCE -> when (value) {
                    "自动" -> 1; "手动" -> 2; "日光" -> 3; "荧光灯" -> 4
                    "白炽灯" -> 5; "闪光灯" -> 6; "阴天" -> 7; "阴影" -> 8
                    else -> 0
                }
                CameraSetting.FOCUS_MODE -> when (value) {
                    "自动对焦" -> 1; "手动对焦" -> 2; else -> 0
                }
                CameraSetting.IMAGE_FORMAT -> when (value) {
                    "JPEG" -> 0x3801; "RAW" -> 0x380D; "TIFF" -> 0x3802; "PNG" -> 0x3809
                    else -> 0
                }
                CameraSetting.IMAGE_QUALITY -> when (value) {
                    "标准" -> 1; "高" -> 2; "精细" -> 3; "RAW" -> 4; else -> 0
                }
            }
        } catch (_: Exception) { 0 }
    }

    private fun initPtpSession(connection: android.hardware.usb.UsbDeviceConnection, camera: CameraInfo): Boolean {
        val device = camera.usbDevice ?: return false
        val endpoints = findPtpEndpoints(device) ?: run {
            Log.e(TAG, "No PTP endpoints found on ${camera.model}")
            return false
        }
        if (!connection.claimInterface(endpoints.`interface`, true)) {
            Log.e(TAG, "Failed to claim PTP interface")
            return false
        }
        ptpConnection = connection
        ptpEndpoints = endpoints
        return try {
            val resp = sendPtpCommandNoData(PtpOpCode.OPEN_SESSION, 1)  // session_id=1
            ptpSessionOpen = (resp.code == PtpResponseCode.OK)
            if (ptpSessionOpen) {
                Log.i(TAG, "PTP session opened for ${camera.model}")
            } else {
                Log.e(TAG, "PTP OpenSession failed: 0x${resp.code.toString(16)}")
            }
            ptpSessionOpen
        } catch (e: PtpException) {
            Log.e(TAG, "PTP OpenSession error: ${e.message}", e)
            false
        }
    }

    /**
     * 查找 PTP 端点配对（批量 in/out + 可选中断 in）。
     * 识别 interfaceClass=6（PTP）或 0xFF（厂商特定，常见于 Canon/Nikon）的接口。
     */
    private fun findPtpEndpoints(device: UsbDevice): PtpEndpoints? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            // PTP interfaceClass=6, 或厂商特定 0xFF
            if (intf.interfaceClass != 6 && intf.interfaceClass != 0xFF) continue
            var bulkIn: android.hardware.usb.UsbEndpoint? = null
            var bulkOut: android.hardware.usb.UsbEndpoint? = null
            var interruptIn: android.hardware.usb.UsbEndpoint? = null
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep
                    else if (ep.direction == UsbConstants.USB_DIR_OUT) bulkOut = ep
                } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                    interruptIn = ep
                }
            }
            if (bulkIn != null && bulkOut != null) {
                return PtpEndpoints(intf, bulkIn, bulkOut, interruptIn)
            }
        }
        return null
    }

    /**
     * 序列化 PTP 容器为字节流（小端序）。
     * 容器布局：[4 长度][2 类型][2 code][4 txnId][N×4 参数 或 N 数据]
     */
    private fun serializeContainer(container: PtpContainer): ByteArray {
        val hasParams = container.type == PtpContainerType.COMMAND ||
            container.type == PtpContainerType.RESPONSE ||
            container.type == PtpContainerType.EVENT
        val paramBytes = if (hasParams) container.params.size * 4 else 0
        val dataBytes = container.data.size
        val totalLength = 12 + paramBytes + dataBytes

        val buffer = java.nio.ByteBuffer.allocate(totalLength)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(totalLength)
        buffer.putShort(container.type)
        buffer.putShort(container.code.toShort())
        buffer.putInt(container.transactionId)
        if (hasParams) {
            for (p in container.params) {
                buffer.putInt(p)
            }
        }
        if (dataBytes > 0) {
            buffer.put(container.data)
        }
        return buffer.array()
    }

    /**
     * 解析 PTP 响应容器（小端序）。
     * 剩余字节按 4 字节切分为 params 数组。
     */
    private fun parseResponse(data: ByteArray): PtpContainer {
        require(data.size >= 12) { "PTP response too short: ${data.size}" }
        val buffer = java.nio.ByteBuffer.wrap(data)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val length = buffer.int and 0xFFFFFFFF.toInt()
        val type = buffer.short
        val code = buffer.short.toInt() and 0xFFFF
        val transactionId = buffer.int

        val paramEnd = if (length in 12..data.size) length else data.size
        val remaining = paramEnd - 12
        val params = mutableListOf<Int>()
        var i = 0
        while (i + 4 <= remaining) {
            params.add(buffer.int)
            i += 4
        }

        return PtpContainer(
            type = type,
            code = code,
            transactionId = transactionId,
            params = params.toIntArray(),
            data = ByteArray(0),
        )
    }

    /**
     * 发送 PTP 命令（无数据阶段）。
     * 三阶段：COMMAND → RESPONSE。Device_Busy 重试 3 次（间隔 200ms）。
     */
    private fun sendPtpCommandNoData(code: Int, vararg params: Int): PtpContainer {
        val endpoints = ptpEndpoints
            ?: throw PtpException(PtpResponseCode.SESSION_NOT_OPEN, "No PTP endpoints")
        val connection = ptpConnection
            ?: throw PtpException(PtpResponseCode.SESSION_NOT_OPEN, "No PTP connection")

        val transactionId = transactionCounter.getAndIncrement()
        val command = PtpContainer(
            type = PtpContainerType.COMMAND,
            code = code,
            transactionId = transactionId,
            params = params.toList().toIntArray(),
        )
        val packet = serializeContainer(command)
        val readBuf = ByteArray(endpoints.inEndpoint.maxPacketSize.coerceAtLeast(512))

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            val sent = connection.bulkTransfer(
                endpoints.outEndpoint, packet, packet.size, 5000,
            )
            if (sent < 0) {
                throw PtpException(PtpResponseCode.GENERAL_ERROR, "Failed to send command (sent=$sent)")
            }

            val received = connection.bulkTransfer(
                endpoints.inEndpoint, readBuf, readBuf.size, 5000,
            )
            if (received < 12) {
                throw PtpException(
                    PtpResponseCode.GENERAL_ERROR,
                    "Failed to receive response (received=$received)",
                )
            }

            val response = parseResponse(readBuf.copyOf(received))
            if (response.code == PtpResponseCode.DEVICE_BUSY) {
                attempt++
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(200)
                    continue
                }
                throw PtpException(PtpResponseCode.DEVICE_BUSY, "Device busy after $MAX_RETRIES retries")
            }
            if (response.code != PtpResponseCode.OK) {
                throw PtpException(response.code, "PTP error: 0x${response.code.toString(16)}")
            }
            return response
        }
        throw PtpException(PtpResponseCode.GENERAL_ERROR, "Exhausted retries")
    }

    /**
     * 发送 PTP 命令并接收数据阶段。
     * 三阶段：COMMAND → DATA (可能多次 bulkTransfer) → RESPONSE。
     * 返回 (response, dataPayload)。
     */
    private fun sendPtpCommandReceiveData(
        code: Int,
        vararg params: Int,
    ): Pair<PtpContainer, ByteArray> {
        val endpoints = ptpEndpoints
            ?: throw PtpException(PtpResponseCode.SESSION_NOT_OPEN, "No PTP endpoints")
        val connection = ptpConnection
            ?: throw PtpException(PtpResponseCode.SESSION_NOT_OPEN, "No PTP connection")

        val transactionId = transactionCounter.getAndIncrement()
        val command = PtpContainer(
            type = PtpContainerType.COMMAND,
            code = code,
            transactionId = transactionId,
            params = params.toList().toIntArray(),
        )
        val commandPacket = serializeContainer(command)
        val readBuf = ByteArray(endpoints.inEndpoint.maxPacketSize.coerceAtLeast(512))

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            val sent = connection.bulkTransfer(
                endpoints.outEndpoint, commandPacket, commandPacket.size, 5000,
            )
            if (sent < 0) {
                throw PtpException(PtpResponseCode.GENERAL_ERROR, "Failed to send command (sent=$sent)")
            }

            // 接收 DATA 阶段（可能分多个包）
            val dataBuffer = java.io.ByteArrayOutputStream()
            var firstRead = true
            var totalExpected = -1
            while (true) {
                val received = connection.bulkTransfer(
                    endpoints.inEndpoint, readBuf, readBuf.size, 5000,
                )
                if (received < 0) {
                    if (firstRead) {
                        throw PtpException(
                            PtpResponseCode.GENERAL_ERROR,
                            "Failed to receive data (received=$received)",
                        )
                    }
                    break
                }
                if (firstRead) {
                    if (received >= 4) {
                        val lenBuf = java.nio.ByteBuffer.wrap(readBuf, 0, 4)
                        lenBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        totalExpected = lenBuf.int
                    }
                    firstRead = false
                }
                dataBuffer.write(readBuf, 0, received)
                if (totalExpected > 0 && dataBuffer.size() >= totalExpected) {
                    break
                }
                if (received < readBuf.size) {
                    // 短包：传输结束
                    break
                }
            }

            // 剥离 12 字节 DATA 容器头
            val fullData = dataBuffer.toByteArray()
            val payload = if (fullData.size > 12) fullData.copyOfRange(12, fullData.size) else ByteArray(0)

            // 接收 RESPONSE
            val respReceived = connection.bulkTransfer(
                endpoints.inEndpoint, readBuf, readBuf.size, 5000,
            )
            if (respReceived < 12) {
                throw PtpException(
                    PtpResponseCode.GENERAL_ERROR,
                    "Failed to receive response (received=$respReceived)",
                )
            }
            val response = parseResponse(readBuf.copyOf(respReceived))

            if (response.code == PtpResponseCode.DEVICE_BUSY) {
                attempt++
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(200)
                    continue
                }
                throw PtpException(PtpResponseCode.DEVICE_BUSY, "Device busy after $MAX_RETRIES retries")
            }
            if (response.code != PtpResponseCode.OK) {
                throw PtpException(response.code, "PTP error: 0x${response.code.toString(16)}")
            }
            return Pair(response, payload)
        }
        throw PtpException(PtpResponseCode.GENERAL_ERROR, "Exhausted retries")
    }

    /**
     * 发送 PTP 命令并发送数据阶段。
     * 三阶段：COMMAND → DATA → RESPONSE。
     */
    private fun sendPtpCommandWithData(
        code: Int,
        data: ByteArray,
        vararg params: Int,
    ): PtpContainer {
        val endpoints = ptpEndpoints
            ?: throw PtpException(PtpResponseCode.SESSION_NOT_OPEN, "No PTP endpoints")
        val connection = ptpConnection
            ?: throw PtpException(PtpResponseCode.SESSION_NOT_OPEN, "No PTP connection")

        val transactionId = transactionCounter.getAndIncrement()
        val command = PtpContainer(
            type = PtpContainerType.COMMAND,
            code = code,
            transactionId = transactionId,
            params = params.toList().toIntArray(),
        )
        val commandPacket = serializeContainer(command)
        val dataContainer = PtpContainer(
            type = PtpContainerType.DATA,
            code = code,
            transactionId = transactionId,
            data = data,
        )
        val dataPacket = serializeContainer(dataContainer)
        val readBuf = ByteArray(endpoints.inEndpoint.maxPacketSize.coerceAtLeast(512))

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            val sentCmd = connection.bulkTransfer(
                endpoints.outEndpoint, commandPacket, commandPacket.size, 5000,
            )
            if (sentCmd < 0) {
                throw PtpException(PtpResponseCode.GENERAL_ERROR, "Failed to send command (sent=$sentCmd)")
            }

            // 发送 DATA 阶段，按 outEndpoint 最大包大小分片
            val maxPacket = endpoints.outEndpoint.maxPacketSize.coerceAtLeast(512)
            var offset = 0
            while (offset < dataPacket.size) {
                val chunkSize = minOf(maxPacket, dataPacket.size - offset)
                val chunk = dataPacket.copyOfRange(offset, offset + chunkSize)
                val sent = connection.bulkTransfer(
                    endpoints.outEndpoint,
                    chunk,
                    chunk.size,
                    5000,
                )
                if (sent < 0) {
                    throw PtpException(PtpResponseCode.GENERAL_ERROR, "Failed to send data chunk (sent=$sent)")
                }
                offset += chunkSize
            }

            val respReceived = connection.bulkTransfer(
                endpoints.inEndpoint, readBuf, readBuf.size, 5000,
            )
            if (respReceived < 12) {
                throw PtpException(
                    PtpResponseCode.GENERAL_ERROR,
                    "Failed to receive response (received=$respReceived)",
                )
            }
            val response = parseResponse(readBuf.copyOf(respReceived))

            if (response.code == PtpResponseCode.DEVICE_BUSY) {
                attempt++
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(200)
                    continue
                }
                throw PtpException(PtpResponseCode.DEVICE_BUSY, "Device busy after $MAX_RETRIES retries")
            }
            if (response.code != PtpResponseCode.OK) {
                throw PtpException(response.code, "PTP error: 0x${response.code.toString(16)}")
            }
            return response
        }
        throw PtpException(PtpResponseCode.GENERAL_ERROR, "Exhausted retries")
    }

    private fun findBulkOutEndpoint(
        connection: android.hardware.usb.UsbDeviceConnection,
    ): android.hardware.usb.UsbEndpoint? {
        // 已由 findPtpEndpoints 管理，此处保留兼容
        return ptpEndpoints?.outEndpoint
    }

    private suspend fun triggerCaptureInternal(): CaptureResult? {
        val conn = ptpConnection ?: return null
        val eps = ptpEndpoints ?: return null
        if (!ptpSessionOpen) return null

        return withContext(Dispatchers.IO) {
            try {
                // 1. 发送 INITIATE_CAPTURE (0x100E)，参数：storage_id=0xFFFFFFFF(任意), format_code=0(任意)
                val resp = sendPtpCommandNoData(PtpOpCode.INITIATE_CAPTURE, 0xFFFFFFFF, 0x00000000)
                if (resp.code != PtpResponseCode.OK) {
                    Log.e(TAG, "InitiateCapture failed: 0x${resp.code.toString(16)}")
                    return@withContext null
                }

                // 2. 等待 ObjectAdded 事件（通过 interrupt endpoint 轮询，超时 10 秒）
                val objectHandle = waitForObjectAddedEvent(10000L) ?: run {
                    Log.e(TAG, "Timeout waiting for ObjectAdded event")
                    return@withContext null
                }

                // 3. GetObjectInfo 获取文件名和大小
                val (infoResp, infoData) = sendPtpCommandReceiveData(PtpOpCode.GET_OBJECT_INFO, objectHandle)
                if (infoResp.code != PtpResponseCode.OK) {
                    Log.e(TAG, "GetObjectInfo failed: 0x${infoResp.code.toString(16)}")
                    return@withContext null
                }
                val objInfo = parseObjectInfo(infoData)

                // 4. GetObject 下载图像数据
                val (getResp, imageData) = sendPtpCommandReceiveData(PtpOpCode.GET_OBJECT, objectHandle)
                if (getResp.code != PtpResponseCode.OK) {
                    Log.e(TAG, "GetObject failed: 0x${getResp.code.toString(16)}")
                    return@withContext null
                }

                // 5. 写入本地文件
                val outputDir = context.getExternalFilesDir("captures") ?: return@withContext null
                if (!outputDir.exists()) outputDir.mkdirs()
                val fileName = if (objInfo.fileName.isNotEmpty()) objInfo.fileName
                              else "capture_${System.currentTimeMillis()}.jpg"
                val outputFile = File(outputDir, fileName)
                File(outputFile.absolutePath).writeBytes(imageData)

                Log.i(TAG, "Capture saved: ${outputFile.absolutePath} (${imageData.size} bytes)")
                CaptureResult(
                    localPath = outputFile.absolutePath,
                    fileName = fileName,
                    fileSize = imageData.size.toLong(),
                    timestamp = System.currentTimeMillis(),
                )
            } catch (e: PtpException) {
                Log.e(TAG, "PTP capture error: ${e.message}", e)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed: ${e.message}", e)
                null
            }
        }
    }

    private fun fetchLiveViewFrame(): ByteArray? {
        val conn = ptpConnection ?: return null
        val eps = ptpEndpoints ?: return null
        if (!ptpSessionOpen) return null

        return try {
            // 厂商扩展 LiveView（Nikon: 0x9201, Canon: 0x9153）
            // 通用 PTP 标准无 LiveView 命令，此处尝试 Nikon 扩展
            val manufacturer = _connectedCameras.value.firstOrNull()?.manufacturer?.lowercase() ?: ""
            val liveViewOpCode = when {
                manufacturer.contains("nikon") || manufacturer.contains("尼康") -> 0x9201
                manufacturer.contains("canon") || manufacturer.contains("佳能") -> 0x9153
                else -> return null  // 不支持的厂商，不模拟
            }
            val (resp, data) = sendPtpCommandReceiveData(liveViewOpCode)
            if (resp.code != PtpResponseCode.OK) return null
            // Nikon LiveView 数据格式：[偏移头][JPEG 数据]
            // 简化：直接返回数据，调用方解码 JPEG
            data
        } catch (e: PtpException) {
            Log.w(TAG, "LiveView fetch failed: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "LiveView fetch error: ${e.message}")
            null
        }
    }

    private fun waitForObjectAddedEvent(timeoutMs: Long): Int? {
        val eps = ptpEndpoints ?: return null
        val conn = ptpConnection ?: return null
        val interruptEp = eps.interruptEndpoint ?: return null

        val buffer = ByteArray(interruptEp.maxPacketSize.coerceAtLeast(64))
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val read = conn.bulkTransfer(interruptEp, buffer, buffer.size, 500)
            if (read > 0) {
                // 解析事件容器：[4 长度][2 类型=EVENT][2 code][4 txnId][4 param]
                if (read >= 12) {
                    val bb = java.nio.ByteBuffer.wrap(buffer, 0, read).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val length = bb.int
                    val type = bb.short
                    val code = bb.short.toInt() and 0xFFFF
                    val txnId = bb.int
                    if (type == PtpContainerType.EVENT && code == 0x4002 /* ObjectAdded */) {
                        return if (read >= 16) bb.int else null  // 返回 object handle
                    }
                }
            }
        }
        return null
    }

    private fun parseObjectInfo(data: ByteArray): ObjectInfo {
        val bb = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val storageId = bb.int
        val formatCode = bb.short.toInt() and 0xFFFF
        val protectionStatus = bb.short.toInt() and 0xFFFF
        val objectCompressedSize = bb.int.toLong() and 0xFFFFFFFFL
        val thumbFormat = bb.short.toInt() and 0xFFFF
        val thumbCompressedSize = bb.short.toInt() and 0xFFFF
        val thumbWidth = bb.int
        val thumbHeight = bb.int
        val imagePixWidth = bb.int
        val imagePixHeight = bb.int
        val imageBitDepth = bb.int
        val parentObject = bb.int
        val associationType = bb.short.toInt() and 0xFFFF
        val associationDesc = bb.int
        val sequenceNumber = bb.int
        // 文件名：UTF-16LE null-terminated string（前 1 字节是字符数）
        val fileName = readPtpString(bb)
        val captureDate = readPtpDateTime(bb)
        val modificationDate = readPtpDateTime(bb)
        val keywords = readPtpString(bb)
        return ObjectInfo(storageId, formatCode, protectionStatus, objectCompressedSize,
            thumbFormat, thumbCompressedSize, thumbWidth, thumbHeight,
            imagePixWidth, imagePixHeight, imageBitDepth, parentObject,
            associationType, associationDesc, sequenceNumber,
            fileName, captureDate, modificationDate, keywords)
    }

    /** 读取 PTP 字符串：1 字节长度（字符数）+ UTF-16LE 数据（不含结尾 null） */
    private fun readPtpString(bb: java.nio.ByteBuffer): String {
        return try {
            val charCount = bb.get().toInt() and 0xFF
            if (charCount == 0) ""
            else {
                val sb = StringBuilder(charCount)
                for (i in 0 until charCount) {
                    sb.append(bb.getChar())
                }
                sb.toString()
            }
        } catch (_: Exception) { "" }
    }

    /** 读取 PTP 日期时间字符串：1 字节长度 + ASCII "YYYY:MM:DD HH:MM:SS\0" */
    private fun readPtpDateTime(bb: java.nio.ByteBuffer): String {
        return try {
            val len = bb.get().toInt() and 0xFF
            if (len == 0) ""
            else {
                val bytes = ByteArray(len)
                bb.get(bytes)
                // 去除结尾 null
                String(bytes, Charsets.US_ASCII).trimEnd('\u0000')
            }
        } catch (_: Exception) { "" }
    }

    private suspend fun downloadObject(handle: Int) {
        val (infoResp, infoData) = sendPtpCommandReceiveData(PtpOpCode.GET_OBJECT_INFO, handle)
        if (infoResp.code != PtpResponseCode.OK) return
        val objInfo = parseObjectInfo(infoData)

        val (getResp, imageData) = sendPtpCommandReceiveData(PtpOpCode.GET_OBJECT, handle)
        if (getResp.code != PtpResponseCode.OK) return

        val outputDir = context.getExternalFilesDir("captures") ?: return
        if (!outputDir.exists()) outputDir.mkdirs()
        val fileName = if (objInfo.fileName.isNotEmpty()) objInfo.fileName
                       else "img_${handle}.jpg"
        val outputFile = File(outputDir, fileName)
        outputFile.writeBytes(imageData)

        val result = CaptureResult(
            localPath = outputFile.absolutePath,
            fileName = fileName,
            fileSize = imageData.size.toLong(),
            timestamp = System.currentTimeMillis(),
        )
        _capturedImages.update { it + result }

        _activeSession.value?.let { session ->
            _activeSession.value = session.copy(images = session.images + result.localPath)
        }
        Log.i(TAG, "Auto-imported: ${outputFile.absolutePath}")
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
        val conn = ptpConnection ?: return
        val eps = ptpEndpoints ?: return
        if (!ptpSessionOpen) return

        withContext(Dispatchers.IO) {
            try {
                // 1. GetNumObjects (0x1006)，参数：storage_id=0xFFFFFFFF, format=0, assoc=0
                val (numResp, numData) = sendPtpCommandReceiveData(PtpOpCode.GET_NUM_OBJECTS,
                    0xFFFFFFFF, 0x00000000, 0x00000000)
                if (numResp.code != PtpResponseCode.OK) return@withContext
                val currentCount = if (numData.size >= 4) {
                    java.nio.ByteBuffer.wrap(numData).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                } else 0

                // 2. 比较上次记录数量
                if (currentCount <= lastObjectCount) return@withContext

                // 3. GetObjectHandles 获取所有句柄
                val (handlesResp, handlesData) = sendPtpCommandReceiveData(PtpOpCode.GET_OBJECT_HANDLES,
                    0xFFFFFFFF, 0x00000000, 0x00000000)
                if (handlesResp.code != PtpResponseCode.OK) return@withContext

                // 解析句柄数组（每 4 字节一个）
                val handles = mutableListOf<Int>()
                val bb = java.nio.ByteBuffer.wrap(handlesData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                while (bb.remaining() >= 4) {
                    handles.add(bb.int)
                }

                // 4. 下载新对象（只下载尚未见过的）
                val newHandles = if (knownObjectHandles.isEmpty()) {
                    // 首次：只记录，不自动下载
                    knownObjectHandles.addAll(handles)
                    lastObjectCount = handles.size
                    emptyList()
                } else {
                    val newOnes = handles.filter { it !in knownObjectHandles }
                    knownObjectHandles.addAll(handles)
                    lastObjectCount = handles.size
                    newOnes
                }

                for (handle in newHandles) {
                    runCatching { downloadObject(handle) }
                }
            } catch (e: PtpException) {
                Log.w(TAG, "checkAndImportNewImages PTP error: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "checkAndImportNewImages failed: ${e.message}")
            }
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
    const val USB_DIR_OUT = 0
    const val USB_DIR_IN = 0x80
}