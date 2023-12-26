package io.agora.jkzy_demo.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.tencent.mmkv.MMKV;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import io.agora.jkzy_demo.config.JKZYConstants;
import io.agora.jkzy_demo.utils.CommonUtil;
import io.agora.jkzy_demo.utils.TokenUtils;
import io.agora.jkzy_demo.App;
import io.agora.jkzy_demo.R;
import io.agora.jkzy_demo.widget.VideoReportLayout;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.ScreenCaptureParameters;
import io.agora.rtc2.proxy.LocalAccessPointConfiguration;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

/**
 * This demo demonstrates how to make a one-to-one video call
 */
public class JoinChannelVideo extends BaseFragment implements View.OnClickListener {
    private static final String TAG = JoinChannelVideo.class.getSimpleName();

    private VideoReportLayout fl_local, fl_remote, fl_remote_2, fl_remote_3;
    private Button join, switch_camera, enableScreen, muteVideo, muteAudio;
    private EditText et_channel;
    private RtcEngine engine;
    private int myUid;
    private boolean joined = false;
    private Map<Integer, ViewGroup> remoteViews = new ConcurrentHashMap<Integer, ViewGroup>();

    ChannelMediaOptions option;

    private MMKV mmkv;
    // 是否打开私有化服务器指向
    private boolean isAccessPoint;
    // h264/h265开关
    private boolean m265Switch;
    // 软/硬编开关
    private boolean mHwEncoder;
    // 打开状态信息开关
    private boolean addState;
    // 分辨率
    private String dimensions;
    // 帧率
    private String fps;
    // 分辨率/帧率控制策略
    // 0 画质优先，弱网下调整帧率 不调整分辨率
    // 1 帧率优先，弱网下调整分辨率 不调整帧率
    // 2 平衡模式，弱网下先调整帧率、再调整分辨率；
    private String controlStrategy;

    private boolean isScreenSharing = false;
    private boolean isAudioMuted = false;
    private boolean isVideoMuted = false;
    private boolean isFullOfLocal;
    private boolean isFullOfRemote1;
    private boolean isFullOfRemote2;
    private boolean isFullOfRemote3;

    private final ScreenCaptureParameters screenCaptureParameters = new ScreenCaptureParameters();
    private static final int DEFAULT_SHARE_FRAME_RATE = 15;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_joinchannel_video, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        join = view.findViewById(R.id.btn_join);
        switch_camera = view.findViewById(R.id.btn_switch_camera);
        et_channel = view.findViewById(R.id.et_channel);
        view.findViewById(R.id.btn_join).setOnClickListener(this);
        switch_camera.setOnClickListener(this);
        fl_local = view.findViewById(R.id.fl_local);
        fl_remote = view.findViewById(R.id.fl_remote);
        fl_remote_2 = view.findViewById(R.id.fl_remote2);
        fl_remote_3 = view.findViewById(R.id.fl_remote3);

        enableScreen = view.findViewById(R.id.btn_enable_screen_sharing);
        enableScreen.setOnClickListener(this);
        muteVideo = view.findViewById(R.id.btn_mute_local_video);
        muteVideo.setOnClickListener(this);
        muteAudio = view.findViewById(R.id.btn_mute_local_audio);
        muteAudio.setOnClickListener(this);

