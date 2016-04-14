/**
 * A HTTP plugin for Cordova / Phonegap
 */
package com.synconset;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.HashMap;

import org.apache.cordova.CallbackContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.text.TextUtils;

import javax.net.ssl.SSLHandshakeException;

import android.util.Log;

import com.github.kevinsawicki.http.HttpRequest;
import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;

import java.util.Iterator;
 
public class CordovaHttpPostJson extends CordovaHttp implements Runnable {
    
    final String KEY_FORMAT = "[%s]";
	
	public CordovaHttpPostJson(String urlString, JSONObject jsonObj, Map<String, String> headers, CallbackContext callbackContext) {
        super(urlString, jsonObj, headers, callbackContext);
    }
	
	private Map<String, String> parseJson(JSONObject jsonObject, String prefix, long depth)
    {
        Map<String, String> entities = new HashMap();

        if (jsonObject == null)
            return entities;

        if (prefix == null)
            prefix = "";

        try {
            Iterator<String> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                try {
                    Object value = jsonObject.get(key);
                    if (value == null)
                        continue;

                    if (value instanceof JSONObject) {
                        entities.putAll(parseJson((JSONObject) value, prefix + String.format(KEY_FORMAT, key), depth + 1));
                    } else if (value instanceof JSONArray) {
                        //no brackets if we have a root element
                        if (depth == 0) {
                            for (int i = 0; i < ((JSONArray) value).length(); i++) {
                                entities.putAll(parseJson(((JSONArray) value).getJSONObject(i),
                                        prefix +
                                                key +
                                                String.format(KEY_FORMAT, String.valueOf(i)), depth + 1));
                            }
                        } else {
                            for (int i = 0; i < ((JSONArray) value).length(); i++) {
                                entities.putAll(parseJson(((JSONArray) value).getJSONObject(i),
                                        prefix +
                                                String.format(KEY_FORMAT, key) +
                                                String.format(KEY_FORMAT, String.valueOf(i)), depth + 1));
                            }
                        }
                    } else {
                        if (!TextUtils.isEmpty(prefix)) //Don't add bracket to the root elements!
                            key = prefix + String.format(KEY_FORMAT, key);

                        entities.put(key, String.valueOf(value));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return entities;
    }
    
    @Override
    public void run() {
        try {
            HttpRequest request = HttpRequest.post(this.getUrlString());
            this.setupSecurity(request);
            request.headers(this.getHeaders());
			request.form(parseJson(getJsonObject(), null, 0));
            int code = request.code();
            String body = request.body(CHARSET);
            JSONObject response = new JSONObject();
            response.put("status", code);
            if (code >= 200 && code < 300) {
                response.put("data", new JSONObject(body));
                this.getCallbackContext().success(response);
            } else {
                response.put("error", new JSONObject(body));
                this.getCallbackContext().error(response);
            }
        } catch (JSONException e) {
            this.respondWithError("There was an error generating the response");
        }  catch (HttpRequestException e) {
            if (e.getCause() instanceof UnknownHostException) {
                this.respondWithError(0, "The host could not be resolved");
            } else if (e.getCause() instanceof SSLHandshakeException) {
                this.respondWithError("SSL handshake failed");
            } else {
                this.respondWithError("There was an error with the request");
            }
        }
    }
}