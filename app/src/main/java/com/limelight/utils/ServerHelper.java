package com.limelight.utils;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.limelight.AppView;
import com.limelight.Game;
import com.limelight.GameLauncher;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParserException;

public class ServerHelper {

    public static List<QuitAppListener> quitAppListeners = new ArrayList<>();

    public static String getCurrentAddressFromComputer(ComputerDetails computer) {
        return computer.activeAddress;
    }

    public static Intent createPcShortcutIntent(Activity parent, ComputerDetails computer) {
        Intent i = new Intent(parent, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.setAction(Intent.ACTION_DEFAULT);
        return i;
    }

    public static Intent createAppShortcutIntent(Activity parent, ComputerDetails computer, NvApp app) {
        Intent i = new Intent(parent, GameLauncher.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        i.putExtra(Game.EXTRA_APP_ID, "" + app.getAppId());
        i.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        i.setAction(Intent.ACTION_DEFAULT);
        return i;
    }

    public static Intent createStartIntent(Activity parent, NvApp app, ComputerDetails computer,
            ComputerManagerService.ComputerManagerBinder managerBinder) {
        Intent intent = new Intent(parent, Game.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(Game.EXTRA_HOST, getCurrentAddressFromComputer(computer));
        intent.putExtra(Game.EXTRA_APP, app);
        intent.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        intent.putExtra(Game.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(Game.EXTRA_COMPUTER, computer);
        intent.putExtra(Game.EXTRA_PC_UUID, computer.uuid);
        intent.putExtra(Game.EXTRA_PC_NAME, computer.name);
        try {
            if (computer.serverCert != null) {
                intent.putExtra(Game.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        }
        return intent;
    }

    public static Intent createSharedElementStartIntent(Activity parent, NvApp app, ComputerDetails computer,
            ComputerManagerService.ComputerManagerBinder managerBinder, View sharedElement) {
        Intent intent = new Intent(parent, Game.class);
        intent.putExtra(Game.EXTRA_HOST, getCurrentAddressFromComputer(computer));
        intent.putExtra(Game.EXTRA_APP, app);
        intent.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        intent.putExtra(Game.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(Game.EXTRA_COMPUTER, computer);
        intent.putExtra(Game.EXTRA_PC_UUID, computer.uuid);
        intent.putExtra(Game.EXTRA_PC_NAME, computer.name);
        try {
            if (computer.serverCert != null) {
                intent.putExtra(Game.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        }
        return intent;
    }

    public static void doStart(Activity parent, NvApp app, ComputerDetails computer,
            ComputerManagerService.ComputerManagerBinder managerBinder) {
        /**
         * Removed below OFFLINE check as Shortcut Trampoline (started by createAppShortcutIntent) already checks.
         */
//        if (computer.state == ComputerDetails.State.OFFLINE ||
//                ServerHelper.getCurrentAddressFromComputer(computer) == null) {
//            Toast.makeText(parent, parent.getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
//            return;
//        }
        parent.startActivity(createAppShortcutIntent(parent, computer, app));
    }

    public static void doQuit(final Activity parent,
            final ComputerDetails computer,
            final NvApp app,
            final ComputerManagerService.ComputerManagerBinder managerBinder,
            final Runnable onComplete) {
        Toast.makeText(parent,
                parent.getResources().getString(R.string.applist_quit_app) + " " + app.getAppName() + "...",
                Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    Log.d("ServerHelper", "managerBinder uuid: " + managerBinder.getUniqueId());
                    Log.d("ServerHelper", "server address: " + ServerHelper.getCurrentAddressFromComputer(computer));
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(parent));
                    if (httpConn.quitApp()) {
                        message = parent.getResources().getString(R.string.applist_quit_success) + " " + app
                                .getAppName();
                    } else {
                        message = parent.getResources().getString(R.string.applist_quit_fail) + " " + app
                                .getAppName();
                    }
                } catch (GfeHttpResponseException e) {
                    if (e.getErrorCode() == 599) {
                        message = "This session wasn't started by this device," +
                                " so it cannot be quit. End streaming on the original " +
                                "device or the PC itself. (Error code: " + e.getErrorCode() + ")";
                    } else {
                        message = e.getMessage();
                    }
                } catch (UnknownHostException e) {
                    message = parent.getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = parent.getResources().getString(R.string.error_404);
                } catch (IOException | XmlPullParserException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                } finally {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                final String toastMessage = message;
                parent.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(parent, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    public static void doQuit(final Activity parent,
            final ComputerDetails computer,
            final NvApp app,
            final String managerBinderUuid,
            final Runnable onComplete) {

        for (QuitAppListener listener : quitAppListeners) {
            listener.onQuitApp(true);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    Log.d("ServerHelper", "managerBinder uuid: " + managerBinderUuid);
                    Log.d("ServerHelper", "server address: " + ServerHelper.getCurrentAddressFromComputer(computer));
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            managerBinderUuid, computer.serverCert, PlatformBinding.getCryptoProvider(parent));
                    if (httpConn.quitApp()) {
                        message = "";
                    } else {
                        message = parent.getResources().getString(R.string.applist_quit_fail) + " " + app
                                .getAppName();
                    }
                } catch (GfeHttpResponseException e) {
                    if (e.getErrorCode() == 599) {
                        message = "This session wasn't started by this device," +
                                " so it cannot be quit. End streaming on the original " +
                                "device or the PC itself. (Error code: " + e.getErrorCode() + ")";
                    } else {
                        message = e.getMessage();
                    }
                } catch (UnknownHostException e) {
                    message = parent.getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = parent.getResources().getString(R.string.error_404);
                } catch (IOException | XmlPullParserException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                } finally {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                final String toastMessage = message;
                parent.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!toastMessage.isEmpty()) {
                            Toast.makeText(parent, toastMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    public static void addQuitAppListener(QuitAppListener listener) {
        quitAppListeners.add(listener);
    }

    public interface QuitAppListener {

        public void onQuitApp(Boolean success);
    }
}
