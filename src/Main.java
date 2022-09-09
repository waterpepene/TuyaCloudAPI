public class Main {

    static String client_id = "";
    static String secret_key = "";
    static String body = "";
    static String PATH = "";

    public static void main(String[] args) throws Exception {
        TuyaOpenAPI api = new TuyaOpenAPI(client_id, secret_key, "device_id");
        api.Connect();
        api.GetFunctions();


    }
}
