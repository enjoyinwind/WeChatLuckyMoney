package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
import static android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;

public class WeixinProcessor {
    public static final String PackageName = "com.tencent.mm";
    private boolean mutex = false;

    public void process(AccessibilityService service, AccessibilityEvent event){
        if(event.getEventType() == TYPE_NOTIFICATION_STATE_CHANGED){
            watchNotification(event);
        } else {
            if(!watchChat(event)){
                openHongbao(service);
            }
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

    /**
     * 是否已处理红包事件
     * @param event
     * @return
     */
    private boolean watchChat(AccessibilityEvent event){
        AccessibilityNodeInfo rootNodeInfo = event.getSource();
        if(null == rootNodeInfo){
            return false;
        }

        //字样控件id：com.tencent.mm:id/aqo
        List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByText("微信红包");
        if(list.size() > 0){
            AccessibilityNodeInfo lastHongbaoTextViewNode = list.get(list.size() - 1);
            if(!isAlreadyOpened(lastHongbaoTextViewNode)){
                //红包未拆开
                AccessibilityNodeInfo clickableNode = getClickableParentNode(lastHongbaoTextViewNode);
                if(clickableNode != null){
                    clickableNode.performAction(ACTION_CLICK);

                    return true;
                }
            } else {
                //红包已拆开
                return true;
            }
        }

        rootNodeInfo.recycle();

        return false;
    }

    /**
     * 红包窗口打开情况下，点击"打开"或返回
     * @param service
     * @return 是否打开红包
     */
    private boolean openHongbao(AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = service.getRootInActiveWindow();

        //com.tencent.mm:id/d05
        List<AccessibilityNodeInfo> nodeInfoList = rootNodeInfo.findAccessibilityNodeInfosByText("手慢了，红包派完了");
        if(!nodeInfoList.isEmpty()){
            //红包已被领完，返回
            service.performGlobalAction(GLOBAL_ACTION_BACK);

            return true;
        } else {
            AccessibilityNodeInfo node = findOpenButton(rootNodeInfo);
            if(node != null){
                node.performAction(ACTION_CLICK);
                return true;
            }
        }

        return false;
    }


    /**
     * 聊天窗口，判断红包是否已领取或被领完
     * @param node
     * @return
     */
    private boolean isAlreadyOpened(AccessibilityNodeInfo node){
        AccessibilityNodeInfo parentNode = node.getParent();
        if(parentNode != null && parentNode.getChildCount() > 0){
            for(int i = 0; i < parentNode.getChildCount(); i++){
                AccessibilityNodeInfo childNode = parentNode.getChild(i);
                if("已被领完".equals(childNode.getText()) || "已领取".equals(childNode.getText())){
                    return true;
                }
            }
        }

        return false;
    }

    private AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node) {
        if (node == null)
            return null;

        //非layout元素
        if (node.getChildCount() == 0) {
            if ("android.widget.Button".equals(node.getClassName()))
                return node;
            else
                return null;
        }

        //layout元素，遍历找button
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null)
                return button;
        }
        return null;
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
