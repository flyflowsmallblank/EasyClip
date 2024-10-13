package com.handsome.easyclip.wight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
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
import kotlin.math.pow
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

    // 当前固定的比例，宽高比例
    private var mAspectRadio = 1f

    // 存储matrix的9个值
    private var mMatrixValues = FloatArray(9)

    // 最小scale的值
    private var mMinScale = 0.5f

    // 最大scale的值
    private var mMaxScale = 2f

    // 缩放程度，比如缩放16倍，缩放程度为1/4，就会开四次方，缩放2倍，防止用户缩放变化过快
    private var mScaleLevel = 1f / 4f

    // 超出后位移程度，给用户一个反馈，比如移动100m，缩放程度为2/3f。最终体现上是66m
    private var mTranslateLevel = 1f / 2f

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
            mTempMatrix.reset()
            mImageView.imageMatrix = mMatrix
        }

        val path = ClipViewHelper.getRealFilePathFromUri(context, uri)
        val bitmap = ClipViewHelper.decodeSampledBitmap(path, 720, 1280)

        val clipRect = mClipView.getClipRect()
        // 如果rect为null就
        if (clipRect.isEmpty){
            Log.e("lx", "需要先调用setClipRect设置裁剪区域")
            return
        }

        // 根据比例设置minScale和maxScale
        val minScale = setBorderScale(bitmap)
        mMinScale = minScale
        mTempMatrix.preScale(minScale,minScale)

        // 平移,将缩放后的图片平移到imageview的中心
        //imageView的中心x
        val imageViewMidX= mImageView.width.toFloat() / 2
        //imageView的中心y
        val imageViewMidY= mImageView.height.toFloat() / 2
        //bitmap的中心x
        val bitmapMidX = (bitmap.width * minScale / 2)
        //bitmap的中心y
        val bitmapMidY = (bitmap.height * minScale / 2)
        val deltaX = imageViewMidX - bitmapMidX
        val deltaY = imageViewMidY - bitmapMidY
        mTempMatrix.postTranslate(deltaX,deltaY)
        mImageView.imageMatrix = mTempMatrix
        mImageView.setImageBitmap(bitmap)
    }

    /**
     * 根据[mAspectRadio]设置最小比例
     * 假如mAspectRadio9:16。图片宽高比例为9:15，高相对较短，以高为准。
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
                if (mTwoPointerDistance < 40f) {
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
                checkImageLoc(true)
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
        val isHorizonBeyond = toLeft > borderRectF.left || toRight < borderRectF.right
        val isVerticalBeyond = toTop > borderRectF.top || toBottom < borderRectF.bottom
        return isHorizonBeyond to isVerticalBeyond
    }

    /**
     * 矫正位置
     */
    private fun checkImageLoc(isNeedAction : Boolean){
        val borderRect = mClipView.getClipRect()
        val imageRect = mImageView.getImageViewRect()
        checkImageLoc(borderRect, imageRect, mMatrix)
        if (isNeedAction){
            mImageView.imageMatrix = mMatrix
        }
    }

    /**
     * 矫正位置，拖拽或者缩放可能导致图片移出，需要手动矫正
     */
    private fun checkImageLoc(
        borderRectF: RectF,
        imageRectF: RectF,
        imageMatrix: Matrix
    ){
        val deltaXY = calculateToBorderDistance(borderRectF, imageRectF)
        val deltaX = deltaXY.first
        val deltaY = deltaXY.second
        imageMatrix.postTranslate(deltaX, deltaY)
    }

    /**
     * 根据borderRect和imageRect计算出到达border需要的距离
     * @return 不超出边框ImageView需要移动的距离
     */
    private fun calculateToBorderDistance(
        borderRectF: RectF,
        imageRectF: RectF
    ): Pair<Float, Float> {
        var deltaX = 0f
        var deltaY = 0f
        val deltaLeft = borderRectF.left - imageRectF.left
        val deltaRight = borderRectF.right - imageRectF.right
        val deltaTop = borderRectF.top - imageRectF.top
        val deltaBottom = borderRectF.bottom - imageRectF.bottom
        // 水平方向
        if (deltaLeft < 0 && deltaRight > 0){
            //todo 两边都超过了，需要缩放到指定位置，优先级不高
            Log.e("lx", "水平方向溢出")
        }else if (deltaLeft < 0 && deltaRight < 0){
            // 仅仅左边超过了
            deltaX = deltaLeft
        }else if (deltaLeft > 0 && deltaRight > 0){
            // 仅仅右边超过了
            deltaX = deltaRight
        }else{
            // 两边都没超过
            deltaX = 0f
        }

        // 垂直方向
        if (deltaTop < 0 && deltaBottom > 0){
            // 两边都超过了，需要缩放到指定位置
            Log.e("lx", "竖直方向溢出")
        }else if (deltaTop < 0 && deltaBottom < 0){
            // 仅仅顶部超过了
            deltaY = deltaTop
        }else if (deltaTop > 0 && deltaBottom > 0){
            // 仅仅底部超过了
            deltaY = deltaBottom
        }else{
            // 两边都没超过
            deltaY = 0f
        }
        return deltaX to deltaY
    }

    /**
     * 手指移动中拖拽的处理
     */
    private fun onDragMove(event: MotionEvent) {
        val deltaPointerBean = calculateDeltaDistance(event.x, event.y)
        val borderRectF = mClipView.getClipRect()
        val imageRectF = mImageView.getImageViewRect()
        val isExceedXY = checkDragBorder(borderRectF, imageRectF, deltaPointerBean)
        if (isExceedXY.first){
            deltaPointerBean.x *= mTranslateLevel
        }
        if (isExceedXY.second){
            deltaPointerBean.y *= mTranslateLevel
        }
        mMatrix.postTranslate(deltaPointerBean.x, deltaPointerBean.y)
        // 更新第一个手指的位置
        mFirstLastDownPointer.setData(event.x, event.y)
    }

    /**
     * 手指移动中缩放的处理
     */
    private fun onScaleMove(event: MotionEvent) {
        val fromScale = getScale()
        val deltaScale = calculateScale(event)
        val toScale = fromScale * deltaScale
        // 这里是为了防止多次放大才能到边界。
        // 保证缩放到最大和最小的时候就是最大和最小而不是1f固定不变。
        var scale = if (toScale < mMinScale) {
            mMinScale / fromScale
        }else if (toScale > mMaxScale){
            mMaxScale / fromScale
        } else {
            deltaScale
        }

        // 这个是设置缩放的倍数，优化用户体验的
        scale = scale.pow(mScaleLevel)
        mMatrix.preScale(scale, scale, mMidPointer.x, mMidPointer.y)
    }

    /**
     * 处理手指移动
     */
    private fun onActionMove(event: MotionEvent) {
        when (mPointerMode) {
            // 这里是一个手指的情况，处理移动
            PointerMode.DRAG_POINTER -> {
                onDragMove(event)
            }
            // 这里是两个手指的情况，处理缩放
            PointerMode.SCALE_POINTER -> {
                onScaleMove(event)
            }

            // 不处理
            PointerMode.NONE_POINTER -> {
                return
            }
        }
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
    }

    /**
     * 负责设置区域
     */
    fun setClipRect(clipRect: RectF) {
        val left = clipRect.left
        val top = (mImageView.height.toFloat() - clipRect.height()) / 2
        val right = clipRect.right
        val bottom = (mImageView.height.toFloat() + clipRect.height()) / 2
        mClipView.setClipRect(RectF(left,top,right,bottom))
        Log.d("lx", "setClipRect:${RectF(left,top,right,bottom)} ")
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
    private fun calculateDeltaDistance(x: Float, y: Float) : PointerBean{
        val deltaX = x - mFirstLastDownPointer.x
        val deltaY = y - mFirstLastDownPointer.y
        return PointerBean(deltaX,deltaY)
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