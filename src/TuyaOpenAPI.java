//package com.esi.TuyaInterface;
// uncomment the above line if you are building this for niagara.
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.*;

public class TuyaOpenAPI {
    private final String ENDPOINT = "https://openapi.tuyaeu.com";
    private final String TOKEN_PATH = "/v1.0/token";
    private final String clientID;
    private final String clientSecret;
    private final String deviceID;
    private TuyaTokenInfo TokenInfo;
    private String time = "0";
    private static final Logger LOGGER = Logger.getLogger( TuyaOpenAPI.class.getName() );


    public TuyaOpenAPI(String client_id, String client_secret, String device_id) {
        clientID = client_id;
        clientSecret = client_secret;
        deviceID = device_id;
        // allowing the logger to print to console.
        // these lines of code are only needed if you want to see the log messages in the console.
        // and they only work outside of niagara. comment these lines if you want to build the module for niagara.
        Handler handlerObj = new ConsoleHandler();
        handlerObj.setLevel(Level.ALL);
        LOGGER.addHandler(handlerObj);
        LOGGER.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(false);
    }

    public String GetRequest(String path) throws Exception {
        /*
         * This method is used to send a GET request to the Tuya OpenAPI server.
         * path is the path of the request.
         * an example of path is /v1.0/devices/{device_Id}/functions
         */
        BeforeRequest(path);
        LOGGER.log( Level.FINEST, "Sending GET request to {0}", ENDPOINT + path);
        HttpURLConnection connection = StartConnectionWithHeaders("GET", path, "", "");

        String response = GetResponse(connection);
        LOGGER.log( Level.FINEST, "Response: {0}", response);
        LogIfErrorInResponse(response, "GET request to " + ENDPOINT + path + " successful.\n\tResponse: {0}");

        return response;
    }

    public void PostRequest(String path, String body) throws Exception {
        /*
         * This method is used to send a POST request to the Tuya OpenAPI server.
         * It takes the path where the request is sent to, and the body of the request.
         * path example: /v1.0/devices/{device_Id}/commands
         * body example: {"commands": [{"code": "F", "value": 2}]}
         */
        BeforeRequest(path);
        LOGGER.log( Level.FINEST, "Sending POST request to {0}", ENDPOINT + path);
        HttpURLConnection connection = StartConnectionWithHeaders("POST", path, body, "");

        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(body);
        wr.flush();
        wr.close();

        String response = GetResponse(connection);
        LogIfErrorInResponse(response, "POST request to " + ENDPOINT + path + " successful.\n\tResponse: {0}");

    }

    public void Connect() throws Exception {
        /*
         * Connect to the API and get the access token to be stored in the TokenInfo object.
         * This is done by sending a GET request to the {ENDPOINT}/v1.0/token?grant_type=1
         * The response is then parsed and the information is stored in the TokenInfo object.
         */
        if (isConnected()) {
            LOGGER.log( Level.FINEST, "Already connected");
            return;
        }
        BeforeRequest(TOKEN_PATH);
        LOGGER.log( Level.FINEST, "Connecting to the Tuya API at {0}", ENDPOINT + TOKEN_PATH + "?grant_type=1");
        HttpURLConnection connection = StartConnectionWithHeaders("GET", TOKEN_PATH, "", "?grant_type=1");

        String response = GetResponse(connection);
        TokenInfo = new TuyaTokenInfo(response);
        LogIfErrorInResponse(response, "Connected to the Tuya API. Response: {0}");
    }

    public void Disconnect() {
        /*
         * Disconnect from the API by setting the TokenInfo object to null.
         */
        TokenInfo = null;
        LOGGER.log( Level.FINEST, "Disconnected from the Tuya API");
    }
    public boolean ErrorInResponse(String Response){
        /*
         * This method is used to check if the response from the Tuya OpenAPI server contains an error.
         * It takes the response as a string and returns true if the response contains an error.
         */
        return Response.contains("error_code") || Response.contains("\"success\":false");
    }

    public boolean isConnected() {
        /*
         * Returns whether we are connected to the API or not.
         */
        boolean connected = TokenInfo != null && TokenInfo.getAccessToken() != null && !TokenInfo.getAccessToken().isEmpty();
        LOGGER.log( Level.FINEST, "Connected: {0}", connected);
        return connected;
    }


