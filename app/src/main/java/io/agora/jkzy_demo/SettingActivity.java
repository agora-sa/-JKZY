package io.agora.jkzy_demo;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

import com.tencent.mmkv.MMKV;

import io.agora.jkzy_demo.config.JKZYConstants;
import io.agora.jkzy_demo.model.GlobalSettings;
import io.agora.rtc2.RtcEngine;

/**
 * @author cjw
 */
public class SettingActivity extends AppCompatActivity {
    private static final String TAG = SettingActivity.class.getSimpleName();

    private MenuItem saveMenu;
    private AppCompatTextView sdkVersion;
    private CardView versionLayout;
    private View privateCloudView;

    private AppCompatSpinner frameRateSpinner;
    private AppCompatSpinner orientationSpinner;
    private AppCompatSpinner dimensionSpinner;
    private AppCompatSpinner areaSpinner;

    private EditText etIpAddress;
    private Switch swLogReport;
    private EditText etLogServerDomain;
    private EditText etLogServerPort;
    private EditText etLogServerPath;
    private Switch swUseHttps;

    // 连续点击10次，弹出弹框，设置私有参数
    private static final int CLICK_COUNT = 10;
    // 连续点击版本号的次数
    private int clickCount = 0;
    private MMKV mmkv;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_layout);

        sdkVersion = this.findViewById(R.id.sdkVersion);
        orientationSpinner = this.findViewById(R.id.orientation_spinner);
        versionLayout = this.findViewById(R.id.version_Layout);
        frameRateSpinner = this.findViewById(R.id.frame_rate_spinner);
        dimensionSpinner = this.findViewById(R.id.dimension_spinner);
        areaSpinner = this.findViewById(R.id.area_spinner);

        privateCloudView = this.findViewById(R.id.private_cloud_layout);

        etIpAddress = privateCloudView.findViewById(R.id.et_ip_address);
        swLogReport = privateCloudView.findViewById(R.id.sw_log_report);
        etLogServerDomain = privateCloudView.findViewById(R.id.et_log_server_domain);
        etLogServerPort = privateCloudView.findViewById(R.id.et_log_server_port);
        etLogServerPath = privateCloudView.findViewById(R.id.et_log_server_path);
        swUseHttps = privateCloudView.findViewById(R.id.sw_use_https);

        sdkVersion.setText(String.format(getString(R.string.sdkversion1), RtcEngine.getSdkVersion()));
        String[] mItems = getResources().getStringArray(R.array.orientations);
        String[] labels = new String[mItems.length];
        for (int i = 0; i < mItems.length; i++) {
            int resId = getResources().getIdentifier(mItems[i], "string", getPackageName());
            labels[i] = getString(resId);
        }
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        orientationSpinner.setAdapter(arrayAdapter);
        fetchGlobalSettings();

        versionLayout.setOnClickListener(v -> {
            clickCount++;
            if (clickCount == CLICK_COUNT) {
                Log.d(TAG, "click pop window!");
                popWindows();
                clickCount = 0;
            }
        });

        initMMKVValue();
    }

    // 初始化之前在mmkv里面设置的值
    private void initMMKVValue() {
        MMKV.initialize(this);
        mmkv = MMKV.defaultMMKV();
    }

    /**
     * 弹出京东定制话设置参数的popWindow
     */
    private void popWindows() {
        boolean isAccessPoint = mmkv.decodeBool(JKZYConstants.KEY_ACCESS_POINT, true); // 是否打开私有化服务器指向
        boolean m265Switch = mmkv.decodeBool(JKZYConstants.KEY_265_SWITCH, true); // h264/h265开关
        boolean mHwEncoder = mmkv.decodeBool(JKZYConstants.KEY_HW_ENCODER, false); // 软/硬编开关
        boolean addState = mmkv.decodeBool(JKZYConstants.KEY_ADD_STATE, false); // 软/硬编开关
        String dimensions = mmkv.decodeString(JKZYConstants.KEY_DIMENSIONS, "VD_640x480"); // 分辨率
        String fps = mmkv.decodeString(JKZYConstants.KEY_FPS, "FRAME_RATE_FPS_15"); // 帧率
        // 分辨率/帧率控制策略
        // 0 画质优先，弱网下调整帧率 不调整分辨率
        // 1 帧率优先，弱网下调整分辨率 不调整帧率
        // 2 平衡模式，弱网下先调整帧率、再调整分辨率；
        String controlStrategy = mmkv.decodeString(JKZYConstants.KEY_CONTROL_STRATEGY, "1 帧率优先");

        Log.d("JD-DEMO", "isAccessPoint=" + isAccessPoint + " , m265Switch=" + m265Switch
                + " , mHwEncoder=" + mHwEncoder + " , dimensions=" + dimensions
                + " , fps=" + fps + " , controlStrategy=" + controlStrategy);

        // 创建一个自定义View，包含需要输入的参数控件
        View customView = LayoutInflater.from(this).inflate(R.layout.dialog_jd_params_setting, null);
        SwitchCompat accessPoint = customView.findViewById(R.id.add_access_point);
        SwitchCompat softCodecSwitch = customView.findViewById(R.id.soft_codec_265_switch);
        SwitchCompat hwEncoderSwitch = customView.findViewById(R.id.hw_encoder);
        SwitchCompat addStateSwitch = customView.findViewById(R.id.add_state);
        AppCompatSpinner dimensionsSpinner = customView.findViewById(R.id.dimensions_spinner);
        AppCompatSpinner fpsSpinner = customView.findViewById(R.id.rate_frame_spinner);
        AppCompatSpinner controlStrategySpinner = customView.findViewById(R.id.control_strategy);

        // ---------------------- 设置默认值 ----------------------
        accessPoint.setChecked(isAccessPoint);
        softCodecSwitch.setChecked(m265Switch);
        hwEncoderSwitch.setChecked(mHwEncoder);
        addStateSwitch.setChecked(addState);
        String[] mItems = getResources().getStringArray(R.array.fps);
        int i = 0;
        if (fps != null) {
            for (String item : mItems) {
                if (fps.equals(item)) {
                    break;
                }
                i++;
            }
        }
        fpsSpinner.setSelection(i);
        mItems = getResources().getStringArray(R.array.dimensions);
        i = 0;
        if (dimensions != null) {
            for (String item : mItems) {
                if (dimensions.equals(item)) {
                    break;
                }
                i++;
            }
        }
        dimensionsSpinner.setSelection(i);
        mItems = getResources().getStringArray(R.array.control_strategy);
        i = 0;
        if (controlStrategy != null) {
            for (String item : mItems) {
                if (controlStrategy.equals(item)) {
                    break;
                }
                i++;
            }
        }
        controlStrategySpinner.setSelection(i);

        // 创建一个AlertDialog.Builder对象
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 设置弹框标题和内容
        builder.setTitle("JD custom params");
        // 将自定义View添加到弹框中
        builder.setView(customView);
        // 设置确定按钮和取消按钮
        builder.setPositiveButton("确定", (dialog, which) -> {
            // 点击确定保存设置的参数
            Log.d(TAG, " click positive button");

            mmkv.encode(JKZYConstants.KEY_ACCESS_POINT, accessPoint.isChecked());
            mmkv.encode(JKZYConstants.KEY_265_SWITCH, softCodecSwitch.isChecked());
            mmkv.encode(JKZYConstants.KEY_HW_ENCODER, hwEncoderSwitch.isChecked());
            mmkv.encode(JKZYConstants.KEY_ADD_STATE, addStateSwitch.isChecked());
            mmkv.encode(JKZYConstants.KEY_DIMENSIONS, getResources().getStringArray(R.array.dimensions)[dimensionsSpinner.getSelectedItemPosition()]);
            mmkv.encode(JKZYConstants.KEY_FPS, getResources().getStringArray(R.array.fps)[fpsSpinner.getSelectedItemPosition()]);
            mmkv.encode(JKZYConstants.KEY_CONTROL_STRATEGY, getResources().getStringArray(R.array.control_strategy)[controlStrategySpinner.getSelectedItemPosition()]);

            Log.d("JD-DEMO", "after click::: isAccessPoint=" + mmkv.decodeInt(JKZYConstants.KEY_ACCESS_POINT) + " , m265Switch=" + mmkv.decodeInt(JKZYConstants.KEY_265_SWITCH)
                    + " , mHwEncoder=" + mmkv.decodeInt(JKZYConstants.KEY_HW_ENCODER) + " , add state " + mmkv.decodeInt(JKZYConstants.KEY_ADD_STATE) + " " +
                    ", dimensions=" + mmkv.decodeInt(JKZYConstants.KEY_DIMENSIONS)
                    + " , fps=" + mmkv.decodeInt(JKZYConstants.KEY_FPS) + " , controlStrategy=" + mmkv.decodeInt(JKZYConstants.KEY_CONTROL_STRATEGY));
        });
        builder.setNegativeButton("取消", null);
        builder.setCancelable(false);

        // 创建弹框对象并显示
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void fetchGlobalSettings() {
        GlobalSettings globalSettings = ((App) getApplication()).getGlobalSettings();

        String[] mItems = getResources().getStringArray(R.array.orientations);
        String selectedItem = globalSettings.getVideoEncodingOrientation();
        int i = 0;
        if (selectedItem != null) {
            for (String item : mItems) {
                if (selectedItem.equals(item)) {
                    break;
                }
                i++;
            }
        }
        orientationSpinner.setSelection(i);
        mItems = getResources().getStringArray(R.array.fps);
        selectedItem = globalSettings.getVideoEncodingFrameRate();
        i = 0;
        if (selectedItem != null) {
            for (String item : mItems) {
                if (selectedItem.equals(item)) {
                    break;
                }
                i++;
            }
        }
        frameRateSpinner.setSelection(i);
        mItems = getResources().getStringArray(R.array.dimensions);
        selectedItem = globalSettings.getVideoEncodingDimension();
        i = 0;
        if (selectedItem != null) {
            for (String item : mItems) {
                if (selectedItem.equals(item)) {
                    break;
                }
                i++;
            }
        }
        dimensionSpinner.setSelection(i);
        mItems = getResources().getStringArray(R.array.areaCode);
        selectedItem = globalSettings.getAreaCodeStr();
        i = 0;
        if (selectedItem != null) {
            for (String item : mItems) {
                if (selectedItem.equals(item)) {
                    break;
                }
                i++;
            }
        }
        areaSpinner.setSelection(i);


        etIpAddress.setText(globalSettings.privateCloudIp);
        swLogReport.setChecked(globalSettings.privateCloudLogReportEnable);
        etLogServerDomain.setText(globalSettings.privateCloudLogServerDomain);
        etLogServerPort.setText(globalSettings.privateCloudLogServerPort + "");
        etLogServerPath.setText(globalSettings.privateCloudLogServerPath);
        swUseHttps.setChecked(globalSettings.privateCloudUseHttps);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        saveMenu = menu.add("保存");
        saveMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == saveMenu.getItemId()) {
            GlobalSettings globalSettings = ((App) getApplication()).getGlobalSettings();
            globalSettings.privateCloudIp = etIpAddress.getText().toString();
            globalSettings.privateCloudLogReportEnable = swLogReport.isChecked();
            globalSettings.privateCloudLogServerDomain = etLogServerDomain.getText().toString();
            globalSettings.privateCloudLogServerPort = Integer.parseInt(etLogServerPort.getText().toString());
            globalSettings.privateCloudLogServerPath = etLogServerPath.getText().toString();
            globalSettings.privateCloudUseHttps = swUseHttps.isChecked();

            globalSettings.setVideoEncodingOrientation(getResources().getStringArray(R.array.orientations)[orientationSpinner.getSelectedItemPosition()]);
            globalSettings.setVideoEncodingFrameRate(getResources().getStringArray(R.array.fps)[frameRateSpinner.getSelectedItemPosition()]);
            globalSettings.setVideoEncodingDimension(getResources().getStringArray(R.array.dimensions)[dimensionSpinner.getSelectedItemPosition()]);
            globalSettings.setAreaCodeStr(getResources().getStringArray(R.array.areaCode)[areaSpinner.getSelectedItemPosition()]);

            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
