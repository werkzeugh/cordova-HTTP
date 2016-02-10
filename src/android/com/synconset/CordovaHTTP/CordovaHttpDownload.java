/**
 * A HTTP plugin for Cordova / Phonegap
 */
package com.synconset;

import android.util.Log;

import com.github.kevinsawicki.http.HttpRequest;
import com.github.kevinsawicki.http.HttpRequest.HttpRequestException;
import com.github.kevinsawicki.http.HttpRequest.UploadProgress;

import java.io.File;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.file.FileUtils;
import org.apache.cordova.PluginResult;

import org.json.JSONException;
import org.json.JSONObject;

/*
 Modified by Digitalsunray Media GmbH. on 02.10.2016 - 12:41 PM.
 */
public class CordovaHttpDownload extends CordovaHttp implements Runnable {
    private String filePath;

    public CordovaHttpDownload(String urlString, Map<?, ?> params, Map<String, String> headers, CallbackContext callbackContext, String filePath) {
        super(urlString, params, headers, callbackContext);
        this.filePath = filePath;
    }

    @Override
    public void run() {
        try {
            HttpRequest request = HttpRequest.get(this.getUrlString(), this.getParams(), true);
            this.setupSecurity(request);
            request.acceptCharset(CHARSET);
            request.headers(this.getHeaders());
            //listening to the downaload progress updates...

            int code = request.code();

            JSONObject response = new JSONObject();
            this.addResponseHeaders(request, response);
            response.put("status", code);
            if (code >= 200 && code < 300) {
                URI uri = new URI(filePath);
                File file = new File(uri);
                request.progress(new UploadProgress() {
                    public void onUpload(long downloaded, long total) {
                        sendProgressResult(downloaded, total, false);
                    }
                });

                request.receive(file);

                JSONObject fileEntry = FileUtils.getFilePlugin().getEntryForFile(file);
                response.put("file", fileEntry);
                this.getCallbackContext().success(response);
            } else {
                response.put("error", "There was an error downloading the file");
                this.getCallbackContext().error(response);
            }
        } catch(URISyntaxException e) {
            this.respondWithError("There was an error with the given filePath");
        } catch (JSONException e) {
            this.respondWithError("There was an error generating the response");
        } catch (HttpRequestException e) {
            if (e.getCause() instanceof UnknownHostException) {
                this.respondWithError(0, "The host could not be resolved");
            } else if (e.getCause() instanceof SSLHandshakeException) {
                this.respondWithError("SSL handshake failed");
            } else {
                this.respondWithError("There was an error with the request");
            }
        }
        finally
        {
            //Download process is completed!
            sendProgressResult(-1, -1, true);
        }
    }

    private void sendProgressResult(long downloaded, long total, boolean isFinished)
    {
        Log.i("CordovaHttpDownload", downloaded +" finished from "+ total);

        //Create the result's JSON object...
        JSONObject resultObj = new JSONObject();
        try {
            resultObj.put("downloaded", downloaded);
            resultObj.put("total", total);
            resultObj.put("finished", isFinished);

            PluginResult result = new PluginResult(PluginResult.Status.OK, resultObj);
            result.setKeepCallback(!isFinished);

            //Send plugin result...
            getCallbackContext().sendPluginResult(result);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}