package com.shunf4.fucksonyambient;

/*
 * Copyright 2013 two forty four a.m. LLC <http://www.twofortyfouram.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public final class BluetoothBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = "SonyHeadphonesControl";
    public static final UUID uuid = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba");
    public static final UUID uuid_alt = UUID.fromString("ba69e0f5-16e3-2db3-ad46-68503e20cc96");
    public static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"; //$NON-NLS-1$
    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"; //$NON-NLS-1$
    public static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"; //$NON-NLS-1$
    public static final String EXTRA_STRING_MODE = "mode";
    public static final String EXTRA_STRING_VOLUME = "volume";
    public static final String EXTRA_STRING_VOICE = "voice";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) return;
            String address = device.getAddress();
            if (address.toLowerCase().equals(
                    sharedPreferences.getString(MainActivity.KEY_LISTENING_MAC, "")
                            .toLowerCase()
            )) {
                Log.i("FuckSonyAmbient.BluetoothBroadcastReceiver",  address + " connected, switching ambient mode");

                int mode = sharedPreferences.getInt(MainActivity.KEY_AMBIENT_MODE, 0);
                int volume = sharedPreferences.getInt(MainActivity.KEY_VOLUME, 0);
                boolean voice = sharedPreferences.getBoolean(MainActivity.KEY_VOICE_OPTIMIZED, false);

                execute(context, device, mode, volume, voice);

            }
        }

    }

    public static void execute(Context context, BluetoothDevice device, int mode, int volume, boolean voice) {
        boolean enabled = false;
        int noiseCancelling = 0;
        switch (mode) {
            case 0:
                enabled = false;
                break;
            case 1:
                enabled = true;
                noiseCancelling = 2;
                break;
            case 2:
                enabled = true;
                noiseCancelling = 1;
                break;
            case 3:
                enabled = true;
                noiseCancelling = 0;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        boolean finalEnabled = enabled;
        int finalNoiseCancelling = noiseCancelling;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (setAmbientSound(context, device, finalEnabled, finalNoiseCancelling, volume, voice)) {
                        Toast.makeText(context, context.getString(R.string.set_ambient_successful) + context.getString(MainActivity.MODE_STRINGS[mode])
                                        + (mode == 3 ?
                                        ("; " + context.getString(R.string.volume) + volume +
                                                (voice ? ("; " + context.getString(R.string.voice_optimized) ) : "")
                                        ) : "")
                                , Toast.LENGTH_SHORT).show();
                    } else {
                        final String message = context.getString(R.string.headset_not_found);
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    final String message = context.getString(R.string.io_error);
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, 1000);
    }

    static boolean setAmbientSound(Context context, BluetoothDevice device, boolean enabled, int noiseCancelling, int volume, boolean voice) throws IOException, InterruptedException {
        return sendData(context, device, new byte[]{0x00, 0x00, 0x00, 0x08, 0x68, 0x02, (byte) (enabled ? 0x10 : 0x00), 0x02, (byte) (noiseCancelling), 0x01, (byte) (voice ? 1 : 0), (byte) volume});
    }

    static boolean sendData(Context context, BluetoothDevice device, byte[] data) throws IOException, InterruptedException {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();

        ParcelUuid[] uuids = device.getUuids();

        if(uuids == null)
            return false;

        boolean isSonyHeadset = false;
        for (ParcelUuid u : uuids) {
            if (u.toString().equals(uuid.toString()) || u.toString().equals(uuid_alt.toString())) {
                isSonyHeadset = true;
                break;
            }
        }

        if (!isSonyHeadset) return false;

        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid);
        try {
//            Log.i(TAG, "Socket connected: " + socket.isConnected());
            socket.connect();
//            Log.i(TAG, "Socket connected: " + socket.isConnected());

            byte[] packet = new byte[data.length + 2];
            packet[0] = 0x0c;
            packet[1] = 0;
            for (int j = 0; j < data.length; j++) {
                packet[j + 2] = data[j];
            }
            sendPacket(socket, packet);
            packet[1] = 1;
            sendPacket(socket, packet);

            return true;
        } finally {
            socket.close();
        }
    }

    static void sendPacket(BluetoothSocket socket, byte[] data) throws IOException, InterruptedException {
        OutputStream o = socket.getOutputStream();
        InputStream i = socket.getInputStream();
        byte[] packet = new byte[data.length + 3];
        packet[0] = 0x3e;
        packet[packet.length - 1] = 0x3c;
        byte crc = 0;
        for (int j = 0; j < data.length; j++) {
            crc += data[j];
            packet[j + 1] = data[j];
        }
        packet[packet.length - 2] = crc;
        o.write(packet);

        byte[] buffer = new byte[256];
        Date date = new Date();
        while (new Date().getTime() - date.getTime() < 200) {
            if (i.available() > 0) {
                int r = i.read(buffer);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < r; j++) {
                    sb.append(String.format(" %02x", buffer[j]));
                }
//                Log.i(TAG, "Read: " + r + " bytes:" + sb);
                break;
            }
            Thread.sleep(50);
        }
    }
}