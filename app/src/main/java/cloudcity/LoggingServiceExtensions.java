package cloudcity;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import cloudcity.networking.CloudCityHelpers;
import cloudcity.networking.models.MeasurementsModel;
import cloudcity.networking.models.MobileSignalNetworkDataModel;
import cloudcity.networking.models.NetworkDataModel;
import cloudcity.networking.models.NetworkDataModelRequest;
import cloudcity.util.CellUtil;
import cloudcity.util.CloudCityLogger;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.CellInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class LoggingServiceExtensions {
    private static final String TAG = "LoggingServiceExtensions";

    private static Handler CloudCityHandler;
    private static SharedPreferences sp;

    private static GlobalVars gv;

    private static int interval;

    private static volatile DataProvider dp;

    private static HandlerThread handlerThread;

    private static AtomicBoolean isUpdating = new AtomicBoolean(false);

    // Handle remote Cloud City update
    private final static Runnable CloudCityUpdate = new Runnable() {
        @Override
        public void run() {
            try {
                CloudCityLogger.d(TAG, "run: CC Update");
                interval = Integer.parseInt(sp.getString("logging_interval", "1000"));
                String address = CloudCityParamsRepository.getInstance().getServerUrl();
                String token = CloudCityParamsRepository.getInstance().getServerToken();

                NetworkDataModel data = getCloudCityData();
                CloudCityLogger.d(TAG, "getCloudCityData() returned "+data);
                if (data == null) {
                    CloudCityLogger.e(TAG, "run: Error in getting data from Cloud city, skipping sending");
                } else {
                    NetworkDataModelRequest requestData = new NetworkDataModelRequest();
                    requestData.add(data);

                    CloudCityLogger.d(TAG, "sending data at addr=" + address + ", token=" + token + ", interval=" + interval);

                    boolean status = CloudCityHelpers.sendData(address, token, requestData);

                    if (status) {
                        /* Data sent successfully indicate in status icon. */
                        gv.getLog_status().setColorFilter(Color.argb(255, 0, 255, 0));
                    } else {
                        gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
                    }
                }
            } catch (Exception e) {
                CloudCityLogger.e(TAG, "Exception happened! exception "+e, e);
            }

            CloudCityHandler.postDelayed(this, interval);
        }
    };

    public static void setupCloudCity(GlobalVars globalVars, int updateInterval, DataProvider dataProvider, SharedPreferencesGrouper spg) {
        setupCloudCity2(globalVars, updateInterval, dataProvider, spg.getSharedPreference(SPType.logging_sp));
    }

    /**
     * initialize a new remote Cloud City connection
     */
    public static void setupCloudCity2(GlobalVars globalVars, int updateInterval, @NonNull DataProvider dataProvider, SharedPreferences sharedPrefs) {
        CloudCityLogger.d(TAG, "setupCloudCity");

        gv = globalVars;
        interval = updateInterval;
        dp = dataProvider;
        sp = sharedPrefs;

        /* Create CC API instance. */
        handlerThread = new HandlerThread("CloudCityHandlerThread");
        handlerThread.start();
        CloudCityHandler = new Handler(Objects.requireNonNull(handlerThread.getLooper()));
        if (isUpdating.compareAndSet(false, true)) {
            CloudCityHandler.post(CloudCityUpdate);
        }
        ImageView log_status = gv.getLog_status();
        if (log_status != null) {
            gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
        }

        // And finally, update all data providers
        if (dp != null) {
            // This has to be done since i'm getting crashes due to this 'dp' being null after the app
            // works for a while.
            dp.refreshAll();
        } else {
            CloudCityLogger.e(TAG, "DataProvider was null! Didn't refreshAll() internal data caches.");
        }
    }

    /**
     * stop remote influx logging in clear up all internal instances of involved objects
     */
    public static void stopCloudCity() {
        CloudCityLogger.d(TAG, "stopCloudCity");
        // cleanup the handler is existing
        if (CloudCityHandler != null) {
            try {
                CloudCityHandler.removeCallbacks(CloudCityUpdate);
                boolean unset = isUpdating.compareAndSet(true, false);
                if(!unset) {
                    CloudCityLogger.e(TAG, "There was a problem with updating 'isUpdating', expected 'true' but was 'false' instead");
                }
            } catch (NullPointerException e) {
                CloudCityLogger.d(TAG, "trying to stop cloud city service while it was not running");
            }
        }

        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                CloudCityLogger.e(TAG, "Exception happened!! "+e, e);
            }
        }

        gv.getLog_status().setColorFilter(Color.argb(255, 192, 192, 192));
    }

    private static NetworkDataModel getCloudCityData() {
        if (dp == null) {
            CloudCityLogger.e(TAG, "DataProvider was null! Bailing out, returning null...");
            return null;
        }
        List<CellInformation> cellsInfo = dp.getCellInformation();
        List<CellInformation> signalInfo = dp.getSignalStrengthInformation();

        CellInformation currentCell = CellUtil.findRegisteredCell(cellsInfo);
        CellInformation currentSignal = null;
        // After testing on a real phone, apparently there's only one SignalStrengthInformation
        // CellInformation member in the list, so lets just take the first one if it's there
        if (!signalInfo.isEmpty()) {
            currentSignal = signalInfo.get(0);
        }

        // Lets initialize our MeasurementModel for sending from the registered cell model, then overwrite it's values
        // with what we found in the SignalInformation
        MeasurementsModel modelForSending = CellUtil.getMeasurementsModel(currentCell, currentSignal, CellUtil.CellInfoPrecedence.SIGNAL_INFO);

        Location location = GPSMonitor.getLastLocation();

        MobileSignalNetworkDataModel dataModel = new MobileSignalNetworkDataModel(
                location,
                modelForSending
        );

        String category = currentCell != null ? currentCell.getCellType().toString() : "UNKNOWN";
        dataModel.setCategory(category);
        dataModel.setAccuracy(location.getAccuracy());
        /* Convert to km/h */
        dataModel.setSpeed(location.getSpeed() * 3.6);

        dataModel.setCellData(
                CellUtil.getCellInfoModel(currentCell)
        );

        return dataModel;
    }
}
