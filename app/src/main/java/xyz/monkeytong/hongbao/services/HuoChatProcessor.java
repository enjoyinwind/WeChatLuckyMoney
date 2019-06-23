package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

import static android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
import static android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;

public class HuoChatProcessor {
    private static final String TAG = HuoChatProcessor.class.getSimpleName();
    public static final String PackageName = "com.huochat.im";
    private boolean mutex = false;
    //表示当前在什么页面
    private int pageCode;
    //是否已执行过（主要是为了只执行一次）
    private boolean isAlreadyExecutedOnce = false;
    //是否出现过红包已抢完情况
    private boolean isPacketOver = false;

    public void process(final AccessibilityEvent event, final AccessibilityService service){
        if(event.getEventType() == TYPE_NOTIFICATION_STATE_CHANGED){
            watchNotification(event);
        } else {
//            Log.d(TAG, "process: " + event.getEventType());
            if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED){
                if(pageCode == PageCode.ChatActivity){
                    isAlreadyExecutedOnce = false;
                }
            } else if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
                String currentActivityName = event.getClassName().toString();
                if(PageCode.HomeActivityName.equals(currentActivityName)){
                    pageCode = PageCode.HomeActivity;
                } else if(PageCode.ChatActivityName.equals(currentActivityName)){
                    if(pageCode != PageCode.ChatActivity){
                        pageCode = PageCode.ChatActivity;
                        isAlreadyExecutedOnce = false;
                    }
                } else if(PageCode.ReceivePacketDetailActivityName.equals(currentActivityName)){
                    if(pageCode != PageCode.ReceivePacketDetailActivity){
                        pageCode = PageCode.ReceivePacketDetailActivity;
                        isAlreadyExecutedOnce = false;
                    }
                } else if(PageCode.OpenPacketActivityName.equals(currentActivityName)){
                    pageCode = PageCode.OpenPacketActivity;
                }
            } else {
                switch (pageCode){
                    case PageCode.HomeActivity:
                        watchChatList(event, service);
                        break;
                    case PageCode.ChatActivity:
                        findPacket(event, service);
                        break;
                    case PageCode.ReceivePacketDetailActivity:
                        if(!isAlreadyExecutedOnce){
                            isAlreadyExecutedOnce = true;
                            new android.os.Handler().postDelayed(
                                    new Runnable() {
                                        public void run() {
                                            service.performGlobalAction(GLOBAL_ACTION_BACK);
                                        }
                                    },
                                    800);
                        }
                        break;
                    case PageCode.OpenPacketActivity:
                        break;
                }

                //上面页面识别问题，在意识到是开红包对话框后，再去点击"开"，可能无法获取到对应view进行点击
                //因此开红包动作在每个页面都会执行
                openPacket(service);

                answer(service);
//                deletePacket(service);
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

    private void findPacket(AccessibilityEvent event, final AccessibilityService service){
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

                //领取红包后，聊天窗口里会有一条记录显示"你领取了某某人的红包"
                List<AccessibilityNodeInfo> hintNodeList = current.findAccessibilityNodeInfosByViewId("com.huochat.im:id/tv_hint");
                if(hintNodeList != null && hintNodeList.size() > 0){
                    AccessibilityNodeInfo hintNode = hintNodeList.get(0);
                    String hintTxt = hintNode.getText() != null ? hintNode.getText().toString() : "";
                    if(hintTxt.startsWith("你领取") && hintTxt.endsWith("的红包")){
                        i = i - 2;
//                    continue;
                        break;
                    }
                }

                //判断是否有红包
                List<AccessibilityNodeInfo> packetTipsNodeList = current.findAccessibilityNodeInfosByViewId("com.huochat.im:id/tv_status");
                if(packetTipsNodeList != null && packetTipsNodeList.size() > 0){
                    AccessibilityNodeInfo packetNode = packetTipsNodeList.get(0);
                    if(packetNode.getText() != null && packetNode.getText().toString().contains("领取红包")){
                        if(isPacketOver){
                            //红包已抢完情况下，先退出聊天窗口
                            break;
//                            //如果红包出现已抢完情况，再次遍历时把它删掉
//                            AccessibilityNodeInfo temp = getNodeByViewId(current, "com.huochat.im:id/chat_content");
//                            if(temp != null){
//                                temp.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
//
//                                new android.os.Handler().postDelayed(
//                                        new Runnable() {
//                                            public void run() {
//                                                deletePacket(service);
//                                            }
//                                        },
//                                        2000);
//                            }
                        } else {
                            //找到红包后，进行点击
                            isAlreadyExecutedOnce = true;
                            click(packetNode, service);
                            hasPacket = true;
                            break;
                        }
                    }
                }

                --i;
            }

            //没有红包直接返回，有红包不会返回
            if(!hasPacket && !isAlreadyExecutedOnce){
                service.performGlobalAction(GLOBAL_ACTION_BACK);
                isAlreadyExecutedOnce = true;
                isPacketOver = false;
            }
        }
    }

    private void openPacket(final AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = service.getRootInActiveWindow();
        if(null == rootNodeInfo){
            return;
        }

        //红包已抢完
        List<AccessibilityNodeInfo> nodeInfoList = rootNodeInfo.findAccessibilityNodeInfosByText("手慢了，红包抢完了");
        if(nodeInfoList != null && nodeInfoList.size() > 0){
            boolean result = clickByViewId(rootNodeInfo, "com.huochat.im:id/iv_close");

            //关闭了开红包界面，说明红包已抢完，设置下标志位
            if(result && !isPacketOver){
                isPacketOver = true;
            }
        } else {
            //找到开红包按钮，并点击；
            clickByViewId(rootNodeInfo, "com.huochat.im:id/iv_open");
        }

        rootNodeInfo.recycle();
    }

    private boolean clickByViewId(AccessibilityNodeInfo nodeInfo, String viewId){
        List<AccessibilityNodeInfo> nodeInfoList = nodeInfo.findAccessibilityNodeInfosByViewId(viewId);
        if(nodeInfoList != null && nodeInfoList.size() > 0){
            Log.d(TAG, "clickByViewId: " + viewId + "  " + nodeInfoList.size());
            AccessibilityNodeInfo clickableNode = nodeInfoList.get(0);
            if(clickableNode.isClickable()){
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        } else {
            Log.d(TAG, "clickByViewId: " + viewId + "  null");
        }

        return false;
    }

    private void answer(final AccessibilityService service){
        beginAnswer(service);

        answering(service);
    }

    /**
     * 开始答题
     * @param service
     */
    private void beginAnswer(final AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = service.getRootInActiveWindow();
        if(null == rootNodeInfo){
            return;
        }

        clickByViewId(rootNodeInfo, "com.huochat.im:id/tv_receive_btn");

        rootNodeInfo.recycle();
    }

    /**
     * 选择答案并提交
     * @param service
     */
    private void answering(final AccessibilityService service){
        AccessibilityNodeInfo rootNodeInfo = service.getRootInActiveWindow();
        if(null == rootNodeInfo){
            return;
        }

        clickByViewId(rootNodeInfo, "com.huochat.im:id/ll_a");

        clickByViewId(rootNodeInfo, "com.huochat.im:id/tv_conmit_btn");

        clickByViewId(rootNodeInfo, "com.huochat.im:id/tv_ok_btn");

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

    private AccessibilityNodeInfo getNodeByViewId(AccessibilityNodeInfo node, String viewId){
        List<AccessibilityNodeInfo> nodeInfoList = node.findAccessibilityNodeInfosByViewId(viewId);
        if(nodeInfoList != null && nodeInfoList.size() > 0){
            return nodeInfoList.get(0);
        } else {
            return null;
        }
    }

    /**
     * 删除已抢完的红包
     * @param service
     */
    private void deletePacket(final AccessibilityService service){
        if(service.getWindows() != null){
            Log.d(TAG, "deletePacket: service.getWindows().size()=" + service.getWindows().size());
            List<AccessibilityWindowInfo> windowInfoList = service.getWindows();
            for(int i = 0; i < windowInfoList.size(); i++){
                if(windowInfoList.get(i) != null){
                    test(i, windowInfoList.get(i).getRoot());
                }
            }
        }

        AccessibilityNodeInfo rootNodeInfo = service.getRootInActiveWindow();
        if(null == rootNodeInfo){
            return;
        }

        List<AccessibilityNodeInfo> list = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.huochat.im:id/ll_menu_item");
        if(list != null && list.size() > 0){
            AccessibilityNodeInfo node = list.get(0);
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            isPacketOver = false;
            isAlreadyExecutedOnce = false;
        }
    }

    private void test(int index, AccessibilityNodeInfo node){
        if(node != null){
            List<AccessibilityNodeInfo> nodeInfoList = node.findAccessibilityNodeInfosByViewId("com.huochat.im:id/ll_menu_item");
            if(nodeInfoList != null && nodeInfoList.size() > 0){
                Log.d(TAG, "test: ll_menu_item=" + nodeInfoList.size() + "  index=" + index);
            }
        }
    }
}
