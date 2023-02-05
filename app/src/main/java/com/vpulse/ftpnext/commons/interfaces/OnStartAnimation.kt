package com.vpulse.ftpnext.commons.interfaces

import android.view.animation.Animation

abstract class OnStartAnimation : Animation.AnimationListener {
    abstract override fun onAnimationStart(animation: Animation)
    override fun onAnimationEnd(animation: Animation) {}
    override fun onAnimationRepeat(animation: Animation) {}
}