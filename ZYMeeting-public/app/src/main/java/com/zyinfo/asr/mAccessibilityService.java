package com.zyinfo.asr;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class mAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        boolean isedit = event.getClassName().equals("android.widget.EditText");
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
              ) {
            AccessibilityNodeInfo nodeInfo = event.getSource();
            if (nodeInfo != null) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "你的文本");
                nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        return super.onKeyEvent(event);
    }
}