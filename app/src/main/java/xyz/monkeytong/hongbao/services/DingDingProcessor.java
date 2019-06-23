package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import static android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;

public class DingDingProcessor {
    public static final String PackageName = "com.alibaba.android.rimet";
    private boolean mutex = false;
    private boolean showDialog = false;
    private long lastTimeMills;

    public void process(AccessibilityEvent event, AccessibilityService service){
        if(event.getEventType() == TYPE_NOTIFICATION_STATE_CHANGED){
            watchNotification(event);
        } else {
            reply(event, service);
        }
    }

    private boolean watchNotification(AccessibilityEvent event){
        // Not a notification
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
            return false;

        // Not a hongbao
        String tip = event.getText().toString();
        if (!tip.contains("微信红包")) return true;

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void watchChat(AccessibilityEvent event){
        AccessibilityNodeInfo rootNodeInfo = event.getSource();
        if(null == rootNodeInfo){
            return;
        }

        List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByText("查看红包");
        if(list.size() > 0 && !mutex){
            mutex = true;

            AccessibilityNodeInfo clickableNode = getClickableParentNode(list.get(list.size() - 1));
            if(clickableNode != null){
                clickableNode.performAction(ACTION_CLICK);
            }



            mutex = false;
        }

        rootNodeInfo.recycle();
    }

    private void reply(AccessibilityEvent event, AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = service.getRootInActiveWindow();
        if(null == rootNodeInfo){
            return;
        }

        long currentTimeMills = System.currentTimeMillis();
        if(currentTimeMills - lastTimeMills > 3000){
            List<AccessibilityNodeInfo> etList = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.alibaba.android.rimet:id/et_sendmessage");
            if(etList.size() > 0){
                AccessibilityNodeInfo nodeInfo = etList.get(0);
                if(nodeInfo != null){
                    setText(nodeInfo);
                }
            }

            List<AccessibilityNodeInfo> btnList = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.alibaba.android.rimet:id/btn_send");
            if(btnList.size() > 0){
                AccessibilityNodeInfo nodeInfo = btnList.get(0);
                nodeInfo.performAction(ACTION_CLICK);
            }

            lastTimeMills = currentTimeMills;
        }


        rootNodeInfo.recycle();
    }

    private void setText(AccessibilityNodeInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "谢谢");
            info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }
    }

    private void unpack(){
//        GestureDescription.Builder builder = new GestureDescription.Builder();
//        GestureDescription gestureDescription = builder.addStroke(new GestureDescription.StrokeDescription(path, 450, 50)).build();
//        dispatchGesture(gestureDescription, new AccessibilityService.GestureResultCallback() {
//            @Override
//            public void onCompleted(GestureDescription gestureDescription) {
//                Log.d(TAG, "onCompleted");
//                mMutex = false;
//                super.onCompleted(gestureDescription);
//            }
//
//            @Override
//            public void onCancelled(GestureDescription gestureDescription) {
//                Log.d(TAG, "onCancelled");
//                mMutex = false;
//                super.onCancelled(gestureDescription);
//            }
//        }, null);
    }

    private AccessibilityNodeInfo getClickableParentNode(AccessibilityNodeInfo node){
        if(null == node){
            return null;
        }

        if(node.isClickable()){
            return node;
        } else {
            return getClickableParentNode(node.getParent());
        }
    }
}
