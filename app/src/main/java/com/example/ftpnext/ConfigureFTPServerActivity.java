package com.example.ftpnext;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.transition.Slide;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;

public class ConfigureFTPServerActivity extends AppCompatActivity {

    private View mRootView;

    @Override
    public void onCreate(Bundle iSavedInstanceState) {
        super.onCreate(iSavedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);

        Slide lSlide = new Slide();
        lSlide.setDuration(getResources().getInteger(R.integer.form_animation_time));
        lSlide.setInterpolator(new DecelerateInterpolator(5F));
        lSlide.setSlideEdge(Gravity.BOTTOM);
        getWindow().setEnterTransition(lSlide);

        setContentView(R.layout.activity_configure_ftp_server);

        mRootView = findViewById(R.id.activity_configure_ftp_server_scrollview);
        mRootView.requestFocus();

    }

    @Override
    public void finish() {
        super.finish();
    }
}