        Button btnSwitchOrientation = view.findViewById(R.id.btn_orientation);
        btnSwitchOrientation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int curOri = getActivity().getRequestedOrientation();
                if (curOri == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            }
        });

        fl_local.setOnClickListener(v -> {
            if (!isFullOfLocal) {
                setLargeWindow(fl_local);
                fl_local.setVisibility(View.VISIBLE);
                fl_remote.setVisibility(View.GONE);
                fl_remote_2.setVisibility(View.GONE);
                fl_remote_3.setVisibility(View.GONE);
                isFullOfLocal = true;
            } else {
                setSmallWindow(fl_local);
                visibleAll();
                isFullOfLocal = false;
            }
        });
        fl_remote.setOnClickListener(v -> {
            if (!isFullOfRemote1) {
                setLargeWindow(fl_remote);
                fl_local.setVisibility(View.GONE);
                fl_remote.setVisibility(View.VISIBLE);
                fl_remote_2.setVisibility(View.GONE);
                fl_remote_3.setVisibility(View.GONE);
                isFullOfRemote1 = true;
            } else {
                setSmallWindow(fl_remote);
                visibleAll();
                isFullOfRemote1 = false;
            }
        });
        fl_remote_2.setOnClickListener(v -> {
            if (!isFullOfRemote2) {
                setLargeWindow(fl_remote_2);
                fl_local.setVisibility(View.GONE);
                fl_remote.setVisibility(View.GONE);
                fl_remote_2.setVisibility(View.VISIBLE);
                fl_remote_3.setVisibility(View.GONE);
                isFullOfRemote2 = true;
            } else {
                setSmallWindow(fl_remote_2);
                visibleAll();
                isFullOfRemote2 = false;
            }
        });
        fl_remote_3.setOnClickListener(v -> {
            if (!isFullOfRemote3) {
                setLargeWindow(fl_remote_3);
                fl_local.setVisibility(View.GONE);
                fl_remote.setVisibility(View.GONE);
                fl_remote_2.setVisibility(View.GONE);
                fl_remote_3.setVisibility(View.VISIBLE);
                isFullOfRemote3 = true;
            } else {
                setSmallWindow(fl_remote_3);
                visibleAll();
                isFullOfRemote3 = false;
            }
        });
        initMMKVValue();
    }

    // 初始化之前在mmkv里面设置的值
    private void initMMKVValue() {
        MMKV.initialize(getContext());
        mmkv = MMKV.defaultMMKV();

        isAccessPoint = mmkv.decodeBool(JKZYConstants.KEY_ACCESS_POINT, true);
        m265Switch = mmkv.decodeBool(JKZYConstants.KEY_265_SWITCH, true);
        mHwEncoder = mmkv.decodeBool(JKZYConstants.KEY_HW_ENCODER, false);
        addState = mmkv.decodeBool(JKZYConstants.KEY_ADD_STATE, false);
        fps = mmkv.decodeString(JKZYConstants.KEY_FPS, "VD_640x480");
        dimensions = mmkv.decodeString(JKZYConstants.KEY_DIMENSIONS, "FRAME_RATE_FPS_15");
        controlStrategy = mmkv.decodeString(JKZYConstants.KEY_CONTROL_STRATEGY, "1 帧率优先");

        Log.d("SSSSS JD-DEMO",
                "JoinChannelVideo:::isAccessPoint=" + isAccessPoint + " , m265Switch=" + m265Switch
                        + " , mHwEncoder=" + mHwEncoder + " , dimensions=" + dimensions
                        + " , fps=" + fps + " , controlStrategy=" + controlStrategy);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            /**
             * The context of Android Activity
             */
            config.mContext = context.getApplicationContext();
            /**
             * The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id"> How to get the App ID</a>
             */
            config.mAppId = "aab8b8f5a8cd4469a63042fcfafe7063";
            /** Sets the channel profile of the Agora RtcEngine.
             CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
             Use this profile in one-on-one calls or group calls, where all users can talk freely.
             CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
             channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
             an audience can only receive streams.*/
            config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
            /**
             * IRtcEngineEventHandler is an abstract class providing default implementation.
             * The SDK uses this class to report to the app on SDK runtime events.
             */
            config.mEventHandler = iRtcEngineEventHandler;
            config.mAudioScenario = Constants.AudioScenario.getValue(Constants.AudioScenario.DEFAULT);
            config.mAreaCode = RtcEngineConfig.AreaCode.AREA_CODE_CN;
            engine = RtcEngine.create(config);
            /**
             * This parameter is for reporting the usages of APIExample to agora background.
             * Generally, it is not necessary for you to set this parameter.
             */
            engine.setParameters("{"
                    + "\"rtc.report_app_scenario\":"
                    + "{"
                    + "\"appScenario\":" + 100 + ","
                    + "\"serviceType\":" + 11 + ","
                    + "\"appVersion\":\"" + RtcEngine.getSdkVersion() + "\""
                    + "}"
                    + "}");
        } catch (Exception e) {
            e.printStackTrace();
            getActivity().onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /**leaveChannel and Destroy the RtcEngine instance*/
        if (engine != null) {
            engine.leaveChannel();
        }
        handler.post(RtcEngine::destroy);
        engine = null;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_join) {
            if (!joined) {
                CommonUtil.hideInputBoard(getActivity(), et_channel);
                // call when join button hit
                String channelId = et_channel.getText().toString();
                // Check permission
                List<String> permissionList = new ArrayList<>();
                permissionList.add(Permission.READ_EXTERNAL_STORAGE);
                permissionList.add(Permission.WRITE_EXTERNAL_STORAGE);
                permissionList.add(Permission.RECORD_AUDIO);
                permissionList.add(Permission.CAMERA);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
                }

                String[] permissionArray = new String[permissionList.size()];
                permissionList.toArray(permissionArray);

                if (AndPermission.hasPermissions(this, permissionArray)) {
                    joinChannel(channelId);
                    return;
                }
                // Request permission
                AndPermission.with(this).runtime().permission(
                        permissionArray
                ).onGranted(permissions ->
                {
                    // Permissions Granted
                    joinChannel(channelId);
                }).start();
            } else {
                joined = false;
                engine.leaveChannel();
                join.setText("加入");
                for (ViewGroup value : remoteViews.values()) {
                    value.removeAllViews();
                }
                remoteViews.clear();
            }
        } else if (v.getId() == switch_camera.getId()) {
            if (engine != null && joined) {
                engine.switchCamera();
            }
        } else if (v.getId() == R.id.btn_enable_screen_sharing) {
            if (engine != null && joined) {
                if (isScreenSharing) {
                    option.publishScreenCaptureVideo = false;
                    option.publishCameraTrack = true;
                    engine.updateChannelMediaOptions(option);

                    enableScreen.setText("开启屏幕共享");
                    stopScreenSharePreview();


                    SurfaceView surfaceView = new SurfaceView(getContext());
                    if (fl_local.getChildCount() > 0) {
                        fl_local.removeAllViews();
                    }
                    fl_local.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Setup local video to render your local camera preview
                    engine.setupLocalVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, 0));
                    engine.startPreview();
                } else {
                    engine.stopPreview();


                    DisplayMetrics metrics = new DisplayMetrics();
                    getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                    screenCaptureParameters.captureVideo = true;
                    screenCaptureParameters.videoCaptureParameters.width = 720;
                    screenCaptureParameters.videoCaptureParameters.height = (int) (720 * 1.0f / metrics.widthPixels * metrics.heightPixels);
                    screenCaptureParameters.videoCaptureParameters.framerate = DEFAULT_SHARE_FRAME_RATE;
                    screenCaptureParameters.captureAudio = false;
                    screenCaptureParameters.audioCaptureParameters.captureSignalVolume = 100;
                    engine.startScreenCapture(screenCaptureParameters);

                    option.publishScreenCaptureVideo = true;
                    option.publishCameraTrack = false;
                    engine.updateChannelMediaOptions(option);

                    startScreenSharePreview();
                    enableScreen.setText("关闭屏幕共享");

                }
                isScreenSharing = !isScreenSharing;
            }
        } else if (v.getId() == R.id.btn_mute_local_video) {
            if (engine != null && joined) {
                if (isVideoMuted) {
                    muteVideo.setText("摄像头 Mute");
                    engine.muteLocalVideoStream(false);
                    engine.startPreview();
                } else {
                    muteVideo.setText("摄像头 Unmute");
                    engine.muteLocalVideoStream(true);
                    engine.stopPreview();
                }
                isVideoMuted = !isVideoMuted;
            }
        } else if (v.getId() == R.id.btn_mute_local_audio) {
            if (engine != null && joined) {
                if (isAudioMuted) {
                    muteAudio.setText("麦克风 Mute");
                    engine.muteLocalAudioStream(false);
                } else {
                    muteAudio.setText("麦克风 Unmute");
                    engine.muteLocalAudioStream(true);
                }
                isAudioMuted = !isAudioMuted;
            }
        }
    }
