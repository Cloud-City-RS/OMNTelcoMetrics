package cloudcity.util;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

import cloudcity.networking.models.MeasurementsModel;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.CDMAInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.CellInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.GSMInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.LTEInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.NRInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;

public class CellUtil {
    private static final String TAG = "CellUtil";

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

    /**
     * Extract various measurement data from the currently registered {@link CellInformation} as returned by {@link DataProvider}
     * @param category
     * @param currentCell
     * @return
     */
    public static @NonNull MeasurementsModel getMeasurementsModel(String category, CellInformation currentCell) {
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
        // Remap based on category or better - the actual cell type class
        // 3G is CDMA or GSM
        // 4G is LTE
        // 5G is NR
        measurements.setCellType(remapCellClassTypeIntoInteger(currentCell));

        return measurements;
    }

    public static int remapCellClassTypeIntoInteger(CellInformation cellToRemap) {
        // kotlin's exhaustive when() would be great but lets do with what we have
        int retVal = -1;

        if (cellToRemap instanceof GSMInformation || cellToRemap instanceof CDMAInformation) {
            retVal = 3;
        }
        if (cellToRemap instanceof LTEInformation) {
            retVal = 4;
        }
        if (cellToRemap instanceof NRInformation) {
            retVal = 5;
        }

        return retVal;
    }

    public static MeasurementsModel getRegisteredCellInformationUpdatedBySignalStrengthInformation(@NonNull DataProvider dp) {
        if (dp == null) {
            // Don't bother me with this CodeRabbit, i've seen a NullPointerException happen because of this 'dp' being null
            Log.e(TAG, "DataProvider was null, sending Iperf3 data without MeasurementModel");
            return new MeasurementsModel();
        } else {
            List<CellInformation> cellsInfo = dp.getCellInformation();
            List<CellInformation> signalInfo = dp.getSignalStrengthInformation();

            CellInformation currentCell = CellUtil.findRegisteredCell(cellsInfo);
            CellInformation currentSignal = null;
            // After testing on a real phone, apparently there's only one SignalStrengthInformation
            // CellInformation member in the list, so lets just take the first one if it's there
            if (!signalInfo.isEmpty()) {
                currentSignal = signalInfo.get(0);
            }

            String category = currentCell.getCellType().toString();

            // Lets initialize our MeasurementModel for sending from the registered cell model, then overwrite it's values
            // with what we found in the SignalInformation
            MeasurementsModel modelForSending = CellUtil.getMeasurementsModel(category, currentCell);
            // While this one isn't necessary, it's convenient for debugging
            MeasurementsModel updatedModel = updateMeasurementModelByCell(modelForSending, currentSignal);

            return updatedModel;
        }
    }

    /**
     * Updates the {@link MeasurementsModel} {@code measurements} with {@link CellInformation} {@code cellForUpdating} while
     * overwriting all previous values in the model
     * <p>
     * <b>NOTE: has a side-effect of actually updating the measurements, it just returns the updated model as a convenience</b>
     *
     * @param measurements the model to update
     * @param cellForUpdating the source of updated data to overwrite old values in the model
     * @return the updated measurements
     */
    public static MeasurementsModel updateMeasurementModelByCell(@NonNull MeasurementsModel measurements, @NonNull CellInformation cellForUpdating) {
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

        return measurements;
    }
}
