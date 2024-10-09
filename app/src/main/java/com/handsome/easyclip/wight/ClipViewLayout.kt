package com.handsome.easyclip.wight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.RelativeLayout
import com.handsome.easyclip.wight.bean.PointerBean
import com.handsome.easyclip.wight.bean.PointerMode
import com.handsome.easyclip.wight.helper.ClipViewHelper
import kotlin.math.abs
import kotlin.math.sqrt


/**
 * 1. 负责绘制底层图片
 * 2. 负责裁剪图片
 * 3. 负责拖动图片
 */
class ClipViewLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
    // 负责绘制框的
    private val mClipView = ClipView(context)

    // 展示的Imageview
    private val mImageView = ImageView(context)

    // 进行矩阵变化
    private val mMatrix = Matrix()

    // 临时保存的matrix
    private val mTempMatrix = Matrix()

    // 手指模式
    private var mPointerMode = PointerMode.DRAG_POINTER

    // 第一个手指上次处于的位置
    private val mFirstLastDownPointer = PointerBean(-1f, -1f)

    // 第二个手指down的位置
    private val mSecondLastDownPointer = PointerBean(-1f, -1f)

    // 如果有第二个手指的情况，中间位置
    private var mMidPointer = PointerBean(-1f, -1f)

    // 如果有第二个手指的情况，刚开始两个手指的距离，即平方差
    private var mTwoPointerDistance = -1f

    // 变化的坐标
    private var mDeltaPointer = PointerBean(-1f, -1f)

    // 当前固定的比例，宽高比例
    private var mAspectRadio = 1f

    // 存储matrix的9个值
    private var mMatrixValues = FloatArray(9)

    // 最小scale的值
    private var mMinScale = 0.5f

    // 最大scale的值
    private var mMaxScale = 2f

    init {
        mClipView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        mImageView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        mImageView.scaleType = ImageView.ScaleType.MATRIX
        addView(mImageView)
        addView(mClipView)
    }

    /**
     * 更新图片的显示
     */
    fun setImageUri(uri: Uri?,isResetMatrix : Boolean) {
        if (uri == null) {
            Log.e(javaClass.name, "uri不能为null")
        }
        if (isResetMatrix){
            mMatrix.reset()
        }

        val path = ClipViewHelper.getRealFilePathFromUri(context, uri)
        val bitmap = ClipViewHelper.decodeSampledBitmap(path, 1080, 720)

        // 根据比例设置minScale和maxScale
        val minScale = setBorderScale(bitmap)
        mMinScale = minScale
        mTempMatrix.preScale(minScale,minScale)

        // 平移,将缩放后的图片平移到imageview的中心
        //imageView的中心x
        val midX= mImageView.width / 2
        //imageView的中心y
        val midY= mImageView.height / 2
        //bitmap的中心x
        val imageMidX = (bitmap.width * minScale / 2)
        //bitmap的中心y
        val imageMidY = (bitmap.height * minScale / 2)
        val deltaX = midX - imageMidX
        val deltaY = midY - imageMidY
        mTempMatrix.preTranslate(deltaX,deltaY)

        mImageView.imageMatrix = mTempMatrix
        mImageView.setImageBitmap(bitmap)
    }

    /**
     * 根据[mAspectRadio]设置最小比例
     * 假如mAspectRadio9:16。图片长宽高比例为9:15，高相对较短，以高为准。
     * @return 返回最小尺寸
     */
    private fun setBorderScale(bitmap: Bitmap): Float {
        val minScale : Float
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        val bitmapAspectRadio = bitmapWidth / bitmapHeight
        val clipRect = mClipView.getClipRect()
        if (bitmapAspectRadio > mAspectRadio) {
            // 说明图片的宽相对比高更大，以图片的高为准
            // 计算框的高和图片的高的比例
            val clipHeight = clipRect.height()
            minScale = clipHeight / bitmapHeight
        } else {
            // 同理
            val clipWidth = clipRect.width()
            minScale = clipWidth / bitmapWidth
        }
        return minScale
    }

    /**
     * 获得当前的缩放比例
     */
    private fun getScale(): Float {
        mMatrix.getValues(mMatrixValues)
        return mMatrixValues[Matrix.MSCALE_X]
    }

    /**
     * 负责裁剪图片
     */
    fun clipBitMap(): Uri? {
        val width = mImageView.width
        val height = mImageView.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        mImageView.draw(canvas)
        val clipRect = mClipView.getClipRect()
        val clipBitmap = Bitmap.createBitmap(
            bitmap,
            clipRect.left.toInt(),
            clipRect.top.toInt(),
            clipRect.width().toInt(),
            clipRect.height().toInt()
        )
        bitmap.recycle()
        val clipUri = ClipViewHelper.bitmapToUri(context, clipBitmap)
        clipBitmap.recycle()
        return clipUri
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mMatrix.set(mTempMatrix)
                // 记录第一个手指的触碰位置
                mFirstLastDownPointer.setData(x = event.x, y = event.y)
                mPointerMode = PointerMode.DRAG_POINTER
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 三根及以上手指不处理
                if (event.pointerCount != 2) return true
                mSecondLastDownPointer.setData(event.getX(1), event.getY(1))
                mTwoPointerDistance =
                    calculateTwoPointerDistance(mFirstLastDownPointer, mSecondLastDownPointer)
                // 离得太近倍数太大
                if (mTwoPointerDistance < 20f) {
                    mTwoPointerDistance = -1f
                    mSecondLastDownPointer.setData(-1f, -1f)
                    return true
                }
                mMatrix.set(mTempMatrix)
                // 默认就是单指，这里是将其变成多指mode
                mPointerMode = PointerMode.SCALE_POINTER
                // 获得两指之间的位置
                calculateTwoPointerMidLoc(
                    mMidPointer,
                    mFirstLastDownPointer,
                    mSecondLastDownPointer
                )
            }
            // 手指离开屏幕
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                onActionUp()
            }
            // 第二根手指离开屏幕，不允许再移动
            MotionEvent.ACTION_POINTER_UP -> {
                mPointerMode = PointerMode.NONE_POINTER
            }

            MotionEvent.ACTION_MOVE -> {
                onActionMove(event)
            }

            else -> return true
        }
        return true
    }

    /**
     * 获取ImageView根据matrix缩放的
     */
    private fun ImageView.getImageViewRect(): RectF {
        val rect = RectF()
        val imgDrawable = this.drawable
        val matrix = this.imageMatrix
        if (imgDrawable == null) {
            Log.d("lx", "drawable不能为null")
            return rect
        }
        if (matrix == null) {
            Log.d("lx", "ImageView的matrix不能为null")
            return rect
        }
        rect.set(
            0f,
            0f,
            imgDrawable.intrinsicWidth.toFloat(),
            imgDrawable.intrinsicHeight.toFloat()
        )
        matrix.mapRect(rect)
        return rect
    }

    /**
     * 判断经过拖拽后图片所在的区域是否超出边界
     * @param deltaPointer 需要是需要改变的x和y
     * @return 水平方向是否超出边界 to 垂直方向 : true表示超出边界  false表示没超出边界
     */
    private fun checkDragBorder(
        borderRectF: RectF,
        imageRectF: RectF,
        deltaPointer: PointerBean
    ): Pair<Boolean, Boolean> {
        val toLeft = imageRectF.left + deltaPointer.x
        val toRight = imageRectF.right + deltaPointer.x
        val toTop = imageRectF.top + deltaPointer.y
        val toBottom = imageRectF.bottom + deltaPointer.y
        val isHorizonBeyond = toLeft < borderRectF.left && toRight > borderRectF.right
        val isVerticalBeyond = toTop < borderRectF.top && toBottom > borderRectF.bottom
        return isHorizonBeyond to isVerticalBeyond
    }

    /**
     * 判断经过缩放后图片所在的区域是否超出边界，因为放大不会超出裁剪框，这里仅考虑缩小。
     * @param deltaScale 缩放尺寸
     * @return 缩放尺寸
     */
    private fun checkScaleBorder(
        borderRectF: RectF,
        imageRectF: RectF,
        matrix: Matrix,
        deltaScale: Float
    ): Float {
        // 假设缩放的矩形
        val toRectF = RectF()
        toRectF.set(imageRectF)
        // 临时缩放的matrix
        val tempMatrix = Matrix()
        tempMatrix.set(matrix)
        tempMatrix.preScale(deltaScale, deltaScale)
        tempMatrix.mapRect(toRectF)
        val toLeft = toRectF.left
        val toRight = toRectF.right
        val toTop = toRectF.top
        val toBottom = toRectF.bottom
        val horizonScale = (toRight - toLeft) > (borderRectF.right - borderRectF.left)
        val verticalScale = (toBottom - toTop) / (borderRectF.bottom - borderRectF.top)
//        val scale = if (isHorizonBeyond && isVerticalBeyond) {
//            // 都超过了，不缩放
//            1f
//        } else if (isHorizonBeyond) {
//            // 仅水平超过了，缩放
//            (imageRectF.right - imageRectF.left)
//        } else if (isVerticalBeyond) {
//            // 仅垂直超过了，缩放
//            1f
//        } else {
//            // 都没超过，维持原状
//            deltaScale
//        }
//        return scale
        return 1f
    }

    private fun onActionMove(event: MotionEvent) {
        when (mPointerMode) {
            // 这里是一个手指的情况，处理移动
            PointerMode.DRAG_POINTER -> {
                calculateDeltaDistance(event.x, event.y)
                val borderRect = mClipView.getClipRect()
                val imageRect = mImageView.getImageViewRect()
                val isBeyondXY = checkDragBorder(borderRect, imageRect, mDeltaPointer)
                val isHorizonBeyond = isBeyondXY.first
                val isVerticalBeyond = isBeyondXY.second
                // 在指定的范围内滑动，如果超过就不允许滑动
                val deltaX = if (isHorizonBeyond) {
                    mDeltaPointer.x
                } else {
                    0f
                }
                val deltaY = if (isVerticalBeyond) {
                    mDeltaPointer.y
                } else {
                    0f
                }
                mMatrix.preTranslate(deltaX, deltaY)
                mFirstLastDownPointer.setData(event.x, event.y)
            }
            // 这里是两个手指的情况，处理缩放
            PointerMode.SCALE_POINTER -> {
                val fromScale = getScale()
                val deltaScale = calculateScale(event)
                val toScale = fromScale * deltaScale
                val scale = if (toScale < mMinScale || toScale > mMaxScale) {
                    1f
                } else {
                    deltaScale
                }
                val borderRect = mClipView.getClipRect()
                val imageRect = mImageView.getImageViewRect()
                if (scale < 1f){
                    // 放大直接放大就可以,缩小需要判断边界
                    checkScaleBorder(borderRect,imageRect,mMatrix,scale)
                }

                mMatrix.preScale(scale, scale, mMidPointer.x, mMidPointer.y)
            }

            PointerMode.NONE_POINTER -> {
                // 不处理
                return
            }
        }
        Log.d("lx", "手指操作模式=${mPointerMode}  mMatrix=${mMatrix}")
        mImageView.imageMatrix = mMatrix
    }

    /**
     * 手指起来时候将一些资源恢复成原状
     */
    private fun onActionUp() {
        mPointerMode = PointerMode.DRAG_POINTER
        mTempMatrix.set(mMatrix)
        mFirstLastDownPointer.setData(-1f, -1f)
        mSecondLastDownPointer.setData(-1f, -1f)
        mMidPointer.setData(-1f, -1f)
        mTwoPointerDistance = -1f
        mDeltaPointer.setData(-1f, -1f)
    }

    /**
     * 负责设置区域
     */
    fun setClipRect(clipRect: Rect) {
        val left = clipRect.left
        val top = (mImageView.height - clipRect.height()) / 2
        val right = clipRect.right
        val bottom = (mImageView.height + clipRect.height()) / 2
        mClipView.setClipRect(Rect(left,top,right,bottom))
    }

    /**
     * 计算两根手指中间的位置
     */
    private fun calculateTwoPointerMidLoc(
        midPointerBean: PointerBean,
        firstPointer: PointerBean,
        secondPointer: PointerBean
    ) {
        val midX = (firstPointer.x + secondPointer.x) / 2
        val midY = (firstPointer.y + secondPointer.y) / 2
        midPointerBean.setData(x = midX, y = midY)
    }

    /**
     * 计算两根手指的距离
     */
    private fun calculateTwoPointerDistance(
        firstPointer: PointerBean,
        secondPointer: PointerBean
    ): Float {
        return calculateTwoPointerDistance(
            firstPointer.x,
            firstPointer.y,
            secondPointer.x,
            secondPointer.y
        )
    }

    /**
     * 计算两点之间的距离
     */
    private fun calculateTwoPointerDistance(
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float
    ): Float {
        val deltaX = firstX - secondX
        val deltaY = firstY - secondY
        return sqrt(abs(deltaX * deltaX - deltaY * deltaY))
    }

    /**
     * 计算需要移动的距离
     */
    private fun calculateDeltaDistance(x: Float, y: Float) {
        val deltaX = x - mFirstLastDownPointer.x
        val deltaY = y - mFirstLastDownPointer.y
        mDeltaPointer.setData(deltaX, deltaY)
    }

    /**
     * 计算两根手指操作后需要缩放的倍数
     */
    private fun calculateScale(event: MotionEvent): Float {
        val firstX = event.getX(0)
        val firstY = event.getY(0)
        val secondX = event.getX(1)
        val secondY = event.getY(1)
        val newTwoPointerDistance = calculateTwoPointerDistance(firstX, firstY, secondX, secondY)
        return newTwoPointerDistance / mTwoPointerDistance
    }

    fun getClipView() : ClipView{
        return mClipView
    }
}