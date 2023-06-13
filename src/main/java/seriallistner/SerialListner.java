package seriallistner;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
Authoers : Dr M H B Ariyaratne & ChatGPT
 */
public class SerialListner {

    private static String portName = "com7"; // Change this to the appropriate port name
    private static int baudRate = 9600; // Change this to the desired baud rate
    private static int dataBits = 8; // Change this to the desired data bits
    private static int stopBits = 1; // Change this to the desired stop bits
    private static int parity = SerialPort.NO_PARITY; // Change this to the desired parity
    private static byte[] ACK = {0x06}; // ASCII ACK byte
    private static byte STX = 0x02; // ASCII STX byte
    private static byte ETB = 0x17; // ASCII ETB byte
    private static byte[] CR_LF = {0x0D, 0x0A}; // ASCII CR and LF bytes
    static String receivedString = "";
    static int frameNumber;

    static StringBuilder receivedStringBuilder = new StringBuilder();

    public static void main(String[] args) {
        SerialPort serialPort = SerialPort.getCommPort(portName);
        setComPortParameters(serialPort, baudRate, dataBits, stopBits, parity);
        serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                    return;
                }

                byte[] newData = new byte[serialPort.bytesAvailable()];
                int numRead = serialPort.readBytes(newData, newData.length);
                String newString = "";
                try {
                    newString = new String(newData, "ASCII");
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(SerialListner.class.getName()).log(Level.SEVERE, null, ex);
                }

                System.out.println(newString);

                receivedStringBuilder.append(newString);

