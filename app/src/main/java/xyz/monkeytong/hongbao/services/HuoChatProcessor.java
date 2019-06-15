package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
import static android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;

public class HuoChatProcessor {
    public static final String PackageName = "com.huochat.im";
    private boolean mutex = false;
    private boolean showDialog = false;
    private int pageCode;
    //是否已执行过（主要是为了只执行一次）
    private boolean isAlreadyExecutedOnce = false;

    public void process(AccessibilityEvent event, AccessibilityService service){
        if(event.getEventType() == TYPE_NOTIFICATION_STATE_CHANGED){
            watchNotification(event);
        } else {
            String currentActivityName = event.getClassName().toString();
            if(PageCode.HomeActivityName.equals(currentActivityName)){
                pageCode = PageCode.HomeActivity;
            } else if(PageCode.ChatActivityName.equals(currentActivityName)){
                pageCode = PageCode.ChatActivity;
                isAlreadyExecutedOnce = false;
            } else if(PageCode.ReceivePacketDetailActivityName.equals(currentActivityName)){
                pageCode = PageCode.ReceivePacketDetailActivity;
                isAlreadyExecutedOnce = false;
            } else if(PageCode.OpenPacketActivityName.equals(currentActivityName)){
                pageCode = PageCode.OpenPacketActivity;
                isAlreadyExecutedOnce = false;
            }

            switch (pageCode){
                case PageCode.HomeActivity:
                    watchChatList(event, service);
                    break;
                case PageCode.ChatActivity:
                    findPacket(event, service);
                    break;
                case PageCode.ReceivePacketDetailActivity:
                    if(!isAlreadyExecutedOnce){
                        service.performGlobalAction(GLOBAL_ACTION_BACK);
                        isAlreadyExecutedOnce = true;
                    }
                    break;
                case PageCode.OpenPacketActivity:
                    if(!isAlreadyExecutedOnce){
                        openPacket(event);
                        isAlreadyExecutedOnce = true;
                    }
                    break;
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

    private void watchChatList(AccessibilityEvent event, AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = event.getSource();
        if(null == rootNodeInfo){
            return;
        }

//        if(event.getClassName().equals("com.huochat.im.activity.HomeActivity")){
//            mutex = false;
//        }

        if(mutex){
            return;
        }

        List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.huochat.im:id/tv_chat_msg");
        if (list.size() > 0) {
            for (AccessibilityNodeInfo item : list) {
                if (item.getText().toString().startsWith("[红包]")) {
                    AccessibilityNodeInfo clickableNode = getClickableParentNode(item);

                    if (clickableNode != null) {
//                        clickableNode.performAction(ACTION_CLICK);
                        mutex = true;
                        click(clickableNode, service);

                        break;
                    }
                }
            }
        }

//      List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByText("[红包]");
//        if(list.size() > 0){
//            AccessibilityNodeInfo clickableNode = getClickableParentNode(list.get(0));
////            long interval = event.getEventTime() - lastClickTime;
//            if(clickableNode != null){
//                clickableNode.performAction(ACTION_CLICK);
//            }
//        }

//        rootNodeInfo.recycle();
    }

    private void click(AccessibilityNodeInfo clickableNode, AccessibilityService service) {
        if (android.os.Build.VERSION.SDK_INT <= 23) {
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            if (android.os.Build.VERSION.SDK_INT > 23) {
                Rect rect = new Rect();
                clickableNode.getBoundsInScreen(rect);
                int centerX= (rect.left + rect.right) / 2;
                int centerY = (rect.top + rect.bottom) / 2;

                Path path = new Path();
                path.moveTo(centerX, centerY);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                GestureDescription gestureDescription = builder.addStroke(new GestureDescription.StrokeDescription(path, 450, 50)).build();
                service.dispatchGesture(gestureDescription, new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
//                        Log.d(TAG, "onCompleted");
                        mutex = false;
                        super.onCompleted(gestureDescription);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
//                        Log.d(TAG, "onCancelled");
                        mutex = false;
                        super.onCancelled(gestureDescription);
                    }
                }, null);

            }
        }
    }

    private void findPacket(AccessibilityEvent event, AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = event.getSource();
        if(null == rootNodeInfo){
            return;
        }

        if(isAlreadyExecutedOnce){
            return;
        }

        boolean hasPacket = false;

        List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.huochat.im:id/ll_chat_list_item");
        int listSize = list.size();
        if (listSize > 0) {
            int i = listSize - 1;
            while (i > -1){
                AccessibilityNodeInfo current = list.get(i);

                List<AccessibilityNodeInfo> hintNodeList = current.findAccessibilityNodeInfosByViewId("com.huochat.im:id/tv_hint");
                if(hintNodeList != null && hintNodeList.size() > 0){
                    i = i - 2;
//                    continue;
                    break;
                }

                List<AccessibilityNodeInfo> packetTipsNodeList = current.findAccessibilityNodeInfosByViewId("com.huochat.im:id/tv_status");
                if(packetTipsNodeList != null && packetTipsNodeList.size() > 0){
                    //红包
                    isAlreadyExecutedOnce = true;
                    click(packetTipsNodeList.get(0), service);
                    hasPacket = true;
                    break;
                }

                --i;
            }
        }

        //没有红包直接返回，有红包不会返回
        if(!hasPacket && !isAlreadyExecutedOnce){
            service.performGlobalAction(GLOBAL_ACTION_BACK);
            isAlreadyExecutedOnce = true;
        }
    }

    private void openPacket(AccessibilityEvent event){
        AccessibilityNodeInfo rootNodeInfo = event.getSource();
        if(null == rootNodeInfo){
            return;
        }

        //找到开红包按钮，并点击；
        List<AccessibilityNodeInfo> nodeInfoList = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.huochat.im:id/iv_open");
        if(nodeInfoList != null && nodeInfoList.size() > 0){
            nodeInfoList.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        rootNodeInfo.recycle();
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
