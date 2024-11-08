package cloudcity.dataholders;

import android.content.Context;

import androidx.annotation.NonNull;

import java.sql.Timestamp;
import java.util.HashMap;

import cloudcity.Iperf3Monitor;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.Iperf3Fragment;

/**
 * A simple POJO/mule class to transfer data between <br>
 * {@link Iperf3Monitor#startDefault15secTest()}<br>
 * and <br>
 * {@link cloudcity.Iperf3Monitor#startIperf3Test(Context, Iperf3RunnerData)}<br>
 * methods
 */
public class Iperf3RunnerData {

    @NonNull
    private final String joinedCommand;
    @NonNull
    private final String[] commandList;
    @NonNull
    private final HashMap<String, String> dataMap;
    @NonNull
    private final HashMap<String, Boolean> booleanMap;
    @NonNull
    private final Timestamp timestamp;
    @NonNull
    private final Iperf3Fragment.Iperf3Input stupidInput;

    public Iperf3RunnerData(
            @NonNull String command,
            @NonNull String[] cmdList,
            @NonNull HashMap<String, String> map,
            @NonNull HashMap<String, Boolean> boolMap,
            @NonNull Timestamp timestamp,
            @NonNull Iperf3Fragment.Iperf3Input originalInput
    ) {
        this.joinedCommand = command;
        this.commandList = cmdList;
        this.dataMap = map;
        this.booleanMap = boolMap;
        this.timestamp = timestamp;
        this.stupidInput = originalInput;
    }

    public @NonNull HashMap<String, String> getStringDataMap() {
        return dataMap;
    }

    public @NonNull HashMap<String, Boolean> getBooleanDataMap() {
        return booleanMap;
    }

    public @NonNull String getJoinedCommand() {
        return joinedCommand;
    }

    public @NonNull String[] getCommandList() {
        return commandList;
    }

    public @NonNull Timestamp getTimestamp() {
        return timestamp;
    }

    /**
     * You know how that guy from south park says "City Wok"?<br>
     * Yea, this is that exact same City Input
     *
     * @return the {@link Iperf3Fragment.Iperf3Input} city input
     */
    public @NonNull Iperf3Fragment.Iperf3Input getCityInput() {
        return stupidInput;
    }
}
