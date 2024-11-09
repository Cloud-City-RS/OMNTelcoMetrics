package cloudcity;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.CellInfo;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import cloudcity.networking.CloudCityHelpers;
import cloudcity.networking.models.CellInfoModel;
import cloudcity.networking.models.MeasurementsModel;
import cloudcity.networking.models.MobileSignalNetworkDataModel;
import cloudcity.networking.models.NetworkDataModel;
import cloudcity.networking.models.NetworkDataModelRequest;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.CellInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.GSMInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.LTEInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.NRInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.LocationInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;

public class LoggingServiceExtensions {
    private static final String TAG = "LoggingServiceExtensions";

    private static Handler CloudCityHandler;
    private static SharedPreferences sp;

    private static GlobalVars gv;

    private static int interval;

    private static DataProvider dp;

    private static HandlerThread handlerThread;

    private static AtomicBoolean isUpdating = new AtomicBoolean(false);

    // Handle remote Cloud City update
    private final static Runnable CloudCityUpdate = new Runnable() {
        @Override
        public void run() {
            try {
                Log.d(TAG, "run: CC Update");
                interval = Integer.parseInt(sp.getString("logging_interval", "1000"));
                String address = CloudCityParamsRepository.getInstance().getServerUrl();
                String token = CloudCityParamsRepository.getInstance().getServerToken();

                NetworkDataModel data = getCloudCityData();
                Log.d(TAG, "getCloudCityData() returned "+data);
                if (data == null) {
                    Log.e(TAG, "run: Error in getting data from Cloud city, skipping sending");
                } else {
                    NetworkDataModelRequest requestData = new NetworkDataModelRequest();
                    requestData.add(data);

                    Log.d(TAG, "sending data at addr=" + address + ", token=" + token + ", interval=" + interval);

                    boolean status = CloudCityHelpers.sendData(address, token, requestData);

                    if (status) {
                        /* Data sent successfully indicate in status icon. */
                        gv.getLog_status().setColorFilter(Color.argb(255, 0, 255, 0));
                    } else {
                        gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception happened! exception "+e, e);
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
        Log.d(TAG, "setupCloudCity");

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
            Log.e(TAG, "DataProvider was null! Didn't refreshAll() internal data caches.");
        }
    }

    /**
     * stop remote influx logging in clear up all internal instances of involved objects
     */
    public static void stopCloudCity() {
        Log.d(TAG, "stopCloudCity");
        // cleanup the handler is existing
        if (CloudCityHandler != null) {
            try {
                CloudCityHandler.removeCallbacks(CloudCityUpdate);
                boolean unset = isUpdating.compareAndSet(true, false);
                if(!unset) {
                    Log.e(TAG, "There was a problem with updating 'isUpdating', expected 'true' but was 'false' instead");
                }
            } catch (java.lang.NullPointerException e) {
                Log.d(TAG, "trying to stop cloud city service while it was not running");
            }
        }

        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception happened!! "+e, e);
            }
        }

        gv.getLog_status().setColorFilter(Color.argb(255, 192, 192, 192));
    }

    private static NetworkDataModel getCloudCityData() {
        if (dp == null) {
            Log.e(TAG, "DataProvider was null! Bailing out, returning null...");
            return null;
        }
        List<CellInformation> cellsInfo = dp.getCellInformation();
        List<CellInformation> signalInfo = dp.getSignalStrengthInformation();
        LocationInformation location = dp.getLocation();

        CellInformation currentCell = findRegisteredCell(cellsInfo);
        CellInformation currentSignal = null;
        // After testing on a real phone, apparently there's only one SignalStrengthInformation
        // CellInformation member in the list, so lets just take the first one if it's there
        if (!signalInfo.isEmpty()) {
            currentSignal = signalInfo.get(0);
        }

        String category = currentCell.getCellType().toString();

        // Lets initialize our MeasurementModel for sending from the registered cell model, then overwrite it's values
        // with what we found in the SignalInformation
        MeasurementsModel modelForSending = getMeasurementsModel(category, currentCell);
        updateMeasurementModelByCell(modelForSending, currentSignal);

        MobileSignalNetworkDataModel dataModel = new MobileSignalNetworkDataModel(
                location,
                modelForSending
        );

        dataModel.setCategory(currentCell.getCellType().toString());
        dataModel.setAccuracy(location.getAccuracy());
        /* Convert to km/h */
        dataModel.setSpeed(location.getSpeed() * 3.6);

        dataModel.setCellData(getCellInfoModel(category, currentCell));

        return dataModel;
    }

    /**
     * Finds a registered cell information in a list of cell informations
     * @param cellList
     * @return the first registered cell in the list, or null if no registered cells were found
     */
    public static CellInformation findRegisteredCell(@NonNull List<CellInformation> cellList) {
        CellInformation retVal = null;

        for (CellInformation ci: cellList) {
            if (!ci.isRegistered()) {
                continue;
            }

            retVal = ci;
        }

        return retVal;
    }

    private static @NonNull CellInfoModel getCellInfoModel(String category, CellInformation currentCell) {
        CellInfoModel cellInfoModel = new CellInfoModel();

        if (Objects.equals(category, "CDMA") || Objects.equals(category, "GSM")) {
            /* No information in 2G and 3G set dummy data. */
            cellInfoModel.setDummy(1);
        } else {
            /* Real data available for all other network types. */
            if (currentCell instanceof GSMInformation) {
                GSMInformation gsmCell = (GSMInformation) currentCell;
                // Ok so bands is technically the ARFCN
                String bandsString = gsmCell.getBands();
                int arfcn = Integer.parseInt(bandsString);
                cellInfoModel.setEarfcn(arfcn);
                cellInfoModel.setPci(currentCell.getPci());
            }
        }

        long id = currentCell.getCi();

        if (Objects.equals(category, "NR")) {
            if (id != CellInfo.UNAVAILABLE_LONG) {
                cellInfoModel.setCellId(currentCell.getCi());
            }
        } else if (Objects.equals(category, "LTE")) {
            if (id != CellInfo.UNAVAILABLE) {
                cellInfoModel.setCellId((int)(currentCell.getCi() >> 8));
                cellInfoModel.seteNodeBId((int) currentCell.getCi() & 0x000000FF);
            }

        }

        return cellInfoModel;
    }

    private static @NonNull MeasurementsModel getMeasurementsModel(String category, CellInformation currentCell) {
        MeasurementsModel measurements = new MeasurementsModel();

        if (Objects.equals(category, "NR")) {
            // New safety
            if (currentCell instanceof NRInformation) {
                NRInformation nrCell = (NRInformation) currentCell;
                measurements.setCsirsrp(nrCell.getCsirsrp());
                measurements.setCsirsrq(nrCell.getCsirsrq());
                measurements.setCsisinr(nrCell.getCsisinr());
                measurements.setSsrsrp(nrCell.getSsrsrp());
                measurements.setSsrsrq(nrCell.getSsrsrq());
                measurements.setSssinr(nrCell.getSssinr());
            }
        } else if (Objects.equals(category, "LTE")) {
            if (currentCell instanceof LTEInformation) {
                LTEInformation lteCell = (LTEInformation) currentCell;
                measurements.setRsrp(lteCell.getRsrp());
                measurements.setRsrq(lteCell.getRsrq());
                measurements.setRssnr(lteCell.getRssnr());
            }
        } else {
            /* In 3G no measurement data available set dummy data. */
            measurements.setDummy(1);
        }
        return measurements;
    }

    private static void updateMeasurementModelByCell(@NonNull MeasurementsModel measurements, @NonNull CellInformation cellForUpdating) {
        if (cellForUpdating instanceof NRInformation) {
            NRInformation nrCell = (NRInformation) cellForUpdating;
            measurements.setCsirsrp(nrCell.getCsirsrp());
            measurements.setCsirsrq(nrCell.getCsirsrq());
            measurements.setCsisinr(nrCell.getCsisinr());
            measurements.setSsrsrp(nrCell.getSsrsrp());
            measurements.setSsrsrq(nrCell.getSsrsrq());
            measurements.setSssinr(nrCell.getSssinr());
        }

        if (cellForUpdating instanceof LTEInformation) {
            LTEInformation lteCell = (LTEInformation) cellForUpdating;
            measurements.setRsrp(lteCell.getRsrp());
            measurements.setRsrq(lteCell.getRsrq());
            measurements.setRssnr(lteCell.getRssnr());
        }
    }
}
