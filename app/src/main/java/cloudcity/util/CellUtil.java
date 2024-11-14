package cloudcity.util;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

import cloudcity.networking.models.MeasurementsModel;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.CellInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.LTEInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.CellInformations.NRInformation;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.DataProvider.DataProvider;

public class CellUtil {

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
        return measurements;
    }
}