//
//    @Override
//    public void onPause() {
//        super.onPause();
//        Intent serviceIntent = new Intent(getActivity(), MyForegroundService.class);
//        ContextCompat.startForegroundService(getActivity(), serviceIntent);
//    }

    private void joinChannel(String channelId) {
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }

        // Create render view by RtcEngine
        SurfaceView surfaceView = new SurfaceView(context);
        if (fl_local.getChildCount() > 0) {
            fl_local.removeAllViews();
        }
        // Add to the local container
        fl_local.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Setup local video to render your local camera preview
        engine.setupLocalVideo(new VideoCanvas(surfaceView, Constants.RENDER_MODE_HIDDEN, 0));
        // Set audio route to microPhone
        engine.setDefaultAudioRoutetoSpeakerphone(true);

        /**In the demo, the default is to enter as the anchor.*/
        engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
        // Enable video module
        engine.enableVideo();
        // Setup video encoding configs
        engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                ((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingDimensionObject(),
                VideoEncoderConfiguration.FRAME_RATE.valueOf(((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingFrameRate()),
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
        ));

        option = new ChannelMediaOptions();
        option.autoSubscribeAudio = true;
        option.autoSubscribeVideo = true;
        option.publishMicrophoneTrack = true;
        option.publishCameraTrack = true;
        option.publishScreenCaptureVideo = false;
        option.publishScreenCaptureAudio = false;

        if (null != engine) {
            engine.setParameters("{\"rtc.enableMultipath\":true}");

            // JD-加入频道前设置私有参数
            if (isAccessPoint) {
                engine.setParameters("{\"rtc.local_domain\":\"ap.1226191.agora.local\"}");
                LocalAccessPointConfiguration config=new LocalAccessPointConfiguration();
                ArrayList<String> iplist =new ArrayList<>();
                iplist.add("20.1.124.137");
                config.ipList=iplist;
                config.mode=1;
                config.verifyDomainName ="ap.1226191.agora.local";
                engine.setLocalAccessPoint(config);
                Log.d("SSSSS", "enable cus");
            } else {
                engine.setLocalAccessPoint(new LocalAccessPointConfiguration());
                Log.d("SSSSS", "disable cus");

            }

            if (m265Switch) {
                // 打开h265
                engine.setParameters("{\"che.video.videoCodecIndex\":2}");
            } else {
                // 打开h264
                engine.setParameters("{\"che.video.videoCodecIndex\":1}");
            }

            if (mHwEncoder) {
                // 硬编
                engine.setParameters("{\"engine.video.enable_hw_encoder\":true}");
            } else {
                // 软编
                engine.setParameters("{\"engine.video.enable_hw_encoder\":false}");
            }

//            if ("0 画质优先".equals(controlStrategy)) {
//                engine.setParameters("{\"rtc.video.degradation_preference\":0}");
//            } else if ("1 帧率优先".equals(controlStrategy)) {
//                engine.setParameters("{\"rtc.video.degradation_preference\":1}");
//            } else {
            engine.setParameters("{\"rtc.video.degradation_preference\":2}");
//            }

            engine.setParameters("{\"che.video.vqc_auto_resize_type \":1}");

            engine.setParameters("{\"che.audio.custom_bitrate\":24000}");
            engine.setParameters("{\"che.audio.pad_fec.bitrate\":12000}");
            engine.setParameters("{\"che.audio.pad_fec.offset\":5}");
            engine.setParameters("{\"che.audio.pad_fec.num\":1}");

            engine.setParameters("{\"rtc.enable_nasa2\":true}");
            engine.setParameters("{\"rtc.network.e2e_cc_mode\":3}");

            engine.setParameters("{\"rtc.log_filter\":65535}");
            engine.setParameters("{\"rtc.log_size\":9999999}");
        }


        /**Please configure accessToken in the string_config file.
         * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
         *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
         * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
         *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/

        int uid = new Random().nextInt(1000) + 100000;
        TokenUtils.gen(requireContext(), channelId, uid, ret -> {

            /** Allows a user to join a channel.
             if you do not specify the uid, we will generate the uid for you*/
            int res = engine.joinChannel(ret, channelId, uid, option);
            if (res != 0) {
                // Usually happens with invalid parameters
                // Error code description can be found at:
                // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                showAlert(RtcEngine.getErrorDescription(Math.abs(res)));
                return;
            }
            // Prevent repeated entry
            join.setEnabled(false);
        });

    }

    /**
     * IRtcEngineEventHandler is an abstract class providing default implementation.
     * The SDK uses this class to report to the app on SDK runtime events.
     */
    private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler() {
        /**
         * Error code description can be found at:
         * en: https://api-ref.agora.io/en/video-sdk/android/4.x/API/class_irtcengineeventhandler.html#callback_irtcengineeventhandler_onerror
         * cn: https://docs.agora.io/cn/video-call-4.x/API%20Reference/java_ng/API/class_irtcengineeventhandler.html#callback_irtcengineeventhandler_onerror
         */
        @Override
        public void onError(int err) {
            super.onError(err);
            showLongToast("Error code:" + err + ", msg:" + RtcEngine.getErrorDescription(err));
            if (err == Constants.ERR_INVALID_TOKEN || err == Constants.ERR_TOKEN_EXPIRED) {
                engine.leaveChannel();
                runOnUIThread(() -> join.setEnabled(true));

                if (Constants.ERR_INVALID_TOKEN == err) {
                    showAlert("The token is invalid.");
                }
                if (Constants.ERR_TOKEN_EXPIRED == err) {
                    showAlert("The token is expired");
                }
            }
        }

        /**Occurs when a user leaves the channel.
         * @param stats With this callback, the application retrieves the channel information,
         *              such as the call duration and statistics.*/
        @Override
        public void onLeaveChannel(RtcStats stats) {
            super.onLeaveChannel(stats);
            Log.i(TAG, String.format("local user %d leaveChannel!", myUid));
            showLongToast(String.format("local user %d leaveChannel!", myUid));
        }

        /**Occurs when the local user joins a specified channel.
         * The channel name assignment is based on channelName specified in the joinChannel method.
         * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
         * @param channel Channel name
         * @param uid User ID
         * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered*/
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            showLongToast(String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            myUid = uid;
            joined = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    join.setEnabled(true);
                    join.setText("离开");
                    fl_local.setReportUid(uid);
                }
            });
        }

        /**Since v2.9.0.
         * This callback indicates the state change of the remote audio stream.
         * PS: This callback does not work properly when the number of users (in the Communication profile) or
         *     broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         * @param uid ID of the user whose audio state changes.
         * @param state State of the remote audio
         *   REMOTE_AUDIO_STATE_STOPPED(0): The remote audio is in the default state, probably due
         *              to REMOTE_AUDIO_REASON_LOCAL_MUTED(3), REMOTE_AUDIO_REASON_REMOTE_MUTED(5),
         *              or REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7).
         *   REMOTE_AUDIO_STATE_STARTING(1): The first remote audio packet is received.
         *   REMOTE_AUDIO_STATE_DECODING(2): The remote audio stream is decoded and plays normally,
         *              probably due to REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2),
         *              REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4) or REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6).
         *   REMOTE_AUDIO_STATE_FROZEN(3): The remote audio is frozen, probably due to
         *              REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1).
         *   REMOTE_AUDIO_STATE_FAILED(4): The remote audio fails to start, probably due to
         *              REMOTE_AUDIO_REASON_INTERNAL(0).
         * @param reason The reason of the remote audio state change.
         *   REMOTE_AUDIO_REASON_INTERNAL(0): Internal reasons.
         *   REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1): Network congestion.
         *   REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2): Network recovery.
         *   REMOTE_AUDIO_REASON_LOCAL_MUTED(3): The local user stops receiving the remote audio
         *               stream or disables the audio module.
         *   REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote audio
         *              stream or enables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_MUTED(5): The remote user stops sending the audio stream or
         *               disables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the audio stream
         *              or enables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method
         *                  until the SDK triggers this callback.*/
        @Override
        public void onRemoteAudioStateChanged(int uid, int state, int reason, int elapsed) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed);
            Log.i(TAG, "onRemoteAudioStateChanged->" + uid + ", state->" + state + ", reason->" + reason);
        }

        /**Since v2.9.0.
         * Occurs when the remote video state changes.
         * PS: This callback does not work properly when the number of users (in the Communication
         *     profile) or broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         * @param uid ID of the remote user whose video state changes.
         * @param state State of the remote video:
         *   REMOTE_VIDEO_STATE_STOPPED(0): The remote video is in the default state, probably due
         *              to REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3), REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5),
         *              or REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7).
         *   REMOTE_VIDEO_STATE_STARTING(1): The first remote video packet is received.
         *   REMOTE_VIDEO_STATE_DECODING(2): The remote video stream is decoded and plays normally,
         *              probably due to REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY (2),
         *              REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4), REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6),
         *              or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9).
         *   REMOTE_VIDEO_STATE_FROZEN(3): The remote video is frozen, probably due to
         *              REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1) or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8).
         *   REMOTE_VIDEO_STATE_FAILED(4): The remote video fails to start, probably due to
         *              REMOTE_VIDEO_STATE_REASON_INTERNAL(0).
         * @param reason The reason of the remote video state change:
         *   REMOTE_VIDEO_STATE_REASON_INTERNAL(0): Internal reasons.
         *   REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1): Network congestion.
         *   REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY(2): Network recovery.
         *   REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3): The local user stops receiving the remote
         *               video stream or disables the video module.
         *   REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote
         *               video stream or enables the video module.
         *   REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5): The remote user stops sending the video
         *               stream or disables the video module.
         *   REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the video
         *               stream or enables the video module.
         *   REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         *   REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8): The remote media stream falls back to the
         *               audio-only stream due to poor network conditions.
         *   REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9): The remote media stream switches
         *               back to the video stream after the network conditions improve.
         * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method until
         *               the SDK triggers this callback.*/
        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed);
            Log.i(TAG, "onRemoteVideoStateChanged->" + uid + ", state->" + state + ", reason->" + reason);
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            super.onConnectionStateChanged(state, reason);
            Log.i(TAG, "onConnectionStateChanged->" + state + ", reason->" + reason);
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         * @param uid ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.*/
        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);
            showLongToast(String.format("user %d joined!", uid));
            /**Check if the context is correct*/
            Context context = getContext();
            if (context == null) {
                return;
            }
            if (remoteViews.containsKey(uid)) {
                return;
            } else {
                handler.post(() ->
                {
                    /**Display remote video stream*/
                    SurfaceView surfaceView = null;
                    // Create render view by RtcEngine
                    surfaceView = new SurfaceView(context);
                    surfaceView.setZOrderMediaOverlay(true);
                    VideoReportLayout view = getAvailableView();
                    view.setReportUid(uid);
                    remoteViews.put(uid, view);
                    // Add to the remote container
                    view.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Setup remote video to render
                    engine.setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid));
                });
            }
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
         * @param uid ID of the user whose audio state changes.
         * @param reason Reason why the user goes offline:
         *   USER_OFFLINE_QUIT(0): The user left the current channel.
         *   USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
         *              packet was received within a certain period of time. If a user quits the
         *               call and the message is not passed to the SDK (due to an unreliable channel),
         *               the SDK assumes the user dropped offline.
         *   USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
         *               the host to the audience.*/
        @Override
        public void onUserOffline(int uid, int reason) {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
            showLongToast(String.format("user %d offline! reason:%d", uid, reason));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    /**Clear render view
                     Note: The video will stay at its last frame, to completely remove it you will need to
                     remove the SurfaceView from its parent*/
                    engine.setupRemoteVideo(new VideoCanvas(null, Constants.RENDER_MODE_HIDDEN, uid));
                    remoteViews.get(uid).removeAllViews();
                    remoteViews.remove(uid);
                }
            });
        }

        @Override
        public void onLocalAudioStats(LocalAudioStats stats) {
            super.onLocalAudioStats(stats);
            if (addState)
                fl_local.setLocalAudioStats(stats);
        }

        @Override
        public void onRemoteAudioStats(RemoteAudioStats stats) {
            super.onRemoteAudioStats(stats);
            if (addState) {

                fl_remote.setRemoteAudioStats(stats);
                fl_remote_2.setRemoteAudioStats(stats);
                fl_remote_3.setRemoteAudioStats(stats);
            }

        }

        @Override
        public void onLocalVideoStats(Constants.VideoSourceType source, LocalVideoStats stats) {
            super.onLocalVideoStats(source, stats);
            if (addState)
                fl_local.setLocalVideoStats(stats);
        }

        @Override
        public void onRemoteVideoStats(RemoteVideoStats stats) {
            super.onRemoteVideoStats(stats);
            if (addState) {
                fl_remote.setRemoteVideoStats(stats);
                fl_remote_2.setRemoteVideoStats(stats);
                fl_remote_3.setRemoteVideoStats(stats);
            }

        }
    };

    private VideoReportLayout getAvailableView() {
        if (fl_remote.getChildCount() == 0) {
            return fl_remote;
        } else if (fl_remote_2.getChildCount() == 0) {
            return fl_remote_2;
        } else if (fl_remote_3.getChildCount() == 0) {
            return fl_remote_3;
        } else {
            return fl_remote;
        }
    }


    private void visibleAll() {
        fl_local.setVisibility(View.VISIBLE);
        fl_remote.setVisibility(View.VISIBLE);
        fl_remote_2.setVisibility(View.VISIBLE);
        fl_remote_3.setVisibility(View.VISIBLE);
    }


    private void setLargeWindow(ViewGroup viewGroup) {
        // 设置LinearLayout的布局参数为大窗口样式
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) viewGroup.getLayoutParams();
        layoutParams.matchConstraintPercentWidth = 1.0f;
        layoutParams.matchConstraintPercentHeight = 1.0f;
        viewGroup.setLayoutParams(layoutParams);
    }

    private void setSmallWindow(ViewGroup viewGroup) {
        // 设置LinearLayout的布局参数为小窗口样式
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) viewGroup.getLayoutParams();
        layoutParams.matchConstraintPercentWidth = 0.5f;
        layoutParams.matchConstraintPercentHeight = 0.5f;
        viewGroup.setLayoutParams(layoutParams);
    }


    private void startScreenSharePreview() {
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }

        // Create render view by RtcEngine
        SurfaceView surfaceView = new SurfaceView(context);
        if (fl_local.getChildCount() > 0) {
            fl_local.removeAllViews();
        }
        // Add to the local container
        fl_local.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Setup local video to render your local camera preview
        VideoCanvas local = new VideoCanvas(surfaceView, Constants.RENDER_MODE_FIT, 0);
        local.mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED;
        local.sourceType = Constants.VIDEO_SOURCE_SCREEN_PRIMARY;
        engine.setupLocalVideo(local);

        engine.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);
    }

    private void stopScreenSharePreview() {
        fl_local.removeAllViews();
        engine.setupLocalVideo(new VideoCanvas(null));
        engine.stopPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);
    }

    public void hideInputBoard(Activity activity, EditText editText)
    {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
    }
}
