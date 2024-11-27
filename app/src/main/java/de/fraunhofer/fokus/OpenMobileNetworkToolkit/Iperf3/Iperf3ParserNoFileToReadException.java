package de.fraunhofer.fokus.OpenMobileNetworkToolkit.Iperf3;

/**
 * Exception thrown when {@link Iperf3Parser} encounters a {@link java.io.FileNotFoundException}
 * during construction in {@link Iperf3Parser#Iperf3Parser(String)}
 */
public class Iperf3ParserNoFileToReadException extends Exception {

    Iperf3ParserNoFileToReadException(Exception exception) {
        super(exception);
    }
}
