package com.limelight;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.CachedAppAssetLoader.BitmapLoadListener;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class ShortcutTrampoline extends Activity {

    private String uuidString;

    private NvApp app;

    private ArrayList<Intent> intentStack = new ArrayList<>();

    private ComputerDetails computer;

    private SpinnerDialog blockingLoadSpinner;

    private Boolean wakingUpComputer = false;

    private ComputerManagerService.ComputerManagerBinder managerBinder;

    private TextView statusMessageTextView;

    private CachedAppAssetLoader loader;

    private boolean gameCancelled = false;

    private static final int ART_WIDTH_PX = 300;

    private static final int SMALL_WIDTH_DP = 100;

    private static final int LARGE_WIDTH_DP = 150;

    private static final long LOADING_DELAY = 7_500L;

    private Bitmap posterBitmap;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder) binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Get the computer object
                    computer = managerBinder.getComputer(uuidString);

                    if (computer == null) {
                        setStatusMessage(getResources().getString(R.string.conn_error_title));
                        Dialog.displayDialog(ShortcutTrampoline.this,
                                getResources().getString(R.string.conn_error_title),
                                getResources().getString(R.string.scut_pc_not_found),
                                true);

                        if (blockingLoadSpinner != null) {
                            blockingLoadSpinner.dismiss();
                            blockingLoadSpinner = null;
                        }

                        if (managerBinder != null) {
                            unbindService(serviceConnection);
                            managerBinder = null;
                        }

                        return;
                    } else {
                        setupAssetLoader(uuidString);
                    }

                    // Force CMS to repoll this machine
                    managerBinder.invalidateStateForComputer(computer.uuid);

                    // Start polling
                    managerBinder.startPolling(new ComputerManagerListener() {
                        @Override
                        public void notifyComputerUpdated(final ComputerDetails details) {
                            // Don't care about other computers
                            Log.d("ShortcutTrampoline", "computer updated");
                            if (!details.uuid.equalsIgnoreCase(uuidString)) {
                                return;
                            }

                            if (details.state != ComputerDetails.State.UNKNOWN) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Stop showing the spinner
                                        if (blockingLoadSpinner != null) {
                                            blockingLoadSpinner.dismiss();
                                            blockingLoadSpinner = null;
                                        }

                                        // If the managerBinder was destroyed before this callback,
                                        // just finish the activity.
                                        if (managerBinder == null) {
                                            closeActivity();
                                            return;
                                        }

                                        if (details.state == ComputerDetails.State.ONLINE
                                                && details.pairState == PairingManager.PairState.PAIRED) {
                                            setStatusMessage("Launching");
                                            wakingUpComputer = false;
                                            Log.d("ShortcutTrampoline", "computer online and paired");

                                            // Launch game if provided app ID, otherwise launch app view
                                            if (app != null) {
                                                if (details.runningGameId == 0 || details.runningGameId == app
                                                        .getAppId()) {
                                                    intentStack.add(ServerHelper
                                                            .createStartIntent(ShortcutTrampoline.this, app, details,
                                                                    managerBinder));

                                                    // Close this activity
                                                    closeActivity();

                                                    // Now start the activities
                                                    startStream();
                                                } else {
                                                    // Create the start intent immediately, so we can safely unbind the managerBinder
                                                    // below before we return.
                                                    final Intent startIntent = ServerHelper
                                                            .createStartIntent(ShortcutTrampoline.this, app, details,
                                                                    managerBinder);

                                                    UiHelper.displayQuitConfirmationDialog(ShortcutTrampoline.this,
                                                            new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    intentStack.add(startIntent);

                                                                    // Close this activity
                                                                    closeActivity();

                                                                    // Now start the activities
                                                                    startStream();
                                                                }
                                                            }, new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    // Close this activity
                                                                    closeActivity();
                                                                }
                                                            });
                                                }
                                            } else {
                                                // Close this activity
                                                closeActivity();

                                                // Add the PC view at the back (and clear the task)
                                                Intent i;
                                                i = new Intent(ShortcutTrampoline.this, PcView.class);
                                                i.setAction(Intent.ACTION_MAIN);
                                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                intentStack.add(i);

                                                // Take this intent's data and create an intent to start the app view
                                                i = new Intent(getIntent());
                                                i.setClass(ShortcutTrampoline.this, AppView.class);
                                                intentStack.add(i);

                                                // If a game is running, we'll make the stream the top level activity
                                                if (details.runningGameId != 0) {
                                                    intentStack.add(ServerHelper
                                                            .createStartIntent(ShortcutTrampoline.this,
                                                                    new NvApp(null, details.runningGameId, false),
                                                                    details, managerBinder));
                                                }

                                                // Now start the activities
                                                startStream();
                                            }

                                        } else if (details.state == ComputerDetails.State.OFFLINE) {

                                            // If computer is offline we will send WOL request.
                                            wakingUpComputer = true;
                                            doWakeOnLan(details);
                                            managerBinder.invalidateStateForComputer(computer.uuid);
                                        } else if (details.pairState != PairingManager.PairState.PAIRED) {
                                            // Computer not paired - display an error dialog
                                            Dialog.displayDialog(ShortcutTrampoline.this,
                                                    getResources().getString(R.string.conn_error_title),
                                                    getResources().getString(R.string.scut_not_paired),
                                                    true);
                                        }

                                        // We don't want any more callbacks from now on, so go ahead
                                        // and unbind from the service
                                        if (managerBinder != null && !wakingUpComputer) {
                                            managerBinder.stopPolling();
                                            unbindService(serviceConnection);
                                            managerBinder = null;
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    protected boolean validateInput(String uuidString, String appIdString) {
        // Validate UUID
        if (uuidString == null) {
            Dialog.displayDialog(ShortcutTrampoline.this,
                    getResources().getString(R.string.conn_error_title),
                    getResources().getString(R.string.scut_invalid_uuid),
                    true);
            return false;
        }

        try {
            UUID.fromString(uuidString);
        } catch (IllegalArgumentException ex) {
            Dialog.displayDialog(ShortcutTrampoline.this,
                    getResources().getString(R.string.conn_error_title),
                    getResources().getString(R.string.scut_invalid_uuid),
                    true);
            return false;
        }

        // Validate App ID (if provided)
        if (appIdString != null && !appIdString.isEmpty()) {
            try {
                Integer.parseInt(appIdString);
            } catch (NumberFormatException ex) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_app_id),
                        true);
                return false;
            }
        }

        return true;
    }

    private void closeActivity() {
        if (managerBinder == null) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!gameCancelled) {
                        finish();
                    }
                }
            }, LOADING_DELAY);
            return;
        }
    }

    private void startStream() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!gameCancelled) {
                    startActivities(intentStack.toArray(new Intent[]{}));
                }
            }
        }, LOADING_DELAY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Full-screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // If we're going to use immersive mode, we want to have
        // the entire screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        }

        // We specified userLandscape in the manifest which isn't supported until 4.3,
        // so we must fall back at runtime to sensorLandscape.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        // Inflate the content
        setContentView(R.layout.activity_game_loader);
