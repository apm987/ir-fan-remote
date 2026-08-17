package io.github.albert.extractorir;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.hardware.ConsumerIrManager;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.VibrationAttributes;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String PREFERENCES_NAME = "mando_preferences";
    private static final String VIBRATION_DURATION_KEY = "vibration_duration_ms";
    private static final int DEFAULT_VIBRATION_DURATION_MS = 30;
    private static final int MAX_VIBRATION_DURATION_MS = 250;
    private static final int MAX_VIBRATION_AMPLITUDE = 255;
    private static final AudioAttributes LEGACY_VIBRATION_ATTRIBUTES =
            new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

    private final ExecutorService transmitterExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isTransmitting = new AtomicBoolean(false);

    private ConsumerIrManager irManager;
    private SharedPreferences preferences;
    private Vibrator vibrator;
    private View infraredLed;
    private Button[] remoteButtons;
    private int vibrationDurationMs;
    private boolean emitterReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        vibrationDurationMs = preferences.getInt(
                VIBRATION_DURATION_KEY,
                DEFAULT_VIBRATION_DURATION_MS
        );
        vibrator = getDeviceVibrator();

        applySystemBarInsets(findViewById(R.id.screenRoot));
        infraredLed = findViewById(R.id.infraredLed);
        remoteButtons = new Button[] {
                findViewById(R.id.buttonLightOn),
                findViewById(R.id.buttonLightOff),
                findViewById(R.id.buttonFan1),
                findViewById(R.id.buttonFan2),
                findViewById(R.id.buttonFan3),
                findViewById(R.id.buttonFanOff)
        };

        bindButton(R.id.buttonLightOn, RemoteCommand.LIGHT_ON);
        bindButton(R.id.buttonLightOff, RemoteCommand.LIGHT_OFF);
        bindButton(R.id.buttonFan1, RemoteCommand.FAN_1);
        bindButton(R.id.buttonFan2, RemoteCommand.FAN_2);
        bindButton(R.id.buttonFan3, RemoteCommand.FAN_3);
        bindButton(R.id.buttonFanOff, RemoteCommand.FAN_OFF);
        findViewById(R.id.settingsButton).setOnClickListener(
                ignored -> showVibrationSettings()
        );

        irManager = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        boolean debugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debugBuild && getIntent().getBooleanExtra("preview_mode", false)) {
            // Allows a faithful layout screenshot on the Android emulator, which has no IR hardware.
            emitterReady = true;
            setLedActive(false);
            setButtonsEnabled(true);
        } else {
            updateEmitterState();
        }
    }

    private void bindButton(int viewId, RemoteCommand command) {
        findViewById(viewId).setOnClickListener(view -> transmit(command));
    }

    private void updateEmitterState() {
        if (irManager == null || !irManager.hasIrEmitter()) {
            emitterReady = false;
            setButtonsEnabled(false);
            Toast.makeText(this, R.string.no_emitter_message, Toast.LENGTH_LONG).show();
            return;
        }

        if (!supportsCarrierFrequency(irManager, IrProtocol.CARRIER_FREQUENCY_HZ)) {
            emitterReady = false;
            setButtonsEnabled(false);
            Toast.makeText(this, R.string.frequency_unsupported_message, Toast.LENGTH_LONG).show();
            return;
        }

        emitterReady = true;
        setLedActive(false);
        setButtonsEnabled(true);
    }

    private void transmit(RemoteCommand command) {
        if (!emitterReady || !isTransmitting.compareAndSet(false, true)) {
            return;
        }

        vibrate(vibrationDurationMs);
        setLedActive(true);

        transmitterExecutor.execute(() -> {
            String error = null;
            try {
                irManager.transmit(IrProtocol.CARRIER_FREQUENCY_HZ, command.pattern());
            } catch (RuntimeException exception) {
                error = exception.getMessage();
            }

            final String transmissionError = error;
            runOnUiThread(() -> finishTransmission(transmissionError));
        });
    }

    private void finishTransmission(String error) {
        isTransmitting.set(false);
        if (isFinishing() || isDestroyed()) {
            return;
        }

        setLedActive(false);
        if (error == null) {
            setButtonsEnabled(true);
        } else {
            Toast.makeText(this, getString(R.string.transmission_error, error), Toast.LENGTH_LONG).show();
            setButtonsEnabled(emitterReady);
        }
    }

    private void setLedActive(boolean active) {
        infraredLed.setBackgroundResource(active ? R.drawable.bg_led_on : R.drawable.bg_led_off);
        infraredLed.animate().cancel();
        if (active) {
            infraredLed.setScaleX(0.82f);
            infraredLed.setScaleY(0.82f);
            infraredLed.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90L).start();
        } else {
            infraredLed.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button button : remoteButtons) {
            button.setEnabled(enabled);
        }
    }

    private void showVibrationSettings() {
        Context dialogContext = new ContextThemeWrapper(
                this,
                R.style.Theme_ExtractorIr_Dialog
        );
        LinearLayout content = new LinearLayout(dialogContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);

        TextView durationValue = new TextView(dialogContext);
        durationValue.setGravity(Gravity.CENTER);
        durationValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);

        SeekBar durationSelector = new SeekBar(dialogContext);
        durationSelector.setMax(MAX_VIBRATION_DURATION_MS);
        durationSelector.setProgress(vibrationDurationMs);

        TextView hint = new TextView(dialogContext);
        hint.setText(R.string.vibration_settings_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        content.addView(
                durationValue,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        selectorParams.topMargin = dp(8);
        content.addView(durationSelector, selectorParams);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = dp(8);
        content.addView(hint, hintParams);

        updateDurationLabel(durationValue, vibrationDurationMs);
        durationSelector.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDurationLabel(durationValue, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to do.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // The value is saved only after pressing Guardar.
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(dialogContext)
                .setTitle(R.string.vibration_settings_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.vibration_test, null)
                .setPositiveButton(R.string.save, (ignored, which) -> {
                    vibrationDurationMs = durationSelector.getProgress();
                    preferences.edit()
                            .putInt(VIBRATION_DURATION_KEY, vibrationDurationMs)
                            .apply();
                })
                .create();

        dialog.setOnShowListener(ignored -> dialog
                .getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(button -> vibrate(durationSelector.getProgress())));
        dialog.show();
    }

    private void updateDurationLabel(TextView view, int durationMs) {
        view.setText(getString(R.string.vibration_duration, durationMs));
    }

    @SuppressWarnings("deprecation")
    private void vibrate(int durationMs) {
        if (durationMs <= 0 || vibrator == null) {
            return;
        }
        try {
            vibrator.cancel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createOneShot(
                        durationMs,
                        MAX_VIBRATION_AMPLITUDE
                );
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(
                            effect,
                            VibrationAttributes.createForUsage(
                                    VibrationAttributes.USAGE_ALARM
                            )
                    );
                } else {
                    vibrator.vibrate(effect, LEGACY_VIBRATION_ATTRIBUTES);
                }
            } else {
                vibrator.vibrate(durationMs, LEGACY_VIBRATION_ATTRIBUTES);
            }
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.vibration_error, Toast.LENGTH_LONG).show();
        }
    }

    private Vibrator getDeviceVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
            );
            if (manager != null) {
                return manager.getDefaultVibrator();
            }
        }
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private static boolean supportsCarrierFrequency(
            ConsumerIrManager manager,
            int frequencyHz
    ) {
        ConsumerIrManager.CarrierFrequencyRange[] ranges;
        try {
            ranges = manager.getCarrierFrequencies();
        } catch (RuntimeException ignored) {
            return true;
        }

        // Some vendor implementations return null even though transmission works.
        if (ranges == null || ranges.length == 0) {
            return true;
        }
        for (ConsumerIrManager.CarrierFrequencyRange range : ranges) {
            if (frequencyHz >= range.getMinFrequency()
                    && frequencyHz <= range.getMaxFrequency()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static void applySystemBarInsets(View root) {
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    initialLeft + insets.getSystemWindowInsetLeft(),
                    initialTop + insets.getSystemWindowInsetTop(),
                    initialRight + insets.getSystemWindowInsetRight(),
                    initialBottom + insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        root.requestApplyInsets();
    }

    @Override
    protected void onDestroy() {
        transmitterExecutor.shutdownNow();
        super.onDestroy();
    }
}
