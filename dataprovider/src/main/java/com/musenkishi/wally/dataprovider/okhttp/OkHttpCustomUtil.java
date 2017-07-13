package com.musenkishi.wally.dataprovider.okhttp;

import android.text.TextUtils;
import android.util.Log;
import com.musenkishi.wallbase.dataprovider.BuildConfig;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.CacheControl;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Created by ZCZ on 2017/4/15.
 */

public class OkHttpCustomUtil {

  private static final int TIME_OUT = 30;
  private static OkHttpClient client;

  //标准单例模式
  public static OkHttpClient getInstance() {
    if (client == null) {
      synchronized (OkHttpCustomUtil.class) {
        if (client == null) {

          OkHttpClient.Builder builder = new OkHttpClient.Builder();
          //builder.addNetworkInterceptor(new HttpCacheInterceptor())
          //    .addInterceptor(new HttpCacheInterceptor());

          if (BuildConfig.DEBUG) {
            builder.addInterceptor(new LoggerInterceptor("caiba", BuildConfig.DEBUG));
          }

          //本地不验证https链接
          builder.hostnameVerifier(new HostnameVerifier() {
            @Override public boolean verify(String s, SSLSession sslSession) {
              return true;
            }
          });
          TrustManager[] trustAllCerts = new TrustManager[] {
              new X509TrustManager() {
                @Override public void checkClientTrusted(
                    java.security.cert.X509Certificate[] x509Certificates, String s)
                    throws java.security.cert.CertificateException {
                }

                @Override public void checkServerTrusted(
                    java.security.cert.X509Certificate[] x509Certificates, String s)
                    throws java.security.cert.CertificateException {
                }

                @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                  return new java.security.cert.X509Certificate[] {};
                }
              }
          };
          try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            builder.sslSocketFactory(sc.getSocketFactory());
          } catch (Exception e) {
            e.printStackTrace();
          }

          client = builder
              //.cache(getCache())
              .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
              .writeTimeout(TIME_OUT, TimeUnit.SECONDS)
              .readTimeout(TIME_OUT, TimeUnit.SECONDS)
              .build();
        }
      }
    }
    return client;
  }