//        blurLayout = findViewById(R.id.blurLayout);

//        UiHelper.notifyNewRootView(this);

        String appIdString = getIntent().getStringExtra(Game.EXTRA_APP_ID);
        uuidString = getIntent().getStringExtra(AppView.UUID_EXTRA);

        if (validateInput(uuidString, appIdString)) {
            if (appIdString != null && !appIdString.isEmpty()) {
                app = new NvApp(getIntent().getStringExtra(Game.EXTRA_APP_NAME),
                        Integer.parseInt(appIdString),
                        getIntent().getBooleanExtra(Game.EXTRA_APP_HDR, false));
            }

            // Bind to the computer manager service
            bindService();

            statusMessageTextView = findViewById(R.id.activityGameLoader_status_textView);

            setStatusMessage(getResources().getString(R.string.conn_establishing_title));

            ((ViewGroup) findViewById(R.id.activityGameLoader_poster_layout)).getLayoutTransition()
                    .enableTransitionType(LayoutTransition.CHANGING);
        }
    }

    public void setStatusMessage(final String msg) {
        if (statusMessageTextView != null) {
            statusMessageTextView.animate()
                    .alpha(0)
                    .setDuration(500L)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .setListener(new AnimatorListener() {
                        @Override
                        public void onAnimationStart(final Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(final Animator animation) {
                            statusMessageTextView.setText(msg);
                            statusMessageTextView.animate()
                                    .alpha(1)
                                    .setDuration(500L)
                                    .setInterpolator(new FastOutSlowInInterpolator())
                                    .start();
                        }

                        @Override
                        public void onAnimationCancel(final Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(final Animator animation) {

                        }
                    })
                    .start();
        }
    }

    private void setupAssetLoader(String uniqueId) {
        int dpi = this.getResources().getDisplayMetrics().densityDpi;
        int dp;

        dp = LARGE_WIDTH_DP;

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160.0));
        if (scalingDivisor < 1.0) {
            // We don't want to make them bigger before draw-time
            scalingDivisor = 1.0;
        }

        this.loader = new CachedAppAssetLoader(computer, scalingDivisor,
                new NetworkAssetLoader(this, uniqueId),
                new MemoryAssetLoader(),
                new DiskAssetLoader(this),
                BitmapFactory.decodeResource(this.getResources(), R.drawable.no_app_image));

        final ImageView posterView = findViewById(R.id.activityGameLoader_poster_imageView);
        posterView.setClipToOutline(true);
        final ImageView bgImageViewStaticBlur = findViewById(R.id.activityGameLoader_bg_imageView);
        final ProgressBar posterProgressBar = findViewById(R.id.activityGameLoader_poster_progressBar);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loader.populateImageView(app, posterView, posterProgressBar);
                loader.populateImageViewSoft(app, bgImageViewStaticBlur, posterProgressBar, true);
            }
        });
    }


    private void bindService() {
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
//        blurLayout.startBlur();

    }

    @Override
    public void onBackPressed() {
        gameCancelled = true;

        if (blockingLoadSpinner != null) {
            blockingLoadSpinner.dismiss();
            blockingLoadSpinner = null;
        }

        Dialog.closeDialogs();

        if (managerBinder != null) {
            managerBinder.stopPolling();
            unbindService(serviceConnection);
            managerBinder = null;
        }
        finish();
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        blurLayout.pauseBlur();

        if (blockingLoadSpinner != null) {
            blockingLoadSpinner.dismiss();
            blockingLoadSpinner = null;
        }

        Dialog.closeDialogs();

        if (managerBinder != null) {
            managerBinder.stopPolling();
            unbindService(serviceConnection);
            managerBinder = null;
        }
        finish();
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        setStatusMessage("Waking up PC");
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(ShortcutTrampoline.this, getResources().getString(R.string.wol_pc_online),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(ShortcutTrampoline.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
            }
        }).start();
    }
}
