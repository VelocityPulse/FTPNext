package com.vpulse.ftpnext.commons.interfaces;

import android.view.animation.Animation;

public abstract class OnEndAnimation implements Animation.AnimationListener {

    @Override
    public void onAnimationStart(Animation animation) {

    }

    @Override
    public abstract void onAnimationEnd(Animation animation);

    @Override
    public void onAnimationRepeat(Animation animation) {

    }
}
