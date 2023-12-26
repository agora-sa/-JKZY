package io.agora.jkzy_demo.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tencent.mmkv.MMKV;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import io.agora.jkzy_demo.App;
import io.agora.jkzy_demo.R;
import io.agora.jkzy_demo.config.JKZYConstants;
import io.agora.jkzy_demo.utils.CommonUtil;
import io.agora.jkzy_demo.utils.TokenUtils;
import io.agora.jkzy_demo.widget.VideoReportLayout;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.ClientRoleOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.proxy.LocalAccessPointConfiguration;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;
import io.agora.rtc2.video.WatermarkOptions;

/**
 * This demo demonstrates how to make a one-to-one video call
 * <p>
 * By default, Everyone is a host, entered a channel will see yourself in the background( the big one ).
 * click the frame will switch the position.
 * When turn the Co-host on, others will see you.
 */
public class LiveStreaming extends BaseFragment implements View.OnClickListener {
    private static final String TAG = LiveStreaming.class.getSimpleName();

    private BottomSheetDialog mSettingDialog;
    private View mRootView;
    private View customView;
    private View trackView;

    private VideoReportLayout foreGroundVideo, backGroundVideo;
    private AppCompatButton btnSetting;
    private AppCompatButton btnJoin;
    private AppCompatButton btnPreload;
    private AppCompatButton btnPublish;
    private AppCompatButton btnRemoteScreenshot;
    private AppCompatButton btnSwitchCamera;
    private AppCompatEditText etChannel;
    private boolean isLocalVideoForeground;

    private SwitchCompat switchWatermark;
    private SwitchCompat switchBFrame;
    private SwitchCompat switchLowLatency;
    private SwitchCompat switchLowStream;
    private SwitchCompat switchFirstFrame;

    private Spinner spEncoderType;
    private Spinner spRenderMode;
    private SeekBar sbColor;
    private View vColor;

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

    private RtcEngine engine;
    private int myUid = 0;
    private String myToken;
    private int remoteUid;
    private boolean joined = false;
    private boolean isHost = false;
    private boolean isPreloaded = false;
    private int canvasBgColor = 0x0000ffff; // RGBA
    private int canvasRenderMode = Constants.RENDER_MODE_HIDDEN;
    private final VideoEncoderConfiguration videoEncoderConfiguration = new VideoEncoderConfiguration();

    private String joinType;

    private boolean isB;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_live_streaming, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        foreGroundVideo = mRootView.findViewById(R.id.foreground_video);
        backGroundVideo = mRootView.findViewById(R.id.background_video);
        btnSetting = mRootView.findViewById(R.id.btn_setting);
        btnJoin = mRootView.findViewById(R.id.btn_join);
        btnPreload = mRootView.findViewById(R.id.btn_preload);
        btnPublish = mRootView.findViewById(R.id.btn_publish);
        btnRemoteScreenshot = mRootView.findViewById(R.id.btn_remote_screenshot);
        btnSwitchCamera = mRootView.findViewById(R.id.btn_switch_camera);
        etChannel = mRootView.findViewById(R.id.et_channel);

        btnSetting.setOnClickListener(this);
        btnJoin.setOnClickListener(this);
        btnPreload.setOnClickListener(this);
        btnPublish.setOnClickListener(this);
        btnRemoteScreenshot.setOnClickListener(this);
        btnSwitchCamera.setOnClickListener(this);
        foreGroundVideo.setOnClickListener(this);

        customView = LayoutInflater.from(getContext()).inflate(R.layout.fragment_live_streaming_setting, null);
        switchWatermark = customView.findViewById(R.id.switch_watermark);
        switchBFrame = customView.findViewById(R.id.switch_b_frame);
        switchLowLatency = customView.findViewById(R.id.switch_low_latency);
        switchLowStream = customView.findViewById(R.id.switch_low_stream);
        switchFirstFrame = customView.findViewById(R.id.switch_first_frame);
        spEncoderType = customView.findViewById(R.id.sp_encoder_type);
        spRenderMode = customView.findViewById(R.id.sp_render_mode);
        sbColor = customView.findViewById(R.id.sb_color);
        vColor = customView.findViewById(R.id.v_color);

