package com.rapidrawpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotationpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.Filepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javapackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android Imagepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, Dpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG =package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFFpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private valpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) //package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endianpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.topackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        privatepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.topackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (samepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOfpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrwpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext =package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        privatepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0xpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const valpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xCpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL =package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_Wpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC6package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const valpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIXpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIFpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITEpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const valpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFSpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WBpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        privatepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC6package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF IIpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = bytepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic:package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Doublepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val camerapackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth:package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data classpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        valpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val usepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArraypackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Booleanpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            ifpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggbpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            varpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result +package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Formatpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: Filepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extensionpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extensionpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sonypackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikonpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormatpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2",package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" ->package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> Rawpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srwpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" ->package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     *package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fispackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesReadpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAFpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    headerpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormatpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFFpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(headerpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" ||package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFFpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specificpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, headerpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (headerpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset =package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.sizepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(),package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormatpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offsetpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        valpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4,package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType ==package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exceptionpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAWpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEFpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use Exifpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = Expackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = expackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttributepackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sonypackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")
                make.contains("fujifilm") || make.contains("fuji") -> RawFormat("Fpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")
                make.contains("fujifilm") || make.contains("fuji") -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                make.contains("panasonic")package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")
                make.contains("fujifilm") || make.contains("fuji") -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                make.contains("panasonic") -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
                make.contains("package com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")
                make.contains("fujifilm") || make.contains("fuji") -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                make.contains("panasonic") -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
                make.contains("olympus") -> RawFormat("Olympus ORF", "orf", "Olymppackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")
                make.contains("fujifilm") || make.contains("fuji") -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                make.contains("panasonic") -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
                make.contains("olympus") -> RawFormat("Olympus ORF", "orf", "Olympus")
                make.contains("pentax") -> Rawpackage com.rapidraw.core

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 原生RAW文件解码器
 * 使用Android ImageDecoder API + ExifInterface实现真正的RAW格式处理
 * 支持主流相机RAW格式：ARW, CR2, CR3, NEF, RAF, RW2, ORF, DNG
 */
class RawDecoder(private val context: Context) {

    companion object {
        private const val TAG = "RawDecoder"

        // RAW file magic numbers for format detection
        // Sony ARW: starts with TIFF little-endian or has Sony-specific header
        private val ARW_MAGIC = byteArrayOf(0x00.toByte(), 0x00.toByte()) // Sony
        // Canon CR2: TIFF little-endian II
        private val CR2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // Canon (TIFF-based)
        // Nikon NEF: TIFF big-endian MM
        private val NEF_MAGIC = byteArrayOf(0x4D.toByte(), 0x4D.toByte()) // Nikon (TIFF-based)
        // DNG: TIFF little-endian II (same as CR2 base, differentiated by DNG tags)
        private val DNG_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte()) // DNG (TIFF-based)

