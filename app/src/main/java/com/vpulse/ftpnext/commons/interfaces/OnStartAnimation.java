package com.vpulse.ftpnext.commons.interfaces;

import android.view.animation.Animation;

public abstract class OnStartAnimation implements Animation.AnimationListener {

    public abstract void onAnimationStart(Animation animation);

    @Override
    public void onAnimationEnd(Animation animation) {

    }

    @Override
    public void onAnimationRepeat(Animation animation) {

    }
}
