/*
 *  SPDX-FileCopyrightText: 2024 Peter Hasse <peter.hasse@fokus.fraunhofer.de>
 *  SPDX-FileCopyrightText: 2024 Johann Hackler <johann.hackler@fokus.fraunhofer.de>
 *  SPDX-FileCopyrightText: 2024 Fraunhofer FOKUS
 *
 *  SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package de.fraunhofer.fokus.OpenMobileNetworkToolkit;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import cloudcity.CloudCityConstants;
import cloudcity.LoggingServiceExtensions;
import cloudcity.util.CloudCityLogger;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.WifiInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.InfluxDB2x.InfluxdbConnection;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.InfluxDB2x.InfluxdbConnections;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class LoggingService extends Service {
    private static final String TAG = "Logging_Service";
    public NotificationManager nm;
    NotificationCompat.Builder builder;
    InfluxdbConnection ic; // remote influxDB
    InfluxdbConnection lic; // local influxDB
    DataProvider dp;
    SharedPreferencesGrouper spg;
    private Handler notificationHandler;
    private HandlerThread notificationHandlerThread;
    private Handler remoteInfluxHandler;
    private HandlerThread remoteInfluxHandlerThread;
    private Handler localInfluxHandler;
    private HandlerThread localInfluxHandlerThread;
    private Handler localFileHandler;
    private HandlerThread localFileHandlerThread;
    private List<Point> logFilePoints;
    private FileOutputStream stream;
    private int interval;
    GlobalVars gv;
    // Handle local on-device logging to logfile
    private final Runnable localFileUpdate = new Runnable() {
        @Override
        public void run() {
            logFilePoints.addAll(getPoints());
            if (logFilePoints.size() >= 100) {
                for (Point point : logFilePoints) {
                    try {
                        stream.write((point.toLineProtocol() + "\n").getBytes());
                    } catch (IOException e) {
                        CloudCityLogger.e(TAG,e.toString(), e);
                    }
                }
                logFilePoints.clear();
                try {
                    stream.flush();
                } catch (IOException e) {
                    CloudCityLogger.e(TAG,e.toString(), e);
                }
            }
            localFileHandler.postDelayed(this, interval);
        }
    };
    // Handle notification bar update
    private final Runnable notification_updater = new Runnable() {
        @SuppressLint("ObsoleteSdkInt")
        @Override
        public void run() {
            if(dp == null) {
                CloudCityLogger.e(TAG, "run: Data provider is null!");
                return;
            }
            StringBuilder s = dp.getRegisteredCells().get(0).getStringBuilder();
            builder.setContentText(s);
            nm.notify(1, builder.build());
            notificationHandler.postDelayed(this, interval);
        }
    };

    // Handle local on-device influxDB
    private final Runnable localInfluxUpdate = () -> {
/*            gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
            //long ts = System.currentTimeMillis();
            // write network information
            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_network_data", false)) {
                return;
            }
            // write signal strength information
            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_signal_data", false)) { // user settings here
                return;
            }
            // write cell information
            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_cell_data", false)) {

            }
            //always add location information
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ic.writePoints(new ArrayList<>(Collections.singleton(dp.getLocationPoint())));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            remoteInfluxHandler.postDelayed(this, interval);*/
    };

    // Handle remote on-server influxdb update
    private final Runnable RemoteInfluxUpdate = new Runnable() {
        @Override
        public void run() {
            try {
                ic.writePoints(getPoints());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ic.flush();
            remoteInfluxHandler.postDelayed(this, interval);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        CloudCityLogger.d(TAG, "onCreate: Logging service created");
        gv = GlobalVars.getInstance();
    }

    @SuppressLint("ObsoleteSdkInt")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        CloudCityLogger.d(TAG, "onStartCommand: Start logging service");
        GlobalVars gv = GlobalVars.getInstance();

        // setup class variables
        dp = gv.get_dp();
        nm = getSystemService(NotificationManager.class);
        spg = SharedPreferencesGrouper.getInstance(this);
        interval = Integer.parseInt(spg.getSharedPreference(SPType.logging_sp).getString("logging_interval", "1000"));

        // create intent for notifications
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // create notification
            builder = new NotificationCompat.Builder(this, "OMNT_notification_channel").setContentTitle(getText(R.string.loggin_notifaction)).setSmallIcon(R.mipmap.ic_launcher_foreground).setColor(Color.WHITE).setContentIntent(pendingIntent)
                    // prevent to swipe the notification away
                    .setOngoing(true)
                    // don't wait 10 seconds to show the notification
                    .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        } else {
            // create notification
            builder = new NotificationCompat.Builder(this, "OMNT_notification_channel").setContentTitle(getText(R.string.loggin_notifaction)).setSmallIcon(R.mipmap.ic_launcher_foreground).setColor(Color.WHITE).setContentIntent(pendingIntent)
                    // prevent to swipe the notification away
                    .setOngoing(true);
        }

        // create preferences listener
        spg.setListener((prefs, key) -> {
            if (Objects.equals(key, "enable_influx")) {
                if (prefs.getBoolean(key, false)) {
                    if (prefs.getString("influx_URL", "").isEmpty() || prefs.getString("influx_org", "").isEmpty() || prefs.getString("influx_token", "").isEmpty() || prefs.getString("influx_bucket", "").isEmpty()) {
                        CloudCityLogger.i(TAG, "Not all influx settings are present in preferences");
                        Toast.makeText(getApplicationContext(), "Please fill all Influx Settings", Toast.LENGTH_LONG).show();
                        prefs.edit().putBoolean("enable_influx", false).apply();
                    } else {
                        setupRemoteInfluxDB();
                    }
                } else {
                    stopRemoteInfluxDB();
                }
            } else if (Objects.equals(key, "enable_cloud_city")) {
                if (prefs.getBoolean(key, false)) {
                    if (prefs.getString(CloudCityConstants.CLOUD_CITY_SERVER_URL, "").isEmpty() || prefs.getString(CloudCityConstants.CLOUD_CITY_TOKEN, "").isEmpty()) {
                        CloudCityLogger.i(TAG, "Not all Cloud City settings are present in preferences");
                        Toast.makeText(getApplicationContext(), "Please fill all Cloud City Settings", Toast.LENGTH_LONG).show();
                        prefs.edit().putBoolean("enable_cloud_city", false).apply();
                    } else {
                        LoggingServiceExtensions.setupCloudCity(gv, interval, dp, spg);
                    }
                } else {
                    LoggingServiceExtensions.stopCloudCity();
                }
            } else if (Objects.equals(key, "enable_notification_update")) {
                if (prefs.getBoolean(key, false)) {
                    setupNotificationUpdate();
                } else {
                    stopNotificationUpdate();
                }
            } else if (Objects.equals(key, "enable_local_file_log")) {
                if (prefs.getBoolean(key, false)) {
                    setupLocalFile();
                } else {
                    stopLocalFile();
                }
            } else if (Objects.equals(key, "enable_local_influx_log")) {
                if (prefs.getBoolean(key, false)) {
                    setupLocalInfluxDB();
                } else {
                    stopLocalInfluxDB();
                }
            } else if (Objects.equals(key, "logging_interval")) {
                interval = Integer.parseInt(spg.getSharedPreference(SPType.logging_sp).getString("logging_interval", "1000"));
            }
        }, SPType.logging_sp);

        // Start foreground service and setup logging targets
        startForeground(1, builder.build());

        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_notification_update", false)) {
            setupNotificationUpdate();
        }

        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_influx", false)) {
            setupRemoteInfluxDB();
        }

        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_cloud_city", false)) {
            LoggingServiceExtensions.setupCloudCity(gv, interval, dp, spg);
        }

        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_local_file_log", false)) {
            setupLocalFile();
        }

        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_local_influx_log", false)) {
            setupLocalFile();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CloudCityLogger.d(TAG, "onDestroy: Stop logging service");
        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_influx", false)) {
            stopRemoteInfluxDB();
        }

        if (spg.getSharedPreference(SPType.default_sp).getBoolean("enable_cloud_city", false)){
            LoggingServiceExtensions.stopCloudCity();
        }

        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_local_file_log", false)) {
            stopLocalFile();
        }
        if (spg.getSharedPreference(SPType.logging_sp).getBoolean("enable_local_influx_log", false)) {
            stopLocalInfluxDB();
        }

        // Stop foreground service and remove the notification.
        stopForeground(STOP_FOREGROUND_DETACH);
        // Stop the foreground service.
        stopSelf();
    }

    private ArrayList<Point> getPoints() {
        long time = System.currentTimeMillis();
        ArrayList<Point> logPoints = new ArrayList<>();
        if (dp != null) {
            Map<String, String> tags_map = dp.getTagsMap();

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_network_data", false)) {
                Point p = dp.getNetworkInformationPoint();
                if (p.hasFields()) {
                    p.time(time, WritePrecision.MS);
                    p.addTags(tags_map);
                    logPoints.add(p);
                } else {
                    CloudCityLogger.w(TAG, "Point without fields from getNetworkInformationPoint");
                }
            }

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_throughput_data", false)) {
                Point p = dp.getNetworkCapabilitiesPoint();
                if (p.hasFields()) {
                    p.time(time, WritePrecision.MS);
                    p.addTags(tags_map);
                    logPoints.add(p);
                } else {
                    CloudCityLogger.w(TAG, "Point without fields from getNetworkCapabilitiesPoint");
                }
            }

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("log_signal_data", false)) {
                Point p = dp.getSignalStrengthPoint();
                if (p.hasFields()) {
                    p.time(time, WritePrecision.MS);
                    p.addTags(tags_map);
                    logPoints.add(p);
                } else {
                    CloudCityLogger.w(TAG, "Point without fields from getSignalStrengthPoint");
                }
            }

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("log_wifi_data", false)) {
                WifiInformation wifiInformation = dp.getWifiInformation();
                if (wifiInformation != null) {
                    Point p = wifiInformation.getWifiInformationPoint();
                    if (p.hasFields()) {
                        p.time(time, WritePrecision.MS);
                        p.addTags(tags_map);
                        logPoints.add(p);
                    } else {
                        CloudCityLogger.w(TAG, "Point without fields from getWifiInformationPoint");
                    }
                }
            }

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_cell_data", false)) {
                List<Point> ps = dp.getCellInformationPoint();
                for (Point p : ps) {
                    if (p.hasFields()) {
                        p.time(time, WritePrecision.MS);
                        p.addTags(tags_map);
                    } else {
                        CloudCityLogger.w(TAG, "Point without fields from getCellInformationPoint");
                    }
                }
                logPoints.addAll(ps);
            }

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_ip_address_data", false)) {
                List<Point> ps = dp.getNetworkInterfaceInformationPoints();
                for (Point p : ps) {
                    if (p.hasFields()) {
                        p.time(time, WritePrecision.MS);
                        p.addTags(tags_map);
                    } else {
                        CloudCityLogger.w(TAG, "Point without fields from getNetworkInterfaceInformationPoints");
                    }
                }
                logPoints.addAll(ps);
            }

            if (spg.getSharedPreference(SPType.logging_sp).getBoolean("influx_battery_data", false)) {
                Point bp = dp.getBatteryInformationPoint();
                bp.time(time, WritePrecision.MS);
                bp.addTags(tags_map);
                logPoints.add(bp);
            }

            Point p = dp.getLocationPoint();
            p.time(time, WritePrecision.MS);
            p.addTags(tags_map);
            logPoints.add(p);
        } else {
            CloudCityLogger.w(TAG,"data provider not initialized, generating empty point");
        }
        return logPoints;
    }

    private void setupLocalFile() {
        CloudCityLogger.d(TAG, "setupLocalFile");
        logFilePoints = new ArrayList<>();

        // build log file path
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + "/omnt/log/";
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // create the log file
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
        Date now = new Date();
        String filename = path + formatter.format(now) + ".txt";
        CloudCityLogger.d(TAG, "logfile: " + filename);
        File logfile = new File(filename);
        try {
            boolean file_not_exists = logfile.createNewFile();
            if (!file_not_exists) {
                logfile = new File(filename + "_1");
                file_not_exists = logfile.createNewFile();
            }
            if (!file_not_exists) {
                CloudCityLogger.d(TAG, "can't create logfile " + logfile + " event after file rename");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // get an output stream
        try {
            stream = new FileOutputStream(logfile);
        } catch (FileNotFoundException e) {
            Toast.makeText(getApplicationContext(), "logfile not created", Toast.LENGTH_SHORT).show();
            CloudCityLogger.e(TAG,e.toString(), e);
        }

        initLocalFileHandlerAndItsThread();
    }

    private void stopLocalFile() {
        CloudCityLogger.d(TAG, "stopLocalFile");
        if (localFileHandler != null) {
            try {
                stream.close();
                localFileHandler.removeCallbacks(localFileUpdate);
            } catch (java.lang.NullPointerException e) {
                CloudCityLogger.e(TAG, "trying to stop local file service while it was not running", e);
            } catch (IOException e) {
                CloudCityLogger.e(TAG,e.toString(), e);
            }
        }

        if (localFileHandlerThread != null) {
            localFileHandlerThread.quitSafely();
            try {
                localFileHandlerThread.join();
            } catch (InterruptedException e) {
                CloudCityLogger.e(TAG, "Exception happened!! "+e, e);
            }
        }
    }

    private void setupNotificationUpdate() {
        CloudCityLogger.d(TAG, "setupNotificationUpdate");
        notificationHandlerThread = new HandlerThread("NotificationHandlerThread");
        notificationHandlerThread.start();
        notificationHandler = new Handler(Objects.requireNonNull(notificationHandlerThread.getLooper()));
        notificationHandler.post(notification_updater);
    }

    private void stopNotificationUpdate() {
        CloudCityLogger.d(TAG, "stopNotificationUpdate");
        notificationHandler.removeCallbacks(notification_updater);
        builder.setContentText(null);
        nm.notify(1, builder.build());

        if (notificationHandlerThread != null) {
            notificationHandlerThread.quitSafely();
            try {
                notificationHandlerThread.join();
            } catch (InterruptedException e) {
                CloudCityLogger.e(TAG, "Exception happened!! "+e, e);
            }
            notificationHandlerThread = null;
        }
    }

    private void setupLocalInfluxDB() {
        CloudCityLogger.d(TAG, "setupLocalInfluxDB");
        lic = InfluxdbConnections.getLicInstance(getApplicationContext());
        Objects.requireNonNull(lic).open_write_api();
        localInfluxHandlerThread = new HandlerThread("LocalInfluxHandlerThread");
        localInfluxHandlerThread.start();
        localInfluxHandler = new Handler(Objects.requireNonNull(localFileHandlerThread.getLooper()));
        localInfluxHandler.post(localInfluxUpdate);
    }

    private void stopLocalInfluxDB() {
        CloudCityLogger.d(TAG, "stopLocalInfluxDB");
        if (localInfluxHandler != null) {
            try {
                localInfluxHandler.removeCallbacks(RemoteInfluxUpdate);
            } catch (java.lang.NullPointerException e) {
                CloudCityLogger.e(TAG, "trying to stop local influx service while it was not running", e);
            }
        }
        if (lic != null) {
            lic.disconnect();
        }
        if (localInfluxHandlerThread != null) {
            localInfluxHandlerThread.quitSafely();
            try {
                localInfluxHandlerThread.join();
            } catch (InterruptedException e) {
                CloudCityLogger.e(TAG, "Exception happened!! "+e, e);
            }
            localInfluxHandlerThread = null;
        }
    }

    /**
     * initialize a new remote influxDB connection
     */
    private void setupRemoteInfluxDB() {
        CloudCityLogger.d(TAG, "setupRemoteInfluxDB");
        ic = InfluxdbConnections.getRicInstance(getApplicationContext());
        Objects.requireNonNull(ic).open_write_api();
        remoteInfluxHandlerThread = new HandlerThread("RemoteInfluxHandlerThread");
        remoteInfluxHandlerThread.start();
        remoteInfluxHandler = new Handler(Objects.requireNonNull(remoteInfluxHandlerThread.getLooper()));
        remoteInfluxHandler.post(RemoteInfluxUpdate);
        ImageView log_status = gv.getLog_status();
        if (log_status != null) {
            gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
        }
    }

    /**
     * stop remote influx logging in clear up all internal instances of involved objects
     */
    private void stopRemoteInfluxDB() {
        CloudCityLogger.d(TAG, "stopRemoteInfluxDB");
        // cleanup the handler is existing
        if (remoteInfluxHandler != null) {
            try {
                remoteInfluxHandler.removeCallbacks(RemoteInfluxUpdate);
            } catch (java.lang.NullPointerException e) {
                CloudCityLogger.d(TAG, "trying to stop remote influx service while it was not running");
            }
        }

        if (remoteInfluxHandlerThread != null) {
            remoteInfluxHandlerThread.quitSafely();
            try {
                remoteInfluxHandlerThread.join();
            } catch (InterruptedException e) {
                CloudCityLogger.e(TAG, "Exception happened!! "+e, e);
            }
            remoteInfluxHandlerThread = null;
        }

        // close disconnect influx connection if existing
        if (ic != null) {
            ic.disconnect();
            ic = null;
        }

        // remove reference in connection manager
        InfluxdbConnections.removeRicInstance();
        gv.getLog_status().setColorFilter(Color.argb(255, 192, 192, 192));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initLocalFileHandlerAndItsThread() {
        localFileHandlerThread = new HandlerThread("LocalFileHandlerThread");
        localFileHandlerThread.start();
        localFileHandler = new Handler(Objects.requireNonNull(localFileHandlerThread.getLooper()));
        localFileHandler.post(localFileUpdate);
    }
}
