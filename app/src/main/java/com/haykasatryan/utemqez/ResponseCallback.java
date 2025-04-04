package com.haykasatryan.utemqez;

public interface ResponseCallback {
    void onResponse(String response);
    void onError(Throwable throwable);
}
