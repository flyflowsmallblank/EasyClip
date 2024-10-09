package com.handsome.easyclip.wight.bean

data class PointerBean(
    var x : Float,
    var y : Float
){
    fun setData(x: Float,y: Float){
        this.x = x
        this.y = y
    }
}


enum class PointerMode{
    DRAG_POINTER,  // 拖动
    SCALE_POINTER,    // 放大缩小
    NONE_POINTER // 没有操作
}
