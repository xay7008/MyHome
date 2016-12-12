package com.example.xzy.myhome.activity;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.xzy.myhome.R;
import com.example.xzy.myhome.Setting;
import com.example.xzy.myhome.adapter.DeviceItemRecycleViewAdapter;
import com.example.xzy.myhome.model.bean.Device;
import com.example.xzy.myhome.model.bean.PacketBean;
import com.example.xzy.myhome.util.ToastUtil;
import com.gizwits.gizwifisdk.api.GizWifiDevice;
import com.gizwits.gizwifisdk.enumration.GizWifiDeviceNetStatus;
import com.yanzhenjie.recyclerview.swipe.Closeable;
import com.yanzhenjie.recyclerview.swipe.OnSwipeMenuItemClickListener;
import com.yanzhenjie.recyclerview.swipe.SwipeMenu;
import com.yanzhenjie.recyclerview.swipe.SwipeMenuCreator;
import com.yanzhenjie.recyclerview.swipe.SwipeMenuItem;
import com.yanzhenjie.recyclerview.swipe.SwipeMenuRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.example.xzy.myhome.model.bean.PacketBean.COMMAND.UPDATE_DEVICE_COUNT;
import static com.mxchip.helper.ProbeReqData.bytesToHex;

public class Main2Activity extends BaseActivity implements DeviceItemRecycleViewAdapter.DeviceSetListener {

    @BindView(R.id.tb_device_list1)
    Toolbar tbDeviceList;

    @BindView(R.id.text_view_logcat)
    TextView textViewLogcat;

    @BindView(R.id.rv_device_item)
    SwipeMenuRecyclerView rvDeviceItem;
    @BindView(R.id.button_error1)
    Button buttonError1;
    @BindView(R.id.layout_debug)
    ScrollView layoutDebug;

