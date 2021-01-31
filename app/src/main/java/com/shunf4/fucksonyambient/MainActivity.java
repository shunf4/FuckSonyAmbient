package com.shunf4.fucksonyambient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements TextWatcher, CompoundButton.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener {

    EditText listeningMac;
    RadioButton disable;
    RadioButton noiseCancelling;
    RadioButton windCancelling;
    RadioButton ambientSound;
    SeekBar volume;
    SwitchCompat voiceOptimized;

    public static final String KEY_LISTENING_MAC = "listening_mac";
    public static final String KEY_AMBIENT_MODE = "ambient_mode";
    public static final String KEY_VOLUME = "volume";
    public static final String KEY_VOICE_OPTIMIZED = "voice_optimized";

    public static final int[] MODE_STRINGS = new int[] {
            R.string.disable_ambient_sound_control,
            R.string.enable_noise_cancelling,
            R.string.enable_wind_cancelling,
            R.string.ambient_sound,
    };

    public static byte[] parseMacAddressString(CharSequence macAddressString) {
        String[] parts = macAddressString.toString().split(":");
        if (parts.length != 6) return null;
        if (!Arrays.stream(parts).allMatch(part -> {
            if (part.length() != 2) return false;
            char[] partChars = part.toCharArray();
            return (partChars[0] >= '0' && partChars[0] <= '9'
                    || partChars[0] >= 'a' && partChars[0] <= 'f'
                    || partChars[0] >= 'A' && partChars[0] <= 'F')
                    && (partChars[1] >= '0' && partChars[1] <= '9'
                    || partChars[1] >= 'a' && partChars[1] <= 'f'
                    || partChars[1] >= 'A' && partChars[1] <= 'F');
        })) return null;

        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) {
            result[i] = Integer.valueOf(Integer.parseInt(parts[i], 16)).byteValue();
        }

        return result;
    }

    public static String macAddressToString(byte[] macAddress) {
        StringBuilder sb = new StringBuilder();
        for (byte part : macAddress) {
            sb.append(String.format("%02X", part));
            sb.append(":");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public static int[] byteArrayToIntArray(byte[] byteArray) {
        int[] result = new int[byteArray.length];
        for (int i = 0; i < byteArray.length; i++) {
            result[i] = byteArray[i];
        }
        return result;
    }

    public static byte[] intArrayToByteArray(int[] intArray) {
        byte[] result = new byte[intArray.length];
        for (int i = 0; i < intArray.length; i++) {
            result[i] = (byte) intArray[i];
        }
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listeningMac = findViewById(R.id.input_mac_address_to_listen);
        disable = findViewById(R.id.radioButtonDisable);
        noiseCancelling = findViewById(R.id.radioButtonNoiseCancelling);
        windCancelling = findViewById(R.id.radioButtonWindCancelling);
        ambientSound = findViewById(R.id.radioButtonAmbientSound);
        volume = findViewById(R.id.seekBarVolume);
        voiceOptimized = findViewById(R.id.switchVoiceOptimized);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        listeningMac.setText(sharedPreferences.getString(KEY_LISTENING_MAC, ""));
        int mode = sharedPreferences.getInt(KEY_AMBIENT_MODE, 0);
        switch (mode) {
            case 0: disable.setChecked(true); break;
            case 1: noiseCancelling.setChecked(true); break;
            case 2: windCancelling.setChecked(true); break;
            case 3: ambientSound.setChecked(true); break;
        }

        volume.setProgress(sharedPreferences.getInt(KEY_VOLUME, 0));
        voiceOptimized.setChecked(sharedPreferences.getBoolean(KEY_VOICE_OPTIMIZED, false));

        saveSettings();

        listeningMac.addTextChangedListener(this);
        disable.setOnCheckedChangeListener(this);
        noiseCancelling.setOnCheckedChangeListener(this);
        windCancelling.setOnCheckedChangeListener(this);
        ambientSound.setOnCheckedChangeListener(this);
        volume.setOnSeekBarChangeListener(this);
        voiceOptimized.setOnCheckedChangeListener(this);
    }

    public void applyNow(View view) {
        byte[] macAddress = parseMacAddressString(listeningMac.getText());
        if (macAddress == null) {
            Toast.makeText(this, R.string.error_mac_address, Toast.LENGTH_LONG).show();
            return;
        }

        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice bd =  ba.getRemoteDevice(macAddress);

        if (bd != null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            BluetoothBroadcastReceiver.execute(this, bd,
                    sharedPreferences.getInt(KEY_AMBIENT_MODE, 0),
                    sharedPreferences.getInt(KEY_VOLUME, 0),
                    sharedPreferences.getBoolean(KEY_VOICE_OPTIMIZED, false)
            );
        } else {
            Toast.makeText(this, R.string.headset_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings() {
        int mode;
        if (disable.isChecked()) {
            mode = 0;
        } else if (noiseCancelling.isChecked()) {
            mode = 1;
        } else if (windCancelling.isChecked()) {
            mode = 2;
        } else if (ambientSound.isChecked()) {
            mode = 3;
        } else {
            mode = 0;
        }

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
        editor.putString(KEY_LISTENING_MAC, listeningMac.getText().toString());
        editor.putInt(KEY_AMBIENT_MODE, mode);
        editor.putInt(KEY_VOLUME, volume.getProgress());
        editor.putBoolean(KEY_VOICE_OPTIMIZED, voiceOptimized.isChecked());
        editor.apply();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        saveSettings();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        saveSettings();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        saveSettings();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}