    public ArrayList<Map<String, Object>> GetFunctions() throws Exception {
        /*
         * This method is used to get the functions of the device.
         * It returns an ArrayList of Objects.
         * Each object is a function of the device.
         * By function we mean an action that can be performed on the device, like turning it on or off.
         */
        LOGGER.log( Level.FINEST, "Getting functions of the device.");
        String PATH = "/v1.0/devices/" + deviceID +  "/functions";
        if (!isConnected()) {
            LOGGER.log( Level.SEVERE, "Not connected to the API. Please connect first.");
            return null;
        }

        String commands = GetRequest(PATH);
        if (ErrorInResponse(commands)) {
            LOGGER.log( Level.SEVERE, "Error in response: {0}", commands);
            return null;
        }
        // commands is a JSON similar to {"result":"functions":[{"code":"switch","desc":"switch","name":"switch","type":"BOOLEAN","values":"{}"},"success":true,"t":1662021611678,"tid":"123"}
        // extract functions array from the JSON
        Map<String, Object> map = new Gson().fromJson(commands, Map.class);
        Map<String, Object> result = (Map<String, Object>) map.get("result");
        ArrayList<Object> devices = (ArrayList<Object>) result.get("functions");

        // Creating an ArrayList of Maps to store the functions to be easily accessed.
        ArrayList<Map<String, Object>> devicesAsMaps = new ArrayList<>();
        for (Object device : devices) {
            Map<String, Object> currentDevice = (Map<String, Object>) device;
            devicesAsMaps.add(currentDevice);
        }
        return devicesAsMaps;
    }

