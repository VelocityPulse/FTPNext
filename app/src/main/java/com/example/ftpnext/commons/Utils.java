package com.example.ftpnext.commons;

import android.app.Activity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class Utils {

    public static void hideKeyboard(Activity iActivity) {
        InputMethodManager lImm = (InputMethodManager) iActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View lView = iActivity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (lView == null) {
            lView = new View(iActivity);
        }
        lImm.hideSoftInputFromWindow(lView.getWindowToken(), 0);
    }

    public static void hideKeyboard(Activity iActivity, View iView) {
        InputMethodManager lImm = (InputMethodManager) iActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        if (iView == null)
            iView = new View(iActivity);
        lImm.hideSoftInputFromWindow(iView.getWindowToken(), 0);
    }
}
