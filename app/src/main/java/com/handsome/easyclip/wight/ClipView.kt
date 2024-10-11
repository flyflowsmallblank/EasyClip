package com.handsome.easyclip.wight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.handsome.easyclip.wight.bean.PointerBean

/**
 * 负责画裁剪框
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
    private val xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    // 设置裁剪比例
    private var mAspectRadio = 1f


    // 配置区域
    // 蒙版颜色
    private var mMaskColor : Int= Color.parseColor("#a8000000")
    // 框颜色
    private var mClipBorderColor : Int = Color.parseColor("#FFFFFF")

    init{
        initPaintConfig()
    }

    private fun initPaintConfig() {
        // 抗锯齿
        mLightPaint.isAntiAlias = true
        // 设置xfermode模式，此模式为绘制不相交的，相交部分透明。
        mLightPaint.setXfermode(xfermode)

        // 设置边框厚度和颜色
        mClipBorderPaint.strokeWidth = 8f
        mClipBorderPaint.isAntiAlias = true
        // 设置为描边
        mClipBorderPaint.style = Paint.Style.STROKE
        mClipBorderPaint.color = mClipBorderColor
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
        invalidate()
    }

    /**
     * 设置裁剪比例
     */
    fun setAspectRadio(aspectRadio : Float){
        if (aspectRadio <= 0){
            Log.d("lx", "aspectRadio不能为null")
            return
        }
        mAspectRadio = aspectRadio
    }

    /**
     * 设置裁剪矩形，高根据比例自动换算。
     */
    fun setClipRectWidth(leftTopPointer : PointerBean,width : Int){
        val left = leftTopPointer.x
        val top = leftTopPointer.y
        val right = left + width
        val bottom = top + width / mAspectRadio
        mClipRect.set(left,top,right,bottom)
        invalidate()
    }

    /**
     * 设置裁剪矩形，宽根据比例自动换算。
     */
    fun setClipRectHeight(leftTopPointer : PointerBean,height : Int){
        val left = leftTopPointer.x
        val top = leftTopPointer.y
        val right = left + height * mAspectRadio
        val bottom = top + height
        mClipRect.set(left,top,right,bottom)
        invalidate()
    }

    fun getClipRect() : RectF{
        return mClipRect
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }
}