    static int sLampLuminance=150;
    private GizWifiDevice mGizDevice;
    private List<Device> mDeviceList;
    private DeviceItemRecycleViewAdapter deviceRVAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        ButterKnife.bind(this);
        Intent intent = getIntent();
        mGizDevice = intent.getParcelableExtra("gizDevice");
        mGizDevice.setListener(mDeviceListener);
        mGizDevice.getDeviceStatus();
        mDeviceList = new ArrayList<>();
        if (Setting.TEST) testInitData();
        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Setting.PREF_DEBUG, false))
            layoutDebug.setVisibility(View.GONE);
        iniToolbarList();
        initRecycleView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGizDevice.setSubscribe(false);
    }


    private void iniToolbarList() {
        tbDeviceList.inflateMenu(R.menu.devicelist_toolbar_menu);
        tbDeviceList.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                switch (itemId) {
                    case R.id.add_item:
                        break;
                    case R.id.refresh_item:
                        break;
                    case R.id.delete_item:
                        break;
                }
                return true;
            }
        });
    }

    private void initRecycleView() {
        final int _width = getResources().getDimensionPixelSize(R.dimen.item_width);
        final int _height = ViewGroup.LayoutParams.MATCH_PARENT;
        deviceRVAdapter = new DeviceItemRecycleViewAdapter(mDeviceList);
        deviceRVAdapter.setDeviceSetListener(this);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        rvDeviceItem.setLayoutManager(linearLayoutManager);
        rvDeviceItem.setAdapter(deviceRVAdapter);
        rvDeviceItem.setSwipeMenuCreator(new SwipeMenuCreator() {
            @Override
            public void onCreateMenu(SwipeMenu swipeLeftMenu, SwipeMenu swipeRightMenu, int viewType) {
                SwipeMenuItem setItem = new SwipeMenuItem(Main2Activity.this)
                        .setBackgroundColor(getResources().getColor(R.color.colorPrimary))
                        .setImage(R.drawable.ic_settings_white_24dp)
                        .setHeight(_height)
                        .setWidth(_width);
                swipeLeftMenu.addMenuItem(setItem);
            }
        });
        rvDeviceItem.setSwipeMenuItemClickListener(new OnSwipeMenuItemClickListener() {
            @Override
            public void onItemClick(Closeable closeable, int adapterPosition, int menuPosition, int direction) {
                closeable.smoothCloseMenu();
                Intent intent = new Intent(Main2Activity.this, SettingsActivity.class);
                intent.putExtra("device", mDeviceList.get(adapterPosition));
                intent.putExtra("gizDevice", mGizDevice);
                startActivity(intent);
            }
        });

    }

    @Override
    protected void receiveSucceedData(byte[] b) {
        textViewLogcat.setText(mLogcat);
        PacketBean packetBean = new PacketBean(b);
        switch (PacketBean.receiveDataType(packetBean)) {
            case PacketBean.RECEIVE_DEVICE_TYPE.DEVICE:
                updateDeviceData(packetBean);
                break;
            case PacketBean.RECEIVE_DEVICE_TYPE.GATEWAY:
                updateDeviceList(packetBean);
                break;
        }
    }


    private void updateDeviceData(PacketBean packetBean) {
        int index = getListIndex(packetBean.getMac());
        if (index == -1) {
            Log.e(TAG, "processReceiveData: " + "收到未知设备的神秘请求");
            return;
        }
        switch (packetBean.getCommand()) {
            case PacketBean.COMMAND.STATE_READ:
                updateDeviceState(index, packetBean);
                break;
            case PacketBean.COMMAND.TIME_READ:
                byte[] _mac = mDeviceList.get(index).getMac();
                byte _deviceType = mDeviceList.get(index).getDeviceType();
                PacketBean.updateDeviceTime(_mac, _deviceType, mGizDevice);
                break;
        }
    }

    private void updateDeviceState(int index, PacketBean packetBean) {
        if (packetBean.getDeviceType() == PacketBean.DEVICE_TYPE.SENSOR_TEMPERATURE) {
            mDeviceList.get(index).setTemperature(packetBean.getDataTemperature());
            mDeviceList.get(index).setHumidity(packetBean.getDataHumidity());
            Log.i(TAG, "温度传感器数据" + mDeviceList.get(index) + "    " + packetBean.getDataState());
            deviceRVAdapter.notifyItemChanged(index);
        } else {
            mDeviceList.get(index).setSwitchState(packetBean.getDataState());
            Log.i(TAG, "接收到设备状态" + mDeviceList.get(index) + "    " + packetBean.getDataState());
            deviceRVAdapter.notifyDataSetChanged();
        }
    }

    private int getListIndex(byte[] mac) {
        for (int a = 0; a < mDeviceList.size(); a++) {
            if (Arrays.equals(mDeviceList.get(a).getMac(), mac))
                return a;
        }
        return -1;
    }

    private void updateDeviceList(PacketBean packetBean) {
        //请求设备数
        if (packetBean.getCommand() == PacketBean.COMMAND.UPDATE_DEVICE_COUNT ||
                packetBean.getCommand() == PacketBean.COMMAND.DEVICE_RESPONSE_APP_COUNT) {
            byte count;
            byte[] data = packetBean.getData();
            count = data[0];
            Log.i(TAG, "updateDeviceList: 接收到设备列表更新，有" + count + "台设备");
            mDeviceList.removeAll(mDeviceList);
            for (byte i = 0; i < count; i++) {
                new PacketBean().requestDeviceList(mGizDevice, i);
            }
            //获取设备信息
        } else if (packetBean.getCommand() == PacketBean.COMMAND.UPDATE_DEVICE_MESSAGE) {
            Device device = new Device();
            int index = getListIndex(packetBean.getDataMac());
            Log.w(TAG, "index:" + index);
            if (index != -1) {
                device = mDeviceList.get(index);
                device.setMac(packetBean.getDataMac());
                device.setDeviceType(packetBean.getDataDeviceType());
            } else {
                device.setMac(packetBean.getDataMac());
                device.setDeviceType(packetBean.getDataDeviceType());
                mDeviceList.add(device);
            }

            Log.i(TAG, "获取到的设备MAC：" + bytesToHex(packetBean.getDataMac()) +
                    "类型" + packetBean.getDataDeviceType());
            deviceRVAdapter.notifyDataSetChanged();
        }
    }



    @OnClick({R.id.button_error, R.id.button_error1, R.id.button_clear})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_error:
                new PacketBean().requestDeviceList(mGizDevice, (byte) 0);
                break;
            case R.id.button_error1:
                PacketBean packetBean = new PacketBean();
                packetBean.setType(PacketBean.TYPE.APP_REQUEST);
                packetBean.setCommand(UPDATE_DEVICE_COUNT);
                packetBean.sendPacket(mGizDevice);
                break;
            case R.id.button_clear:
                mLogcat = "";
                textViewLogcat.setText(mLogcat);
                break;
        }
    }



    @Override
    public void onNameClick(int position, View view) {
        //// TODO: 2016/10/26 名字修改

    }

    @Override
    public void onSwitchClick(int position, View view, byte switchState) {
        PacketBean packetBean = new PacketBean();
        packetBean.setMac(mDeviceList.get(position).getMac())
                .setDeviceType(mDeviceList.get(position).getDeviceType())
                .setType(PacketBean.TYPE.APP_REQUEST)
                .setCommand(PacketBean.COMMAND.STATE_WRITE)
                .setDataState(switchState)
                .setDataLength((byte) 1)
                .sendPacket(mGizDevice);
        sLampLuminance = switchState;
    }

    private void testInitData() {
        Device device1 = new Device();
        device1.setDeviceType(PacketBean.DEVICE_TYPE.SENSOR_TEMPERATURE);
        device1.setMac(new byte[]{0, 0, 0, 0, 0, 0, 0, 1});
        Device device2 = new Device();
        device2.setDeviceType(PacketBean.DEVICE_TYPE.LAMP);
        device2.setMac(new byte[]{0, 0, 0, 0, 0, 0, 0, 2});

        Device device3 = new Device();
        device3.setDeviceType(PacketBean.DEVICE_TYPE.CURTAIN);
        device3.setMac(new byte[]{0, 0, 0, 0, 0, 0, 0, 3});

        Device device4 = new Device();
        device4.setDeviceType(PacketBean.DEVICE_TYPE.SOCKET);
        device4.setMac(new byte[]{0, 0, 0, 0, 0, 0, 0, 4});

        Device device5 = new Device();
        device5.setDeviceType(PacketBean.DEVICE_TYPE.SOCKET);
        device5.setMac(new byte[]{0, 0, 0, 0, 0, 0, 0, 5});

        Collections.addAll(mDeviceList, device1, device2, device3, device4, device5);
    }

    @Override
    protected void mDidUpdateNetStatus(GizWifiDevice device, GizWifiDeviceNetStatus netStatus) {
        switch (netStatus) {
            case GizDeviceOnline:
                ToastUtil.showToast(Main2Activity.this, device.getProductName() + "设备上线");
                break;
            case GizDeviceOffline:
                Intent intent = new Intent("com.example.xzy.myhome.FORCE_OFFLINE");
                sendBroadcast(intent);
                ToastUtil.showToast(Main2Activity.this, device.getProductName() + "设备状态变为:离线");
                break;
            case GizDeviceControlled:
                PacketBean packetBean = new PacketBean();
                packetBean.setType(PacketBean.TYPE.APP_REQUEST);
                packetBean.setCommand(UPDATE_DEVICE_COUNT);
                packetBean.sendPacket(mGizDevice);
                ToastUtil.showToast(Main2Activity.this, device.getProductName() + "设备状态变为:可控");
                break;
            case GizDeviceUnavailable:
                ToastUtil.showToast(Main2Activity.this, device.getProductName() + "设备状态变为:难以获得的");
                break;
        }
    }
}