package com.handsome.easyclip.wight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 负责画裁剪框和背后的阴影
 */
class ClipView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr){
    // paint负责亮区域的绘制
    private val mLightPaint : Paint = Paint()
    // clip负责裁剪边框的绘制
    private val mClipBorderPaint : Paint = Paint()
    // 裁剪区域
    private val mClipRect = RectF()
    // 负责设置相交模式
    private val mXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    // 设置裁剪比例
    private var mAspectRatio = 1f

    // 配置区域
    // 蒙版颜色
    private var mMaskColor : Int= Color.parseColor("#a8000000")

    init{
        setLayerType(LAYER_TYPE_SOFTWARE, null)  // 禁止硬件GPU加速
        initPaintConfig()
    }

    private fun initPaintConfig() {
        // 抗锯齿
        mLightPaint.isAntiAlias = true
        // 设置xfermode模式，此模式为绘制不相交的，相交部分透明。
        mLightPaint.setXfermode(mXfermode)

        // 设置边框厚度和颜色
        mClipBorderPaint.strokeWidth = 8f
        mClipBorderPaint.isAntiAlias = true
        // 设置为描边
        mClipBorderPaint.style = Paint.Style.STROKE
        mClipBorderPaint.color = Color.parseColor("#FFFFFF")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawClipRect(canvas)
    }

    private fun drawClipRect(canvas: Canvas) {
        // 保存画布
        canvas.saveLayer(0f, 0f, this.width.toFloat(), this.height.toFloat(), null)
        // 绘制背景为蒙板颜色
        canvas.drawColor(mMaskColor)
        // 裁剪区域不绘制，其他地方绘制为蒙板颜色
        canvas.drawRect(mClipRect,mLightPaint)
        // 绘制裁剪的边框
        canvas.drawRect(mClipRect,mClipBorderPaint)
        // 恢复画布，并且将期间绘制的内容绘制到原来的画布上
        canvas.restore()
    }

    fun setClipRect(clipRect : RectF){
        mClipRect.set(clipRect)
        mAspectRatio = mClipRect.width() / mClipRect.height()
        invalidate()
    }

    fun getAspectRatio() : Float{
        return mAspectRatio
    }

    fun getClipRect() : RectF{
        return RectF(mClipRect)
    }

    fun setMaskColor(color : Int){
        mMaskColor = color
        invalidate()
    }

    fun setMaskColor(color : String){
        setMaskColor(Color.parseColor(color))
    }

    fun setClipBorderColor(color : Int){
        mClipBorderPaint.color = color
        invalidate()
    }

    fun setClipBorderColor(color : String){
        setClipBorderColor(Color.parseColor(color))
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }
}