package com.handsome.easyclip.wight.helper

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity.WINDOW_SERVICE
import java.io.File
import java.io.OutputStream

object ClipViewHelper {

    fun getScreenWidth(context: Context): Int {
        val wm = context.getSystemService(WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(outMetrics)
        return outMetrics.widthPixels
    }

    /**
     * 根据Uri返回文件绝对路径
     * 兼容了file:///开头的 和 content://开头的情况
     */
    fun getRealFilePathFromUri(context: Context, uri: Uri?): String? {
        if (null == uri) return null
        val scheme = uri.scheme
        var resultPath: String? = null
        if (scheme == null) {
            resultPath = uri.path
        } else if (ContentResolver.SCHEME_FILE.equals(scheme, ignoreCase = true)) {
            resultPath = uri.path
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme, ignoreCase = true)) {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.ImageColumns.DATA),
                null,
                null,
                null
            )
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                    if (index > -1) {
                        resultPath = cursor.getString(index)
                    }
                }
                cursor.close()
            }
        }
        return resultPath
    }

    /**
     * 将bitmap转换成为uri
     */
    fun bitmapToUri(context: Context,bitmap: Bitmap) : Uri?{
        // 调用返回剪切图
        val resultUri = Uri.fromFile(File(context.cacheDir, "clip_${System.currentTimeMillis()}.jpg"))
        if (resultUri == null){
            Log.e(javaClass.name, "获取磁盘路径失效!")
            return null
        }
        var outputStream: OutputStream? = null
        val result = runCatching {
            outputStream = context.contentResolver.openOutputStream(resultUri)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream!!)
        }
        if (result.isFailure){
            Log.e(javaClass.name, "不能打开${resultUri}")
            return null
        }
        val closeResult = runCatching {
            outputStream?.close()
        }
        if (closeResult.isFailure){
            Log.e(javaClass.name, "不能关闭输出流")
            return null
        }
        return resultUri
    }

    /**
     * 图片等比例压缩，会接近传入的期望宽高
     *
     * @param reqWidth  期望的宽
     * @param reqHeight 期望的高
     */
    fun decodeSampledBitmap(
        filePath: String?, reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        // 不解码但是能获得到bitmap实际的宽高
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        options.inPreferredConfig = Bitmap.Config.RGB_565
        BitmapFactory.decodeFile(filePath, options)

        options.inSampleSize = calculateInSampleSize(
            options, reqWidth,
            reqHeight
        )
        // 用指定大小解码
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(filePath, options)
    }

    /**
     * 计算InSampleSize
     * 宽的压缩比和高的压缩比的较小值  取接近的2的次幂的值
     * 比如宽的压缩比是3 高的压缩比是5 取较小值3  而InSampleSize必须是2的次幂，取接近的2的次幂4
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int, reqHeight: Int
    ): Int {
        // bitmap原本的宽高
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())

            // 选择较小的压缩比作为初始的 ratio 值，保证最终生成的图片在两个维度上都大于或等于期望的尺寸。
            val ratio = if (heightRatio < widthRatio) heightRatio else widthRatio
            // inSampleSize只能是2的次幂  将ratio就近取2的次幂的值
            inSampleSize = if (ratio < 3) ratio
            else if (ratio < 6.5) 4
            else if (ratio < 8) 8
            else ratio
        }

        return inSampleSize
    }
}