package com.vpulse.ftpnext.commons.interfaces

import android.view.animation.Animation

abstract class OnEndAnimation : Animation.AnimationListener {
    override fun onAnimationStart(animation: Animation) {}
    abstract override fun onAnimationEnd(animation: Animation)
    override fun onAnimationRepeat(animation: Animation) {}
}