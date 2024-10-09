package com.handsome.easyclip.wight.extension

fun Float.withinCertainRange(min : Float,max : Float) : Float{
    if (this < min){
        return min
    }else if (this > max){
        return max
    }
    return this
}

fun Float.isInCertainRange(min: Float,max: Float) : Boolean{
    return this in min..max
}