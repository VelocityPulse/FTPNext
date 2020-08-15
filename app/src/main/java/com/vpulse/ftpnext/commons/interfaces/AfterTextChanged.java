package com.vpulse.ftpnext.commons.interfaces;

import android.text.Editable;
import android.text.TextWatcher;

public abstract class AfterTextChanged implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    public abstract void afterTextChanged(Editable s);

}