    public static String JSONtoBodyCommands(String userInput) throws Exception{
        /*
         * This method is used to convert a JSON string to a body of a POST request.
         * It takes the JSON string as a parameter and returns the body of the POST request.
         * The JSON string should be in the following format:
         * {code: value}
         * example:
         * {"F": 2}  -> Sets fan speed to 2 (max)
         *
         * The body of the POST request should be in the following format:
         * {"commands": [{"code": "F", "value": 2}]}
         * Used as a utility method for the PostRequest method to easily control a device.
         */
        // turning the JSON string into a map
        Map<String, Object> map = new Gson().fromJson(userInput, Map.class);
        // creating a list of commands to be sent to the device
        ArrayList<String> commands = new ArrayList<>();
        // iterating over the map and creating a command for each key-value pair
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String code = entry.getKey();
            String value = entry.getValue().toString();
            String command = "{\"code\": \"" + code + "\", \"value\": \"" + value + "\"}";
            commands.add(command);
        }
        // creating the body of the POST request
        String body = "{\"commands\": [" + String.join(",", commands) + "]}";
        return body;
    }
    private String BuildSignature(String RequestMethod, String Path, String body, String Parameters) throws Exception {
        /*
         * This Method takes in the request method, path, body, and parameters and returns the signature.
         * The parameters are required to build the signature in a specific order.
         * More info here: https://developer.tuya.com/en/docs/iot/singnature?id=Ka43a5mtx1gsc (2022-09-01)
         */

        // Encrypt the body, which would be the variable which is used to send a command.
        String contentSHA256 = Sha256Util.encryption(body).toLowerCase();
        // If the path is the path to where the token is requested, then don't add the token to the signature.
        boolean TokenAccess = Path.equals(TOKEN_PATH);
        String tokenIfNeeded = TokenAccess ? "" : TokenInfo.getAccessToken();

        // Build the signature according to the documentation above.
        String stringToSign =  clientID + tokenIfNeeded + time + RequestMethod + "\n" + contentSHA256 +"\n\n" + Path + Parameters;
        String signature = Sha256Util.sha256HMAC(stringToSign, clientSecret);
        LOGGER.log( Level.FINEST, "Built signature with stringToSign: {0}", stringToSign);

        return signature.toUpperCase();
    }

    private HttpURLConnection StartConnectionWithHeaders(String RequestMethod, String Path, String body, String Parameters) throws Exception {
        /*
         * RequestMethod can be either: GET, POST
         * Path is the path of the request
         * body is the body of the request, if any, example: '/v1.0/devices/{device_Id}/commands'
         * Parameters is the parameters of the request, if any, example: '?access_token=${access_token}'
         * This method starts a connection given all the arguments and returns the connection object to the tuya API.
         */
        // signature to be added to the header of the request
        String sign = BuildSignature(RequestMethod, Path, body, Parameters);
        // Creating the URL object which contains the URL, path, and parameters of the request

        LOGGER.log( Level.FINEST, "Building URL to {0} and starting connection to the endpoint.", ENDPOINT + Path + Parameters);
        URL url = new URL(ENDPOINT + Path + Parameters);
        // Creating the connection object and opening the connection
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        LOGGER.log( Level.FINEST, "Setting headers for the HTTP request.");
        // Setting the request method, headers, and body of the request
        con.setRequestMethod(RequestMethod);
        // setting headers for the request
        con.setRequestProperty("client_id", clientID);
        // if the request is not a token request, add the access token to the header
        // otherwise we get a NullPointerException
        if (!Path.contains(TOKEN_PATH)) con.setRequestProperty("access_token", TokenInfo.getAccessToken());
        con.setRequestProperty("t", time);
        con.setRequestProperty("sign_method", "HMAC-SHA256");
        con.setRequestProperty("lang", "en");
        con.setRequestProperty("sign", sign);
        // set the content type to application/json so that the body is sent as json
        con.setRequestProperty("Content-Type", "application/json");
        // optional headers for the request
        con.setRequestProperty("dev_version", "0.1.2");
        con.setRequestProperty("dev_lang", "java");

        // setDoOutput(true) is required for POST requests to send the body
        con.setDoOutput(true);

        return con;
    }

    private String GetResponse(HttpURLConnection con) throws IOException {
        /*
         * This method takes a connection and returns the response body as a string.
         */
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }

    private void BeforeRequest(String path) throws Exception {
        /*
         * This method runs before any request is sent to the API.
         * It checks if the token is expired and if it is, it will refresh the token.
         * It also updates the time variable.
         */
        time = String.valueOf(Instant.now().getEpochSecond() * 1000);
        RefreshAccessTokenIfNeeded(path);
    }

    private void RefreshAccessTokenIfNeeded(String path) throws Exception {
        /*
         * The access token expires after 2 hours.
         * If the access token is expired / will expire in less than a minute, refresh it
         * This is done by sending a GET request to the token endpoint with the refresh token
         * received when the access token was first created
         * The response will contain a new access token and refresh token
         * which will be used for subsequent requests
         */
        // if the path is the token path, we don't need to refresh the token
        // or if the user is not connected, there is no token to refresh.
        LOGGER.log( Level.FINEST, "Checking if the token needs to be refreshed.");
        if (!isConnected()  || path.startsWith(TOKEN_PATH)) {
            LOGGER.log( Level.FINEST, "Token does not need to be refreshed. REASON: {0}",
                    !isConnected() ? "User is not connected" : "Path is the token path");
            return;
        }

        long now = Instant.now().getEpochSecond() * 1000;
        Double expiredTime = TokenInfo.getExpireTime();

        // if the token is expired or will expire in less than a minute, refresh it
        if ((expiredTime - 60 * 1000) > now) {  // 1min
            LOGGER.log( Level.FINEST, "Token does not need to be refreshed. REASON: Token is not expired.");
            return;
        }

        // reset the token info
        TokenInfo.setAccessToken("");
        // send a GET request to the token endpoint with the refresh token
        String parameterToAdd = "/" + TokenInfo.getRefreshToken();
        HttpURLConnection connection = StartConnectionWithHeaders("GET", TOKEN_PATH, "", parameterToAdd);

        String response = GetResponse(connection);
        // update the token info with the new access token and refresh token
        TokenInfo = new TuyaTokenInfo(response);
        LogIfErrorInResponse(response, "Token refreshed. Response: \n\t{0}");
    }

    private void LogIfErrorInResponse(String response, String message) throws Exception {
        /*
         * This method checks if the response contains an error by looking for "success":false in the response.
         * If it does contain an error, it logs the error message with the SEVERE level.
         * If it does not contain an error, it logs the message with the INFO level.
         */
        if (ErrorInResponse(response)) {
            LOGGER.log( Level.SEVERE, "Error in request: {0}", response);
            LOGGER.log( Level.SEVERE, "\tFor more information check: https://developer.tuya.com/en/docs/iot/error-code?id=K989ruxx88swc", response);
            return;
        }

        LOGGER.log( Level.FINEST, message, response);
    }

}
