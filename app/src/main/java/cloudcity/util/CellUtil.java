package cloudcity.util;

import android.telephony.CellInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

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
     * @param cellList list of cells to look through
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
     * Maps cell type to an integer value for database compatibility
     *
     * @param cellToRemap The cell information to be remapped
     * @return Integer representing the cell type:<br>
     * 3 - 3G (GSM/CDMA)<br>
     * 4 - 4G (LTE)<br>
     * 5 - 5G (NR)<br>
     * @throws IllegalStateException when an unknown/unsupported cell type is encountered
     */
    public static int remapCellClassTypeIntoInteger(CellInformation cellToRemap) {
        // kotlin's exhaustive when() would be great but lets do with what we have
        int retVal;

        if (cellToRemap instanceof GSMInformation || cellToRemap instanceof CDMAInformation) {
            retVal = 3;
        } else if (cellToRemap instanceof LTEInformation) {
            retVal = 4;
        } else if (cellToRemap instanceof NRInformation) {
            retVal = 5;
        } else {
            Log.e(TAG, "Unsupported cell type: "+cellToRemap.getCellType());
            throw new IllegalStateException("Unsupported cell type encountered in CellUtil::remapCellClassTypeIntoInteger! cellType: "+cellToRemap.getCellType());
        }

        return retVal;
    }

    /**
     * Handy method for doint everything we normally do manually, find the registered cell, grab the signal strength
     * and then init the {@link MeasurementsModel} from the registered cell and update with the signal strength
     *
     * @param dp the {@link DataProvider} to grab all of these things from
     * @return the MeasurementsModel to be uploaded
     */
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

            // Lets initialize the MeasurementsModel by taking non-null (and non-invalid) values from both the currently registered
            // cell and the current signal info, and give precedence to one of them;
            //
            // in case both are non-null, the one with precedence will be used
            // in case one of them is null, the other one will be used
            // in case both of them are null, the one with precedence will be used
            MeasurementsModel modelForSending = CellUtil.getMeasurementsModel(currentCell, currentSignal, CellInfoPrecedence.SIGNAL_INFO);

            return modelForSending;
        }
    }

    /**
     * Enum class used to give precedence to either the registered cell or the signal strength info
     */
    public enum CellInfoPrecedence {
        /**
         * Give precedence to Cell info
         */
        CELL_INFO,
        /**
         * Give precedence to Signal info
         */
        SIGNAL_INFO
    }

    /**
     * Extract various measurement data from the currently registered {@link CellInformation} as returned by {@link DataProvider}
     * @param currentCell the currently-registered cell to get information from
     * @param signalStrength the signal strength to get information from
     * @param precedence which cell info should take precedence
     * @return the {@link MeasurementsModel} obtained from information contained in the {@code currentCell}
     */
    public static @NonNull MeasurementsModel getMeasurementsModel(CellInformation currentCell, CellInformation signalStrength, CellInfoPrecedence precedence) {
        MeasurementsModel measurements = new MeasurementsModel();

        // Since all of these 'informations' are just numbers, they will never be NULL, however they
        // will be CellInfo.UNAVAILABLE so we will use that as a NULL marker;
        //
        // The idea is simple, if both the 'currentCell' and 'signalStrength' contain a non-null
        // value, take the one that takes precedence; otherwise take the non-null value.
        // If both of them are null, well then, take the null that takes precedence since it changes nothing
        //
        // We are in a bad situation if currentCell and signalStrength can end up being of different types.

        // New safety
        if (currentCell instanceof NRInformation) {
            NRInformation nrCell = (NRInformation) currentCell;
            // Assume signalStrength is the same
            NRInformation nrSignal = (NRInformation) signalStrength;

            Integer validCsirsrp = getNonNullAndNonInvalidMember(nrCell.getCsirsrp(), nrSignal.getCsirsrp(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validCsirsrq = getNonNullAndNonInvalidMember(nrCell.getCsirsrq(), nrSignal.getCsirsrq(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validCsisinr = getNonNullAndNonInvalidMember(nrCell.getCsisinr(), nrSignal.getCsisinr(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validSsrsrp = getNonNullAndNonInvalidMember(nrCell.getSsrsrp(), nrSignal.getSsrsrp(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validSsrsrq = getNonNullAndNonInvalidMember(nrCell.getSsrsrq(), nrSignal.getSsrsrq(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validSssinr = getNonNullAndNonInvalidMember(nrCell.getSssinr(), nrSignal.getSssinr(), CellInfoPrecedence.SIGNAL_INFO);

            measurements.setCsirsrp(validCsirsrp);
            measurements.setCsirsrq(validCsirsrq);
            measurements.setCsisinr(validCsisinr);
            measurements.setSsrsrp(validSsrsrp);
            measurements.setSsrsrq(validSsrsrq);
            measurements.setSssinr(validSssinr);
        } else if (currentCell instanceof LTEInformation) {
            LTEInformation lteCell = (LTEInformation) currentCell;
            // Likewise, assume signal strength is the same
            LTEInformation lteSignal = (LTEInformation) signalStrength;

            Integer validRsrp = getNonNullAndNonInvalidMember(lteCell.getRsrp(), lteSignal.getRsrp(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validRsrq = getNonNullAndNonInvalidMember(lteCell.getRsrq(), lteSignal.getRsrq(), CellInfoPrecedence.SIGNAL_INFO);
            Integer validRssnr = getNonNullAndNonInvalidMember(lteCell.getRssnr(), lteSignal.getRssnr(), CellInfoPrecedence.SIGNAL_INFO);

            measurements.setRsrp(validRsrp);
            measurements.setRsrq(validRsrq);
            measurements.setRssnr(validRssnr);
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

    private static Integer getNonNullAndNonInvalidMember(Integer cellValue, Integer signalValue, CellInfoPrecedence precedence) {
        boolean cellValueNonNull = cellValue != null && cellValue != CellInfo.UNAVAILABLE;
        boolean signalValueNonNull = signalValue != null && signalValue != CellInfo.UNAVAILABLE;

        // The idea is simple, if both the 'currentCell' and 'signalStrength' contain a non-null
        // value, take the one that takes precedence; otherwise take the non-null value.
        // If both of them are null, well then, take the null that takes precedence since it changes nothing
        if (cellValueNonNull && signalValueNonNull) {
            switch (precedence) {
                case CELL_INFO: return cellValue;
                case SIGNAL_INFO: return signalValue;
            }
        } else if (cellValueNonNull) {
            return cellValue;
        } else if (signalValueNonNull) {
            return signalValue;
        } else {
            // This is the case when both of them are null, so return the one with precendence
            // since it changes nothing
            switch (precedence) {
                case CELL_INFO: return cellValue;
                case SIGNAL_INFO: return signalValue;
            }
        }

        // We will not return here, but actually throw. We must surely have returned by now
        throw new IllegalStateException("No suitable information found in CellUtil::getNonNullAndNonInvalidMember()");
    }
}