        // Supported RAW extensions
        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "nrw", "raf", "rw2", "orf", "dng", "pef", "srw", "raw"
        )

        fun isRawFile(path: String): Boolean {
            val ext = path.substringAfterLast('.', "").lowercase()
            return ext in RAW_EXTENSIONS
        }

        // TIFF tag constants
        private const val TIFF_TAG_SUB_IFD = 0x014A
        private const val TIFF_TAG_CFA_PATTERN = 0x828E
        private const val TIFF_TAG_DNG_VERSION = 0xC612
        private const val TIFF_TAG_WHITE_LEVEL = 0xC61D
        private const val TIFF_TAG_BLACK_LEVEL = 0xC61A
        private const val TIFF_TAG_WB_RGE_COEFFS = 0xC621
        private const val TIFF_TAG_AS_SHOT_NEUTRAL = 0xC628
        private const val TIFF_TAG_AS_SHOT_WHITE_XY = 0xC629
        private const val TIFF_TAG_COLOR_MATRIX_1 = 0xC621

        // EXIF White Balance tag
        private const val EXIF_TAG_WHITE_BALANCE = 0xA403
        private const val EXIF_TAG_WB_RBGG_COEFFS = 0xA404 // ColorSpace / WB_RGGBLevels

        // DNG Version tag offset
        private const val DNG_VERSION_TAG = 0xC612

        // Fujifilm RAF magic: "FUJIFILMCCD-RAW "
        private val RAF_MAGIC = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)

        // Panasonic RW2 magic: TIFF II with Panasonic maker
        private val RW2_MAGIC = byteArrayOf(0x49.toByte(), 0x49.toByte())

        // Olympus ORF magic: TIFF MM or II with Olympus maker
        private val ORF_MAGIC_II = byteArrayOf(0x49.toByte(), 0x49.toByte())
        private val ORF_MAGIC_MM = byteArrayOf(0x4D.toByte(), 0x4D.toByte())
    }

    data class RawInfo(
        val width: Int,
        val height: Int,
        val iso: Int,
        val exposureTime: Double,
        val aperture: Double,
        val focalLength: Double,
        val whiteBalance: Int,
        val cameraModel: String,
        val lensModel: String,
        val captureTime: Long,
        val bitDepth: Int,
        val isRaw: Boolean
    )

    data class DecodeOptions(
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val preserveExif: Boolean = true,
        val useEmbeddedPreview: Boolean = false
    )

    data class WhiteBalanceCoeffs(
        val rggb: FloatArray,
        val temperature: Int,
        val tint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WhiteBalanceCoeffs) return false
            return rggb.contentEquals(other.rggb) && temperature == other.temperature && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = rggb.contentHashCode()
            result = 31 * result + temperature
            result = 31 * result + tint
            return result
        }
    }

    data class RawFormat(
        val name: String,
        val extension: String,
        val manufacturer: String
    )

    // ========================================================================
    // Format Detection
    // ========================================================================

    /**
     * 检测RAW文件的具体格式
     */
    fun detectRawFormat(file: File): RawFormat? {
        if (!file.exists() || !file.canRead()) return null

        val extension = file.extension.lowercase()
        return when (extension) {
            "arw" -> RawFormat("Sony ARW", "arw", "Sony")
            "cr2" -> RawFormat("Canon CR2", "cr2", "Canon")
            "cr3" -> RawFormat("Canon CR3", "cr3", "Canon")
            "nef" -> RawFormat("Nikon NEF", "nef", "Nikon")
            "nrw" -> RawFormat("Nikon NRW", "nrw", "Nikon")
            "raf" -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
            "rw2" -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
            "orf" -> RawFormat("Olympus ORF", "orf", "Olympus")
            "dng" -> RawFormat("Adobe DNG", "dng", "Adobe")
            "pef" -> RawFormat("Pentax PEF", "pef", "Pentax")
            "srw" -> RawFormat("Samsung SRW", "srw", "Samsung")
            "raw" -> detectRawFormatByMagic(file)
            else -> detectRawFormatByMagic(file)
        }
    }

    /**
     * 通过magic number检测RAW格式
     */
    private fun detectRawFormatByMagic(file: File): RawFormat? {
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val bytesRead = fis.read(header)
                if (bytesRead < 4) return null

                // Check Fujifilm RAF
                if (header.size >= RAF_MAGIC.size &&
                    header.sliceArray(0 until RAF_MAGIC.size).contentEquals(RAF_MAGIC)
                ) {
                    return RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                }

                // Check TIFF byte order markers
                val byteOrderMark = String(header, 0, 2, Charsets.US_ASCII)
                if (byteOrderMark == "II" || byteOrderMark == "MM") {
                    // TIFF-based RAW - need to check maker notes or specific tags
                    return detectTiffBasedFormat(file, header)
                }

                // Canon CR3 uses ISOBMFF container (ftyp box)
                if (header.size >= 8) {
                    val ftypOffset = readUInt32BE(header, 4)
                    if (ftypOffset in 4..64 && header.size >= ftypOffset + 8) {
                        val boxType = String(header, ftypOffset.toInt(), 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                    // Direct ftyp at offset 4
                    if (header.size >= 12) {
                        val boxType = String(header, 4, 4, Charsets.US_ASCII)
                        if (boxType == "ftyp") {
                            return RawFormat("Canon CR3", "cr3", "Canon")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting RAW format: ${e.message}")
        }
        return null
    }

    /**
     * 检测TIFF-based RAW格式（ARW, CR2, NEF, DNG, RW2, ORF, PEF, SRW）
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): RawFormat? {
        try {
            // Use ExifInterface to read make/model
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase() ?: ""

            return when {
                make.contains("sony") -> RawFormat("Sony ARW", "arw", "Sony")
                make.contains("canon") -> RawFormat("Canon CR2", "cr2", "Canon")
                make.contains("nikon") -> RawFormat("Nikon NEF", "nef", "Nikon")
                make.contains("fujifilm") || make.contains("fuji") -> RawFormat("Fujifilm RAF", "raf", "Fujifilm")
                make.contains("panasonic") -> RawFormat("Panasonic RW2", "rw2", "Panasonic")
                make.contains("olympus") -> RawFormat("Olympus ORF", "orf", "Olympus")
                make.contains("pentax") -> RawFormat("Pentax PEF", "pef", "Pentax")
                make.contains("