        switchWatermark.setOnCheckedChangeListener((buttonView, isChecked) -> enableWatermark(isChecked));
        switchBFrame.setOnCheckedChangeListener((buttonView, isChecked) -> enableBFrame(isChecked));
        switchLowLatency.setOnCheckedChangeListener((buttonView, isChecked) -> enableLowLegacy(isChecked));
        switchLowStream.setOnCheckedChangeListener((buttonView, isChecked) -> enableLowStream(isChecked));
        switchFirstFrame.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.tip)
                        .setMessage(R.string.first_frame_optimization_tip)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> {
                            buttonView.setChecked(false);
                            dialog.dismiss();
                        })
                        .setPositiveButton(R.string.confirm, (dialog, which) -> {
                            // Enable FirstFrame Optimization
                            engine.enableInstantMediaRendering();
                            buttonView.setEnabled(false);
                            dialog.dismiss();
                        })
                         .show();
            }
        });
        spEncoderType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setEncodingPreference(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spRenderMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (getString(R.string.render_mode_hidden).equals(parent.getSelectedItem())) {
                    canvasRenderMode = Constants.RENDER_MODE_HIDDEN;
                }
                else if(getString(R.string.render_mode_fit).equals(parent.getSelectedItem())){
                    canvasRenderMode = Constants.RENDER_MODE_FIT;
                }
                else if(getString(R.string.render_mode_adaptive).equals(parent.getSelectedItem())){
                    canvasRenderMode = Constants.RENDER_MODE_ADAPTIVE;
                }
                updateVideoView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        sbColor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int color = Color.argb(255, progress, progress, 255 - progress);
                canvasBgColor = (Color.red(color) << 24) | (Color.green(color) << 16) | (Color.blue(color) << 8) | Color.alpha(color);
                vColor.setBackgroundColor(color);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateVideoView();
            }
        });
        mSettingDialog = new BottomSheetDialog(requireContext());
        mSettingDialog.setContentView(customView);

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
        fps = mmkv.decodeString(JKZYConstants.KEY_FPS, "FRAME_RATE_FPS_15");
        dimensions = mmkv.decodeString(JKZYConstants.KEY_DIMENSIONS, "VD_640x480");
        controlStrategy = mmkv.decodeString(JKZYConstants.KEY_CONTROL_STRATEGY, "1 帧率优先");

        Log.d("SSSSS JD-DEMO",
                "JoinChannelVideo:::isAccessPoint=" + isAccessPoint + " , m265Switch=" + m265Switch
                        + " , mHwEncoder=" + mHwEncoder + " , dimensions=" + dimensions
                        + " , fps=" + fps + " , controlStrategy=" + controlStrategy);
    }

    private void updateVideoView() {

        if(backGroundVideo.getChildCount() > 0 && backGroundVideo.getReportUid() != -1){
            int reportUid = backGroundVideo.getReportUid();
            SurfaceView videoView = new SurfaceView(requireContext());
            backGroundVideo.removeAllViews();
            backGroundVideo.addView(videoView);
            VideoCanvas local = new VideoCanvas(videoView, canvasRenderMode, reportUid);
//            local.backgroundColor = canvasBgColor;
            if(reportUid == myUid){
                engine.setupLocalVideo(local);
            }else{
                engine.setupRemoteVideo(local);
            }
        }
        if(foreGroundVideo.getChildCount() > 0 && foreGroundVideo.getReportUid() != -1){
            int reportUid = foreGroundVideo.getReportUid();
            SurfaceView videoView = new SurfaceView(requireContext());
            videoView.setZOrderMediaOverlay(true);
            foreGroundVideo.removeAllViews();
            foreGroundVideo.addView(videoView);
            VideoCanvas local = new VideoCanvas(videoView, canvasRenderMode, reportUid);
//            local.backgroundColor = canvasBgColor;
            if(reportUid == myUid){
                engine.setupLocalVideo(local);
            }else{
                engine.setupRemoteVideo(local);
            }
        }
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

            /*
             * Creates an RtcEngine instance.
             * @param context The context of Android Activity
             * @param appId The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id">
             *              How to get the App ID</a>
             * @param handler IRtcEngineEventHandler is an abstract class providing default implementation.
             *                The SDK uses this class to report to the app on SDK runtime events.*/
            RtcEngineConfig rtcEngineConfig = new RtcEngineConfig();
            rtcEngineConfig.mAppId = "aab8b8f5a8cd4469a63042fcfafe7063";
            rtcEngineConfig.mContext = context.getApplicationContext();
            rtcEngineConfig.mEventHandler = iRtcEngineEventHandler;
            /* Sets the channel profile of the Agora RtcEngine. */
            rtcEngineConfig.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
            rtcEngineConfig.mAudioScenario = Constants.AudioScenario.getValue(Constants.AudioScenario.DEFAULT);
            rtcEngineConfig.mAreaCode = ((App) getActivity().getApplication()).getGlobalSettings().getAreaCode();
            engine = RtcEngine.create(rtcEngineConfig);
            /*
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
            /* setting the local access point if the private cloud ip was set, otherwise the config will be invalid.*/
            engine.setLocalAccessPoint(((App) getActivity().getApplication()).getGlobalSettings().getPrivateCloudConfig());

            engine.setVideoEncoderConfiguration(videoEncoderConfiguration);
            engine.enableDualStreamMode(true);
        } catch (Exception e) {
            requireActivity().onBackPressed();
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /*leaveChannel and Destroy the RtcEngine instance*/
        if (engine != null) {
            engine.leaveChannel();
        }
        handler.post(RtcEngine::destroy);
        engine = null;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_join) {
            if (!joined) {
                CommonUtil.hideInputBoard(requireActivity(), etChannel);
                // call when join button hit
                String channelId = etChannel.getText().toString();
                // Check permission
                if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE, Permission.Group.CAMERA)) {
                    joinChannel(channelId);
                    return;
                }
                // Request permission
                AndPermission.with(this).runtime().permission(
                        Permission.Group.STORAGE,
                        Permission.Group.MICROPHONE,
                        Permission.Group.CAMERA
                ).onGranted(permissions ->
                {
                    // Permissions Granted
                    joinChannel(channelId);
                }).start();
            } else {
                joined = false;
                isHost = false;
                isPreloaded = false;
                btnJoin.setText("主播");
                btnPublish.setEnabled(false);
                btnPreload.setEnabled(true);
                etChannel.setEnabled(true);
                btnJoin.setEnabled(true);
                btnSetting.setEnabled(true);
                btnPublish.setText(getString(R.string.enable_publish));
                trackView.setVisibility(View.GONE);
                switchWatermark.setChecked(false);
                remoteUid = 0;
                foreGroundVideo.removeAllViews();
                backGroundVideo.removeAllViews();

                /**After joining a channel, the user must call the leaveChannel method to end the
                 * call before joining another channel. This method returns 0 if the user leaves the
                 * channel and releases all resources related to the call. This method call is
                 * asynchronous, and the user has not exited the channel when the method call returns.
                 * Once the user leaves the channel, the SDK triggers the onLeaveChannel callback.
                 * A successful leaveChannel method call triggers the following callbacks:
                 *      1:The local client: onLeaveChannel.
                 *      2:The remote client: onUserOffline, if the user leaving the channel is in the
                 *          Communication channel, or is a BROADCASTER in the Live Broadcast profile.
                 * @returns 0: Success.
                 *          < 0: Failure.
                 * PS:
                 *      1:If you call the destroy method immediately after calling the leaveChannel
                 *          method, the leaveChannel process interrupts, and the SDK does not trigger
                 *          the onLeaveChannel callback.
                 *      2:If you call the leaveChannel method during CDN live streaming, the SDK
                 *          triggers the removeInjectStreamUrl method.*/
                engine.stopPreview();
                engine.leaveChannel();

            }
        } else if (v.getId() == R.id.btn_publish) {
            isHost = !isHost;
            if (isHost) {
                engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
            } else {
                ClientRoleOptions clientRoleOptions = new ClientRoleOptions();
                clientRoleOptions.audienceLatencyLevel = switchLowLatency.isChecked() ? Constants.AUDIENCE_LATENCY_LEVEL_ULTRA_LOW_LATENCY : Constants.AUDIENCE_LATENCY_LEVEL_LOW_LATENCY;
                engine.setClientRole(Constants.CLIENT_ROLE_AUDIENCE, clientRoleOptions);
            }
            btnPublish.setEnabled(false);
            btnPublish.setText(isHost ? getString(R.string.disnable_publish) : getString(R.string.enable_publish));
        } else if (v.getId() == R.id.foreground_video) {
            switchView();
        } else if (v.getId() == R.id.btn_setting) {
//            mSettingDialog.show();

            if (!joined) {
                CommonUtil.hideInputBoard(requireActivity(), etChannel);
                // call when join button hit
                String channelId =  etChannel.getText().toString();
                // Check permission
                if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE, Permission.Group.CAMERA)) {
                    joinChannelWithAu(channelId);
                    return;
                }
                // Request permission
                AndPermission.with(this).runtime().permission(
                        Permission.Group.STORAGE,
                        Permission.Group.MICROPHONE,
                        Permission.Group.CAMERA
                ).onGranted(permissions ->
                {
                    // Permissions Granted
                    joinChannelWithAu(channelId);
                }).start();
            } else {
                joined = false;
                isHost = false;
                isPreloaded = false;
                btnSetting.setText("观众");
                btnJoin.setEnabled(true);
                btnPublish.setEnabled(false);
                btnPreload.setEnabled(true);
                etChannel.setEnabled(true);
                btnSetting.setEnabled(true);
                btnPublish.setText(getString(R.string.enable_publish));
                trackView.setVisibility(View.GONE);
                switchWatermark.setChecked(false);
                remoteUid = 0;
                foreGroundVideo.removeAllViews();
                backGroundVideo.removeAllViews();

                /**After joining a channel, the user must call the leaveChannel method to end the
                 * call before joining another channel. This method returns 0 if the user leaves the
                 * channel and releases all resources related to the call. This method call is
                 * asynchronous, and the user has not exited the channel when the method call returns.
                 * Once the user leaves the channel, the SDK triggers the onLeaveChannel callback.
                 * A successful leaveChannel method call triggers the following callbacks:
                 *      1:The local client: onLeaveChannel.
                 *      2:The remote client: onUserOffline, if the user leaving the channel is in the
                 *          Communication channel, or is a BROADCASTER in the Live Broadcast profile.
                 * @returns 0: Success.
                 *          < 0: Failure.
                 * PS:
                 *      1:If you call the destroy method immediately after calling the leaveChannel
                 *          method, the leaveChannel process interrupts, and the SDK does not trigger
                 *          the onLeaveChannel callback.
                 *      2:If you call the leaveChannel method during CDN live streaming, the SDK
                 *          triggers the removeInjectStreamUrl method.*/
                engine.stopPreview();
                engine.leaveChannel();

            }
        } else if (v.getId() == R.id.btn_remote_screenshot) {
            takeSnapshot(remoteUid);
        }else if (v.getId() == R.id.btn_switch_camera) {
            if (engine != null) {
                engine.switchCamera();
            }
        } else if (v.getId() == R.id.btn_preload) {
            String channelName = etChannel.getText().toString();
            if (TextUtils.isEmpty(channelName)) {
                Toast.makeText(getContext(), "The channel name is empty!", Toast.LENGTH_SHORT).show();
            } else {
                myUid = new Random().nextInt(1000) + 10000;
                TokenUtils.gen(getContext(), channelName, myUid, token ->
                {
                    myToken = token;
                    int ret = engine.preloadChannel(token, channelName, myUid);
                    if (ret == Constants.ERR_OK) {
                        isPreloaded = true;
                        btnPreload.setEnabled(false);
                        etChannel.setEnabled(false);
                        Toast.makeText(getContext(), "Preload success : uid=" + myUid, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void switchView() {
        isLocalVideoForeground = !isLocalVideoForeground;
        int foreGroundReportId = foreGroundVideo.getReportUid();
        foreGroundVideo.setReportUid(backGroundVideo.getReportUid());
        backGroundVideo.setReportUid(foreGroundReportId);

        if (foreGroundVideo.getChildCount() > 0) {
            foreGroundVideo.removeAllViews();
        }
        if (backGroundVideo.getChildCount() > 0) {
            backGroundVideo.removeAllViews();
        }
        // Create render view by RtcEngine
        SurfaceView localView = new SurfaceView(getContext());
        SurfaceView remoteView = new SurfaceView(getContext());
        if (isLocalVideoForeground) {
            // Add to the local container
            foreGroundVideo.addView(localView,0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Add to the remote container
            backGroundVideo.addView(remoteView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Setup remote video to render
            VideoCanvas remote = new VideoCanvas(remoteView, canvasRenderMode, remoteUid);
//                remote.backgroundColor = canvasBgColor;
            engine.setupRemoteVideo(remote);
            // Setup local video to render your local camera preview
            VideoCanvas local = new VideoCanvas(localView, canvasRenderMode, 0);
//                local.backgroundColor = canvasBgColor;
            engine.setupLocalVideo(local);
            localView.setZOrderMediaOverlay(true);
        } else {
            // Add to the local container
            foreGroundVideo.addView(remoteView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Add to the remote container
            backGroundVideo.addView(localView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Setup local video to render your local camera preview
            VideoCanvas local = new VideoCanvas(localView, canvasRenderMode, 0);
//                local.backgroundColor = canvasBgColor;
            engine.setupLocalVideo(local);
            // Setup remote video to render
            VideoCanvas remote = new VideoCanvas(remoteView, canvasRenderMode, remoteUid);
//                remote.backgroundColor = canvasBgColor;
            engine.setupRemoteVideo(remote);
            remoteView.setZOrderMediaOverlay(true);
        }
    }

    private void joinChannel(String channelId) {
        joinType = "broadcast";
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }

        isLocalVideoForeground = false;
        // Create render view by RtcEngine
        SurfaceView surfaceView = new SurfaceView(context);
        if (backGroundVideo.getChildCount() > 0) {
            backGroundVideo.removeAllViews();
        }
        // Add to the local container
        backGroundVideo.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Setup local video to render your local camera preview
        VideoCanvas local = new VideoCanvas(surfaceView, canvasRenderMode, 0);
//        local.backgroundColor = canvasBgColor;
        engine.setupLocalVideo(local);
        engine.setDefaultAudioRoutetoSpeakerphone(true);
        engine.startPreview();

        // Enable video module
        engine.enableVideo();
        // Setup video encoding configs
        engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                ((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingDimensionObject(),
                VideoEncoderConfiguration.FRAME_RATE.valueOf(((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingFrameRate()),
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.valueOf(((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingOrientation())
        ));

        engine.startMediaRenderingTracing();

        setParams();


        isB = true;
        /*
         * Please configure accessToken in the string_config file.
         * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
         *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
         * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
         *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/
        TokenUtils.gen(requireContext(), channelId, myUid, token -> {
            /* Allows a user to join a channel.
             if you do not specify the uid, we will generate the uid for you*/

            ChannelMediaOptions option = new ChannelMediaOptions();
            option.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
            option.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
            option.autoSubscribeAudio = true;
            option.autoSubscribeVideo = true;
            int res;
            if (isPreloaded) {
                res = engine.joinChannel(myToken, channelId, myUid, option);
            } else {
                res = engine.joinChannel(token, channelId, myUid, option);
            }
            if (res != 0) {
                engine.stopPreview();
                // Usually happens with invalid parameters
                // Error code description can be found at:
                // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                showAlert(RtcEngine.getErrorDescription(Math.abs(res)));
                return;
            }
            // Prevent repeated entry
//            mRootBinding.btnJoin.setEnabled(false);
//            mRootBinding.btnPreload.setEnabled(false);

        });

    }

    private void joinChannelWithAu(String channelId) {
        joinType = "au";
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }

        isLocalVideoForeground = false;
        // Create render view by RtcEngine
        SurfaceView surfaceView = new SurfaceView(context);
        if (backGroundVideo.getChildCount() > 0) {
            backGroundVideo.removeAllViews();
        }
        // Add to the local container
        backGroundVideo.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Setup local video to render your local camera preview
        VideoCanvas local = new VideoCanvas(surfaceView, canvasRenderMode, 0);
//        local.backgroundColor = canvasBgColor;
        engine.setupLocalVideo(local);
        engine.setDefaultAudioRoutetoSpeakerphone(true);
        engine.startPreview();

        // Enable video module
        engine.enableVideo();
        // Setup video encoding configs
        engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                ((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingDimensionObject(),
                VideoEncoderConfiguration.FRAME_RATE.valueOf(((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingFrameRate()),
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.valueOf(((App) getActivity().getApplication()).getGlobalSettings().getVideoEncodingOrientation())
        ));

        engine.startMediaRenderingTracing();

        setParams();


        isB = false;
        /*
         * Please configure accessToken in the string_config file.
         * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
         *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
         * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
         *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/
        TokenUtils.gen(requireContext(), channelId, myUid, token -> {
            /* Allows a user to join a channel.
             if you do not specify the uid, we will generate the uid for you*/

            ChannelMediaOptions option = new ChannelMediaOptions();
            option.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
            option.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE;
            option.autoSubscribeAudio = true;
            option.autoSubscribeVideo = true;
            int res;
            if (isPreloaded) {
                res = engine.joinChannel(myToken, channelId, myUid, option);
            } else {
                res = engine.joinChannel(token, channelId, myUid, option);
            }
            if (res != 0) {
                engine.stopPreview();
                // Usually happens with invalid parameters
                // Error code description can be found at:
                // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
                showAlert(RtcEngine.getErrorDescription(Math.abs(res)));
                return;
            }
            // Prevent repeated entry
        });
    }

    private void enableWatermark(boolean enable) {
        if (enable) {
            WatermarkOptions watermarkOptions = new WatermarkOptions();
            int size = ((App) requireActivity().getApplication()).getGlobalSettings().getVideoEncodingDimensionObject().width / 6;
            int height = ((App) requireActivity().getApplication()).getGlobalSettings().getVideoEncodingDimensionObject().height;
            watermarkOptions.positionInPortraitMode = new WatermarkOptions.Rectangle(10, height / 2, size, size);
            watermarkOptions.positionInLandscapeMode = new WatermarkOptions.Rectangle(10, height / 2, size, size);
            watermarkOptions.visibleInPreview = true;
            int ret = engine.addVideoWatermark("public static final String WATER_MARK_FILE_PATH = \"/assets/agora-logo.png\";", watermarkOptions);
            if (ret != Constants.ERR_OK) {
                Log.e(TAG, "addVideoWatermark error=" + ret + ", msg=" + RtcEngine.getErrorDescription(ret));
            }
        } else {
            engine.clearVideoWatermarks();
        }
    }

    private void enableLowStream(boolean enable) {
        engine.setRemoteDefaultVideoStreamType(enable ? Constants.VIDEO_STREAM_LOW : Constants.VIDEO_STREAM_HIGH);
        if (remoteUid != 0) {
            engine.setRemoteVideoStreamType(remoteUid, enable ? Constants.VIDEO_STREAM_LOW : Constants.VIDEO_STREAM_HIGH);
        }
    }

    private void setEncodingPreference(int index) {
        VideoEncoderConfiguration.ENCODING_PREFERENCE[] preferences = new VideoEncoderConfiguration.ENCODING_PREFERENCE[]{
                VideoEncoderConfiguration.ENCODING_PREFERENCE.PREFER_AUTO,
                VideoEncoderConfiguration.ENCODING_PREFERENCE.PREFER_HARDWARE,
                VideoEncoderConfiguration.ENCODING_PREFERENCE.PREFER_SOFTWARE,
        };

        VideoEncoderConfiguration.AdvanceOptions advanceOptions = new VideoEncoderConfiguration.AdvanceOptions();
        advanceOptions.encodingPreference = preferences[index];
        videoEncoderConfiguration.advanceOptions = advanceOptions;
        engine.setVideoEncoderConfiguration(videoEncoderConfiguration);
    }

    private void enableBFrame(boolean enable) {
        videoEncoderConfiguration.advanceOptions.compressionPreference = enable ?
                VideoEncoderConfiguration.COMPRESSION_PREFERENCE.PREFER_QUALITY :
                VideoEncoderConfiguration.COMPRESSION_PREFERENCE.PREFER_LOW_LATENCY;
        engine.setVideoEncoderConfiguration(videoEncoderConfiguration);
    }

    private void enableLowLegacy(boolean enable) {
        if (isHost) {
            return;
        }
        ClientRoleOptions clientRoleOptions = new ClientRoleOptions();
        clientRoleOptions.audienceLatencyLevel = enable ? Constants.AUDIENCE_LATENCY_LEVEL_ULTRA_LOW_LATENCY : Constants.AUDIENCE_LATENCY_LEVEL_LOW_LATENCY;
        engine.setClientRole(Constants.CLIENT_ROLE_AUDIENCE, clientRoleOptions);
    }

    private void takeSnapshot(int uid) {
        if (uid != 0) {
            String filePath = requireContext().getExternalCacheDir().getAbsolutePath() + File.separator + "livestreaming_snapshot.png";
            int ret = engine.takeSnapshot(uid, filePath);
            if (ret != Constants.ERR_OK) {
                showLongToast("takeSnapshot error code=" + ret + ",msg=" + RtcEngine.getErrorDescription(ret));
            }
        } else {
            showLongToast(getString(R.string.remote_screenshot_tip));
        }
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
            Log.e(TAG, String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
            showAlert(String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err)));
            showLongToast(String.format("join channel error : ", err));
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
                    if (joinType.equals("au")) {
                        btnSetting.setText("离开");
                        btnJoin.setEnabled(false);
                        btnPreload.setEnabled(false);
                        btnSetting.setEnabled(true);
                    } else if (joinType.equals("broadcast")) {
                        btnJoin.setText("离开");
                        btnJoin.setEnabled(true);
                        btnSetting.setEnabled(false);
                    }
//                    mRootBinding.btnJoin.setEnabled(true);
//                    mRootBinding.btnJoin.setText(getString(R.string.leave));
                    btnPublish.setEnabled(true);

                    if (isLocalVideoForeground) {
                        foreGroundVideo.setReportUid(uid);
                    } else {
                        backGroundVideo.setReportUid(uid);
                    }
                }
            });
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

        /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         * @param uid ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.*/
        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);

            backGroundVideo.setVisibility(View.VISIBLE);
            Log.i(TAG, "onUserJoined->" + uid);
            showLongToast(String.format("user %d joined!", uid));
            /**Check if the context is correct*/
            Context context = getContext();
            if (context == null) {
                return;
            }
            if (remoteUid != 0) {
                return;
            } else {
                remoteUid = uid;
            }
            handler.post(() ->
            {
                VideoReportLayout videoContainer = isLocalVideoForeground ? backGroundVideo : foreGroundVideo;

                if (!isB) {
                    foreGroundVideo.setVisibility(View.GONE);
                    switchView();
                }

                /**Display remote video stream*/
                SurfaceView surfaceView = null;
                if (videoContainer.getChildCount() > 0) {
                    videoContainer.removeAllViews();
                }
                // Create render view by RtcEngine
                surfaceView = new SurfaceView(context);
                surfaceView.setZOrderMediaOverlay(!isLocalVideoForeground);
                // Add to the remote container
                videoContainer.addView(surfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                videoContainer.setReportUid(remoteUid);
                // Setup remote video to render
                VideoCanvas remote = new VideoCanvas(surfaceView, canvasRenderMode, remoteUid);
//                remote.backgroundColor = canvasBgColor;
                engine.setupRemoteVideo(remote);
            });
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
            if (uid == remoteUid) {
                remoteUid = 0;
                runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isB) {
                            foreGroundVideo.setVisibility(View.VISIBLE);
                            switchView();
                        }

                        /**Clear render view
                         Note: The video will stay at its last frame, to completely remove it you will need to
                         remove the SurfaceView from its parent*/
                        VideoCanvas remote = new VideoCanvas(null, canvasRenderMode, uid);
//                        remote.backgroundColor = canvasBgColor;
                        engine.setupRemoteVideo(remote);

                        VideoReportLayout videoContainer = isLocalVideoForeground ? backGroundVideo : foreGroundVideo;
                        videoContainer.setReportUid(-1);
                        videoContainer.removeAllViews();
                    }
                });
            }
        }

        /**
         * Occurs when the user role switches in a live streaming. For example, from a host to an audience or vice versa.
         *
         * The SDK triggers this callback when the local user switches the user role by calling the setClientRole method after joining the channel.
         * @param oldRole Role that the user switches from.
         * @param newRole Role that the user switches to.
         */
        @Override
        public void onClientRoleChanged(int oldRole, int newRole, ClientRoleOptions newRoleOptions) {
            super.onClientRoleChanged(oldRole, newRole, newRoleOptions);
            Log.i(TAG, String.format("client role changed from state %d to %d", oldRole, newRole));
            runOnUIThread(() -> {
                btnPublish.setEnabled(true);
            });
        }

        @Override
        public void onSnapshotTaken(int uid, String filePath, int width, int height, int errCode) {
            super.onSnapshotTaken(uid, filePath, width, height, errCode);
            Log.d(TAG, String.format(Locale.US, "onSnapshotTaken uid=%d, filePath=%s, width=%d, height=%d, errorCode=%d", uid, filePath, width, height, errCode));
            if (errCode == 0) {
                showLongToast("SnapshotTaken path=" + filePath);
            } else {
                showLongToast("SnapshotTaken error=" + RtcEngine.getErrorDescription(errCode));
            }
        }

        @Override
        public void onLocalVideoStats(Constants.VideoSourceType source, LocalVideoStats stats) {
            super.onLocalVideoStats(source, stats);
//            if (isLocalVideoForeground) {
//                foreGroundVideo.setLocalVideoStats(stats);
//            } else {
//                backGroundVideo.setLocalVideoStats(stats);
//            }
        }

        @Override
        public void onLocalAudioStats(LocalAudioStats stats) {
            super.onLocalAudioStats(stats);
//            if (isLocalVideoForeground) {
//                foreGroundVideo.setLocalAudioStats(stats);
//            } else {
//                backGroundVideo.setLocalAudioStats(stats);
//            }
        }

        @Override
        public void onRemoteVideoStats(RemoteVideoStats stats) {
            super.onRemoteVideoStats(stats);
//            if (!isLocalVideoForeground) {
//                foreGroundVideo.setRemoteVideoStats(stats);
//            } else {
//                backGroundVideo.setRemoteVideoStats(stats);
//            }
        }

        @Override
        public void onRemoteAudioStats(RemoteAudioStats stats) {
            super.onRemoteAudioStats(stats);
//            if (!isLocalVideoForeground) {
//                foreGroundVideo.setRemoteAudioStats(stats);
//            } else {
//                backGroundVideo.setRemoteAudioStats(stats);
//            }
        }

        @Override
        public void onVideoRenderingTracingResult(int uid, Constants.MEDIA_TRACE_EVENT currentEvent, VideoRenderingTracingInfo tracingInfo) {
            super.onVideoRenderingTracingResult(uid, currentEvent, tracingInfo);
            runOnUIThread(() -> {
                trackView = LayoutInflater.from(getContext()).inflate(R.layout.fragment_live_streaming_video_tracking, null);

                trackView.setVisibility(View.VISIBLE);
                TextView tvUid = trackView.findViewById(R.id.tv_uid);
                TextView tvEvent = trackView.findViewById(R.id.tv_event);
                TextView tvElapsedTime = trackView.findViewById(R.id.tv_elapsedTime);
                TextView tvStart2JoinChannel = trackView.findViewById(R.id.tv_start2JoinChannel);
                TextView tvJoin2JoinSuccess = trackView.findViewById(R.id.tv_join2JoinSuccess);
                TextView tvJoinSuccess2RemoteJoined = trackView.findViewById(R.id.tv_joinSuccess2RemoteJoined);
                TextView tvRemoteJoined2SetView = trackView.findViewById(R.id.tv_remoteJoined2SetView);
                TextView tvRemoteJoined2UnmuteVideo = trackView.findViewById(R.id.tv_remoteJoined2UnmuteVideo);
                TextView tvRemoteJoined2PacketReceived = trackView.findViewById(R.id.tv_remoteJoined2PacketReceived);

                tvUid.setText(String.valueOf(uid));
                tvEvent.setText(String.valueOf(currentEvent.getValue()));
                tvElapsedTime.setText(String.format(Locale.US, "%d ms", tracingInfo.elapsedTime));
                tvStart2JoinChannel.setText(String.format(Locale.US, "%d ms", tracingInfo.start2JoinChannel));
                tvJoin2JoinSuccess.setText(String.format(Locale.US, "%d ms", tracingInfo.join2JoinSuccess));
                tvJoinSuccess2RemoteJoined.setText(String.format(Locale.US, "%d ms", tracingInfo.joinSuccess2RemoteJoined));
                tvRemoteJoined2SetView.setText(String.format(Locale.US, "%d ms", tracingInfo.remoteJoined2SetView));
                tvRemoteJoined2UnmuteVideo.setText(String.format(Locale.US, "%d ms", tracingInfo.remoteJoined2UnmuteVideo));
                tvRemoteJoined2PacketReceived.setText(String.format(Locale.US, "%d ms", tracingInfo.remoteJoined2PacketReceived));
            });
        }

    };

    private void setParams() {
        if (null != engine) {
            // engine.setParameters("{\"rtc.enableMultipath\":true}");

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
    }
}