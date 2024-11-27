package de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import cloudcity.util.CloudCityLogger;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Error;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.Interval.Interval;
import de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3.JSON.start.Start;


public class Iperf3Parser {
    private static final String TAG = Iperf3Parser.class.getSimpleName();

    public static final Object END_MARKER = new Object();

    public interface Iperf3ParserCompletionListener {
        void onParseCompleted();
    }

    private Iperf3ParserCompletionListener completionListener;

    private final String pathToFile;
    private final File file;
    private BufferedReader br = null;
    private PropertyChangeSupport support;
    private Start start;
    private final Intervals intervals = new Intervals();

    public static Iperf3Parser instantiate(@NonNull String pathToFile) throws FileNotFoundException {
        return new Iperf3Parser(pathToFile);
    }

    Iperf3Parser(String pathToFile) throws FileNotFoundException {
        this.pathToFile = pathToFile;
        this.file = new File(this.pathToFile);
        try {
            br = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException ex) {
            System.out.println("File not found");
            CloudCityLogger.e(TAG, "File not found!!! Path to file: "+pathToFile, ex);
            return;
        }
        this.support = new PropertyChangeSupport(this);
    }

    public void parse(){
        String line;
        try {
            while ((line = br.readLine()) != null) {
                JSONObject obj = new JSONObject(line);
                String event = obj.getString("event");
                switch (event) {
                    case "start":
                        CloudCityLogger.v(TAG, "Encountered START");
                        start = new Start();
                        JSONObject startData = obj.getJSONObject("data");
                        start.parseStart(startData);
                        break;
                    case "interval":
                        Interval interval = new Interval();
                        JSONObject intervalData = obj.getJSONObject("data");
                        interval.parse(intervalData);
                        support.firePropertyChange("interval", null, interval);
                        intervals.addInterval(interval);
                        break;
                    case "end":
                        CloudCityLogger.v(TAG, "Encountered END\t\tcompletionListener: "+completionListener);
                        System.out.println("End");
                        support.firePropertyChange("end", null, END_MARKER);
                        break;
                    case "error":
                        Error error = new Error();
                        String errorString = obj.getString("data");
                        error.parse(errorString);
                        support.firePropertyChange("error", null, error);
                        break;
                    default:
                        System.out.println("Unknown event");
                        CloudCityLogger.w(TAG, "Unknown event "+event+" encountered during parsing!");
                        break;
                }
            }
            if (completionListener != null) {
                completionListener.onParseCompleted();
            }
        } catch (Exception e) {
            System.out.println("Error reading file");
            CloudCityLogger.e(TAG, "Exception " + e + " happened while trying to parse file!", e);
        }
    }

    public Intervals getIntervals() {
        return intervals;
    }
    public Start getStart() {
        return start;
    }
    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

    public void setCompletionListener(Iperf3ParserCompletionListener cl) {
        this.completionListener = cl;
    }
}