/*
  public static Cache getCache() {
    File httpCacheDirectory = new File(MyApplication.getInstance().getCacheDir(), "responses");
    return new Cache(httpCacheDirectory, 10 * 1024 * 1024);         // 缓存空间10M
  }*/

  /**
   * Http请求缓存，暂时 退出app时清除
   */
  public static void clearHttpCache() {
    try {
      getInstance().cache().delete();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Okhttp 网络请求缓存时间处理
   * 当没有网络的时候，缓存永远不失效；
   * 当有网络的时候
   * 如果如果请求失败则失败，
   * 如果请求成功，如果需要缓存，在会根据缓存设置的有效期，来决定是否访问缓存；
   */
  public static class HttpCacheInterceptor implements Interceptor {

    @Override public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      String customCacheType = request.header("custom-cache-type");

      boolean isCache = !TextUtils.isEmpty(customCacheType);
      boolean connected = true;
      //boolean connected = NetUtils.isConnected(MyApplication.getInstance());

      //无网络时强制读取缓存配置
      //if (!connected && isCache) {
      if (!connected) {
        request = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build();
        //LogUtils.tag("net").d("no network");
      }

      Response response = chain.proceed(request);

      //有网络时并且需要缓存的接口才缓存数据
      //if (connected && isCache) {
      if (!connected) {
        //int maxStale = 60 * 60 * 24 * 7;
        //int maxStale = 604800;
        int maxStale = Integer.MAX_VALUE; //无网络时，使用之前的缓存。不区分接口。
        response = response.newBuilder()
            .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
            .removeHeader("Pragma")
            .build();

        //} else if (connected) {
        //有网络时不需要缓存的接口，清除之前缓存
      }
      //else if (connected && isCache) {
      //  int maxAge = 604800;                     //需要缓存的接口：缓存1周更新一次
      //  response = response.newBuilder()
      //      .header("Cache-Control", "public, max-age=" + maxAge)
      //      .removeHeader("Pragma")     // 清除头信息
      //      .build();
      //}

      return response;
    }
  }

  public static class LoggerInterceptor implements Interceptor {
    public static final String TAG = "OkHttpUtils";
    private String tag;
    private boolean showResponse;

    public LoggerInterceptor(String tag, boolean showResponse) {
      if (TextUtils.isEmpty(tag)) {
        tag = TAG;
      }
      this.showResponse = showResponse;
      this.tag = tag;
    }

    public LoggerInterceptor(String tag) {
      this(tag, false);
    }

    @Override public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      logForRequest(request);
      Response response = chain.proceed(request);
      return logForResponse(response);
    }

    private Response logForResponse(Response response) {
      try {
        //===>response log
        Log.e(tag, "========response'log=======");
        Response.Builder builder = response.newBuilder();
        Response clone = builder.build();
        Log.e(tag, "url : " + clone.request().url());
        Log.e(tag, "code : " + clone.code());
        Log.e(tag, "protocol : " + clone.protocol());
        Headers headers = clone.headers();
        if (headers != null && headers.size() > 0) {
          Log.e(tag, "headers : " + headers.toString());
        }
        if (!TextUtils.isEmpty(clone.message())) Log.e(tag, "message : " + clone.message());

        if (showResponse) {
          ResponseBody body = clone.body();
          if (body != null) {
            MediaType mediaType = body.contentType();
            if (mediaType != null) {
              Log.e(tag, "responseBody's contentType : " + mediaType.toString());
              if (isText(mediaType)) {
                String resp = body.string();
                Log.e(tag, "responseBody's content : " + resp);

                body = ResponseBody.create(mediaType, resp);
                return response.newBuilder().body(body).build();
              } else {
                Log.e(tag, "responseBody's content : "
                    + " maybe [file part] , too large too print , ignored!");
              }
            }
          }
        }

        Log.e(tag, "========response'log=======end");
      } catch (Exception e) {
        //            e.printStackTrace();
      }

      return response;
    }

    private void logForRequest(Request request) {
      try {
        String url = request.url().toString();
        Headers headers = request.headers();

        Log.e(tag, "========request'log=======");
        Log.e(tag, "method : " + request.method());
        Log.e(tag, "url : " + url);
        if (headers != null && headers.size() > 0) {
          Log.e(tag, "headers : " + headers.toString());
        }
        RequestBody requestBody = request.body();
        if (requestBody != null) {
          MediaType mediaType = requestBody.contentType();
          if (mediaType != null) {
            Log.e(tag, "requestBody's contentType : " + mediaType.toString());
            if (isText(mediaType)) {
              Log.e(tag, "requestBody's content : " + bodyToString(request));
            } else {
              Log.e(tag, "requestBody's content : "
                  + " maybe [file part] , too large too print , ignored!");
            }
          }
        }
        Log.e(tag, "========request'log=======end");
      } catch (Exception e) {
        //            e.printStackTrace();
      }
    }

    private boolean isText(MediaType mediaType) {
      if (mediaType.type() != null && mediaType.type().equals("text")) {
        return true;
      }
      if (mediaType.subtype() != null) {
        if (mediaType.subtype().equals("json")
            || mediaType.subtype().equals("xml")
            || mediaType.subtype().equals("html")
            || mediaType.subtype().equals("x-www-form-urlencoded")
            || mediaType.subtype().equals("webviewhtml")) {
          return true;
        }
      }
      return false;
    }

    private String bodyToString(final Request request) {
      try {
        final Request copy = request.newBuilder().build();
        final Buffer buffer = new Buffer();
        copy.body().writeTo(buffer);
        return buffer.readUtf8();
      } catch (final IOException e) {
        return "something error when show requestBody.";
      }
    }
  }
}
