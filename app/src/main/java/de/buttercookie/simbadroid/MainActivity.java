/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.buttercookie.simbadroid;

import static de.buttercookie.simbadroid.util.StyledTextUtils.getStyledText;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.net.InetAddress;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

import de.buttercookie.simbadroid.databinding.ActivityMainBinding;
import de.buttercookie.simbadroid.permissions.Permissions;
import de.buttercookie.simbadroid.service.SmbService;
import de.buttercookie.simbadroid.service.SmbServiceConnection;
import de.buttercookie.simbadroid.service.SmbServiceStatusLiveData;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "SimbaDroidPrefs";
    private static final String PREF_KEY_IP_ADDRESS = "selectedIpAddress";

    private ActivityMainBinding binding;

    private SmbService mService;
    private boolean mBound = false;
    private ArrayAdapter<String> mIpAddressAdapter;
    private final SmbServiceConnection mSmbSrvConn = new SmbServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            super.onServiceConnected(name, service);
            mService = getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            super.onServiceDisconnected(name);
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mIpAddressAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        mIpAddressAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.ipAddressSpinner.setAdapter(mIpAddressAdapter);
        binding.ipAddressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedIp = (String) parent.getItemAtPosition(position);
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString(PREF_KEY_IP_ADDRESS, selectedIp);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.toggleService.setOnClickListener(v -> toggleSmbService());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.toggleService.setAllowClickWhenDisabled(true);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SmbServiceStatusLiveData.get().observe(this, status -> {
            updateButtonState(status);
            updateStatusText(status);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent serviceIntent = new Intent(this, SmbService.class);
        bindService(serviceIntent, mSmbSrvConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        unbindService(mSmbSrvConn);
        mBound = false;
    }

    private void updateButtonState(SmbService.Status status) {
        final MaterialButton button = binding.toggleService;
        final Spinner spinner = binding.ipAddressSpinner;
        final TextView spinnerLabel = binding.ipAddressLabel;

        if (status.serviceRunning()) {
            button.setText(R.string.button_stop_server);
            button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_stop));
            button.setEnabled(true);
            spinner.setEnabled(false);
            spinner.setVisibility(View.VISIBLE);
            spinnerLabel.setVisibility(View.VISIBLE);
        } else {
            button.setText(R.string.button_start_server);
            button.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_start));
            boolean networkAvailable = status.inetAddresses() != null && !status.inetAddresses().isEmpty();
            button.setEnabled(networkAvailable);
            spinner.setEnabled(true);
            if (networkAvailable) {
                spinner.setVisibility(View.VISIBLE);
                spinnerLabel.setVisibility(View.VISIBLE);
            } else {
                spinner.setVisibility(View.GONE);
                spinnerLabel.setVisibility(View.GONE);
            }
        }
    }

    private void updateStatusText(SmbService.Status status) {
        final TextView statusText = binding.serviceStatus;
        updateIpAddressSpinner(status.inetAddresses());

        if (!status.serviceRunning()) {
            statusText.setText(R.string.status_server_off);
        } else if (!status.serverRunning()) {
            statusText.setText(R.string.message_server_waiting_network);
        } else if (StringUtils.isBlank(status.mdnsAddress()) ||
                StringUtils.isBlank(status.ipAddress())) {
            statusText.setText(R.string.message_server_running);
        } else {
            statusText.setText(getStyledText(this,
                    R.string.status_server_running,
                    status.mdnsAddress(),
                    status.netBiosAddress(),
                    status.ipAddress()));
        }
    }

    private void updateIpAddressSpinner(List<InetAddress> addresses) {
        final Spinner spinner = binding.ipAddressSpinner;
        mIpAddressAdapter.clear();

        if (addresses != null) {
            for (InetAddress addr : addresses) {
                mIpAddressAdapter.add(addr.getHostAddress());
            }
        }

        if (mIpAddressAdapter.getCount() > 0) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String savedIp = prefs.getString(PREF_KEY_IP_ADDRESS, null);
            if (savedIp != null) {
                int position = mIpAddressAdapter.getPosition(savedIp);
                if (position >= 0) {
                    spinner.setSelection(position);
                }
            }
        }
    }

    private void toggleSmbService() {
        if (!mBound) {
            Toast.makeText(this,
                    R.string.toast_error_starting_server,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mService.isRunning()) {
            startSmbService();
        } else {
            stopSmbService();
        }
    }

    private void startSmbService() {
        String selectedIp = (String) binding.ipAddressSpinner.getSelectedItem();
        if (selectedIp == null) {
            // This should not happen if the start button is enabled
            return;
        }
        SmbService.startService(this, true, selectedIp);
    }

    private void stopSmbService() {
        mService.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissions.onRequestPermissionsResult(this, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case Permissions.ACTIVITY_MANAGE_STORAGE_RESULT_CODE: {
                Permissions.onManageStorageActivityResult(this);
            }
        }
    }
}