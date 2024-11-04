package cloudcity;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.CellInfo;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.CellInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.GSMInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.LTEInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.NRInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.LocationInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.GlobalVars;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SPType;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Preferences.SharedPreferencesGrouper;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.CloudCityHelpers;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.models.CellInfoModel;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.models.MeasurementsModel;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.models.NetworkDataModel;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.cloudCity.models.NetworkDataModelRequest;

public class LoggingServiceExtensions {
    private static final String TAG = "LoggingServiceExtensions";

    private static Handler CloudCityHandler;
    private static SharedPreferences sp;

    private static GlobalVars gv;

    private static int interval;

    private static DataProvider dp;

    private static HandlerThread handlerThread;

    // Handle remote Cloud City update
    private final static Runnable CloudCityUpdate = new Runnable() {
        @Override
        public void run() {
            try {
                Log.d(TAG, "run: CC Update");

                String address = sp.getString(CloudCityConstants.CLOUD_CITY_SERVER_URL, "");
                if (address.isEmpty() || CloudCityUtil.isBlank(address)) {
                    address = CloudCityParamsRepository.getInstance().getServerUrl();
                }
                String token = sp.getString(CloudCityConstants.CLOUD_CITY_TOKEN, "");
                if (token.isEmpty() || CloudCityUtil.isBlank(token)) {
                    token = CloudCityParamsRepository.getInstance().getServerToken();
                }

                NetworkDataModel data = getCloudCityData();
                Log.d(TAG, "getCloudCityData() returned "+data);
                if (data == null) {
                    Log.e(TAG, "run: Error in getting data from Cloud city, skipping sending");
                    CloudCityHandler.postDelayed(this, interval);
                    return;
                }
                NetworkDataModelRequest requestData = new NetworkDataModelRequest();
                requestData.add(data);

                boolean status = CloudCityHelpers.sendData(address, token, requestData);

                if (status) {
                    /* Data sent successfully indicate in status icon. */
                    gv.getLog_status().setColorFilter(Color.argb(255, 0, 255, 0));
                } else  {
                    gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception happened! exception "+e, e);
                throw new RuntimeException(e);
            }

            CloudCityHandler.postDelayed(this, interval);
        }
    };

    public static void setupCloudCity(GlobalVars globalVars, int updateInterval, DataProvider dataProvider, SharedPreferencesGrouper spg) {
        setupCloudCity2(globalVars, updateInterval, dataProvider, spg.getSharedPreference(SPType.default_sp));
    }

    /**
     * initialize a new remote Cloud City connection
     */
    public static void setupCloudCity2(GlobalVars globalVars, int updateInterval, DataProvider dataProvider, SharedPreferences sharedPrefs) {
        Log.d(TAG, "setupCloudCity");

        gv = globalVars;
        interval = updateInterval;
        dp = dataProvider;
        sp = sharedPrefs;

        /* Create CC API instance. */
        handlerThread = new HandlerThread("CloudCityHandlerThread");
        handlerThread.start();
        CloudCityHandler = new Handler(Objects.requireNonNull(handlerThread.getLooper()));
        CloudCityHandler.post(CloudCityUpdate);
        ImageView log_status = gv.getLog_status();
        if (log_status != null) {
            gv.getLog_status().setColorFilter(Color.argb(255, 255, 0, 0));
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
        List<CellInformation> cellsInfo = dp.getCellInformation();
        LocationInformation location = dp.getLocation();
        CellInformation currentCell = null;

        for (CellInformation ci: cellsInfo) {
            if (!ci.isRegistered()) {
                continue;
            }

            currentCell = ci;
        }

        if (currentCell == null) {
            return null;
        }

        String category = currentCell.getCellType().toString();

        NetworkDataModel dataModel = new NetworkDataModel();

        dataModel.setCategory(currentCell.getCellType().toString());
        dataModel.setLatitude(location.getLatitude());
        dataModel.setLongitude(location.getLongitude());
        dataModel.setAccuracy(location.getAccuracy());
        /* Convert to km/h */
        dataModel.setSpeed(location.getSpeed() * 3.6);

        dataModel.setCellData(getCellInfoModel(category, currentCell));
        dataModel.setValues(getMeasurementsModel(category, currentCell));

        return dataModel;
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
}
