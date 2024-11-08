package cloudcity.dataholders;

import android.content.Context;

import androidx.annotation.NonNull;

import java.sql.Timestamp;
import java.util.HashMap;

/**
 * A simple POJO/mule class to transfer data between <br>
 * {@link cloudcity.Iperf3Monitor#startDefault15secTest(Context)}<br>
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

    public Iperf3RunnerData(
            @NonNull String command,
            @NonNull String[] cmdList,
            @NonNull HashMap<String, String> map,
            @NonNull HashMap<String, Boolean> boolMap,
            @NonNull Timestamp timestamp
    ) {
        this.joinedCommand = command;
        this.commandList = cmdList;
        this.dataMap = map;
        this.booleanMap = boolMap;
        this.timestamp = timestamp;
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
}
