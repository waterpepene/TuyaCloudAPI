import com.google.gson.Gson;

import java.util.Map;

public class TuyaTokenInfo {
    private Double expireTime = 0.0;
    private String accessToken = "";
    private String refreshToken = "";

    public TuyaTokenInfo(String tokenResponse) {
        // response is a JSON similar to {"result":{"access_token":"123456","expire_time":6952,"refresh_token":"12345"},"success":true,"t":1662021611678,"tid":"123"}
        // extract the access token, refresh token and expire time
        Map<String, Object> map = new Gson().fromJson(tokenResponse, Map.class);
        Map<String, Object> result = (Map<String, Object>) map.get("result");
        accessToken = (String) result.get("access_token");
        refreshToken = (String) result.get("refresh_token");
        expireTime = ((Double) result.get("expire_time") * 1000) + ((Double) map.get("t"));

    }

    public Double getExpireTime() {
        return expireTime;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setAccessToken(String s) {
        accessToken = s;
    }
}