                if (newString.endsWith("\u0003")) {  // Check for ASCII ETX at the end of the message
                    receivedString = receivedStringBuilder.toString();
                    receivedStringBuilder = new StringBuilder();  // Reset the StringBuilder for the next message

                    System.out.println("Full message received: " + receivedString);

                    // Asynchronously process the received message
                    final String finalReceivedString = receivedString;
                    CompletableFuture.supplyAsync(() -> performAsyncProcessing(finalReceivedString))
                            .thenAccept(processedMessage -> {
                                // Handle the processed message (e.g., update UI, send response, etc.)
                                System.out.println("Processed message: " + processedMessage);
                            });

                    // Send ACK
                    serialPort.writeBytes(ACK, ACK.length);
                }
            }

        });

        if (serialPort.openPort()) {
            System.out.println("Serial port opened successfully.");
        } else {
            System.out.println("Failed to open serial port.");
            return;
        }

        // Keep the main thread alive
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to set COM port parameters
    public static void setComPortParameters(SerialPort serialPort, int baudRate, int dataBits, int stopBits, int parity) {
        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(dataBits);
        serialPort.setNumStopBits(stopBits);
        serialPort.setParity(parity);
    }

    // Method to split the input text into frames if it exceeds the maximum length
    public static String[] splitIntoFrames(String text, int maxLength) {
        int textLength = text.length();
        int numOfFrames = textLength / maxLength;
        if (textLength % maxLength != 0) {
            numOfFrames++;
        }
        String[] frames = new String[numOfFrames];
        for (int i = 0; i < numOfFrames; i++) {
            int startIndex = i * maxLength;
            int endIndex = Math.min((i + 1) * maxLength, textLength);
            frames[i] = text.substring(startIndex, endIndex);
        }
        return frames;
    }

    // Method to send a frame
    public static void sendFrame(SerialPort serialPort, String frameText, int fno) {
        String frameNumber = "F" + fno; // Replace with the actual frame number
        String chk1 = "CHK1"; // Replace with the actual CHK1
        String chk2 = "CHK2"; // Replace with the actual CHK2
        String frame = STX + " " + frameNumber + " " + frameText + " " + ETB + " " + chk1 + " " + chk2;
        byte[] frameBytes = frame.getBytes();
        byte[] messageBytes = new byte[frameBytes.length + CR_LF.length];
        System.arraycopy(frameBytes, 0, messageBytes, 0, frameBytes.length);
        System.arraycopy(CR_LF, 0, messageBytes, frameBytes.length, CR_LF.length);
        serialPort.writeBytes(messageBytes, messageBytes.length);
        System.out.println("Sent frame: " + frame);
    }

    // Method to select parity
    public static int paritySelection(String parity) {
        switch (parity.toLowerCase()) {
            case "none":
                return SerialPort.NO_PARITY;
            case "odd":
                return SerialPort.ODD_PARITY;
            case "even":
                return SerialPort.EVEN_PARITY;
            case "mark":
                return SerialPort.MARK_PARITY;
            case "space":
                return SerialPort.SPACE_PARITY;
            default:
                return SerialPort.NO_PARITY;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SerialListner.class.getName());

    private static String sendHttpRequest(String processedMessage) throws IOException {
        // Perform the HTTP request and return the response
        // Replace with your actual HTTP request implementation
        return "HTTP response";
    }
    // Perform asynchronous processing including the HTTP request

    private static String performAsyncProcessing(String receivedString) {
//        LOGGER.info("performAsyncProcessing");
        try {
            // Process the received message
            String processedMessage = processAnalyzerMessage(receivedString);

            // Perform the HTTP request asynchronously
            String result = sendHttpRequest(processedMessage);

            return result;
        } catch (Exception ex) {
            // Handle any exceptions
            ex.printStackTrace();
            return "Error: " + ex.getMessage();
        }
    }

    public static String processAnalyzerMessage(String receivedMessage) {
//        LOGGER.info("Process Analyzer Message");
        try {
//            LOGGER.info("Message Received from Analyzer = " + receivedMessage);
            String msgType = null;
            String baseUrl = "http://arogyahealthlk.com/";
            String restApiUrl = baseUrl + "api/limsmw/limsProcessAnalyzerMessage";
            String username = "buddhika";
            String password = "buddhika123@";
            try {
                URL url = new URL(restApiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                if (username != null && password != null) {
                    String credentials = username + ":" + password;
                    String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                    connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
                }
                connection.setDoOutput(true);
                JSONObject requestBodyJson = new JSONObject();
                String base64EncodedMessage = Base64.getEncoder().encodeToString(receivedMessage.getBytes(StandardCharsets.UTF_8));
                requestBodyJson.put("message", base64EncodedMessage);
//                LOGGER.info("Message Received from Analyzer Encoded = " + base64EncodedMessage);
                OutputStream outputStream = connection.getOutputStream();
                String requestBodyString = requestBodyJson.toString();
                outputStream.write(requestBodyString.getBytes());
                outputStream.flush();
                outputStream.close();
                int responseCode = connection.getResponseCode();
//                LOGGER.info("LIMS response Code = " + responseCode);
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try ( BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder responseBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line).append("\n");
                        }
                        String response = responseBuilder.toString().trim();
//                        LOGGER.info("LIMS response as it is = " + response);
                        JSONObject responseJson = new JSONObject(response);
                        String base64EncodedResultMessage = responseJson.getString("result");
                        byte[] decodedResultMessageBytes = Base64.getDecoder().decode(base64EncodedResultMessage);
                        String decodedResultMessage = new String(decodedResultMessageBytes, StandardCharsets.UTF_8);

//                        LOGGER.info("Encoded Message Received from LIMS = " + base64EncodedResultMessage);
//                        LOGGER.info("Decoded Message Received from LIMS = " + decodedResultMessage);
//                        LOGGER.info("LIMS sent Message Type = " + msgType);
                        return decodedResultMessage;
                    }
                } else {
                    try ( BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                        StringBuilder responseBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line).append("\n");
                        }
                        String response = responseBuilder.toString().trim();
//                        LOGGER.info("response = " + response);
                        return createErrorResponse(response);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return createErrorResponse(e.getMessage());
            }
        } catch (Exception ex) {
            Logger.getLogger(SerialListner.class.getName()).log(Level.SEVERE, null, ex);
            return ex.getMessage();
        }
    }

    private static String createErrorResponse(String errorMessage) {

        return null;
    }

}
