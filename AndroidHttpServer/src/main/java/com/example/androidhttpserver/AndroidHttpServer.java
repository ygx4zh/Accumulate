package com.example.androidhttpserver;


import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import com.example.androidhttpserver.servlet.base.IAndroidServletRequest;
import com.example.androidhttpserver.servlet.http.Cookie;
import com.example.androidhttpserver.servlet.impl.AndroidHttpServlet;
import com.example.androidhttpserver.servlet.impl.AndroidServletRequestImpl;
import com.example.androidhttpserver.servlet.impl.AndroidServletResponseImpl;
import com.example.androidhttpserver.webinfo.WebMapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class AndroidHttpServer extends NanoHTTPD{
    private static final String TAG = "AndroidHttpServer";

    private Context ctx;
    private AssetManager assetManager;

    public AndroidHttpServer(int port, Context ctx) {
        super(port);
        this.ctx = ctx;
        init();
    }

    private void init(){
        assetManager = ctx.getAssets();
    }

    @Override
    public Response serve(String uri,
                          Method method,
                          Map<String, String> headers,
                          Map<String, String> parms,
                          Map<String, String> files) {

        WebMapping mapping = WebMappingSet.findMapping(uri);

        if(!TextUtils.isEmpty(mapping.getHtmlPath())){
            // html
            String htmlPath = mapping.getHtmlPath();
            return handleAsHtml(htmlPath);
        }else{
            // servlet
            Class<? extends AndroidHttpServlet> servletClass = mapping.getServletClass();
            try {
                //
                // ------ cookie;
                //

                AndroidHttpServlet androidHttpServlet = servletClass.newInstance();
                androidHttpServlet.injectContext(ctx);
                IAndroidServletRequest request = createRequest(uri, method, headers, parms, files);
                AndroidServletResponseImpl response = new AndroidServletResponseImpl();
                androidHttpServlet.doRequest(request,response);

                Response response1 = new Response(response.getStatus(), response.getMimeType(), response.toResponseString());
                Map<String, String> respHeader = response.getHeaders();
                for (Map.Entry<String, String> entry : respHeader.entrySet()) {
                    response1.addHeader(entry.getKey(),entry.getValue());
                }

                // 拼装cookies, Set-Cookie: key1=value1;key2=value2;...
                List<Cookie> cookies = response.getCookies();
                int size = cookies.size();
                StringBuilder cookieBuf = new StringBuilder();
                for (int i = 0; i < size; i++) {
                    Cookie cookie = cookies.get(i);
                    cookieBuf.append(cookie.getName()).append("=").append(cookie.getValue());
                    if(i != size-1){
                        cookieBuf.append("; ");
                    }
                }
                String cookiesStr = cookieBuf.toString();
                if(!TextUtils.isEmpty(cookiesStr)){
                    response1.addHeader("Set-Cookie",cookiesStr);
                }
                return response1;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return super.serve(uri, method, headers, parms, files);
    }

    private Response handleAsHtml(String htmlPath){
        try {

            InputStream in = assetManager.open(htmlPath, AssetManager.ACCESS_BUFFER);

            byte[] buffer = new byte[1024 * 1024];

            int temp = 0;
            int len = 0;
            while((temp=in.read())!=-1){
                buffer[len]=(byte)temp;
                len++;
            }
            in.close();
            return new NanoHTTPD.Response(new String(buffer,0,len));
        } catch (IOException ignored) {
        }
        return handleAsHtml(WebMappingSet.findMapping(WebMappingSet._404).getHtmlPath());
    }

    private IAndroidServletRequest createRequest(String uri,
                                                 Method method,
                                                 Map<String, String> headers,
                                                 Map<String, String> parms,
                                                 Map<String, String> files){
        AndroidServletRequestImpl request = new AndroidServletRequestImpl();

        if(headers != null){
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                Log.e(TAG, "createRequest: "+entry.getKey()+" -- "+entry.getValue());
                // 服务器向浏览器写回的时候是Set-Cookie, 但是浏览器请求的时候携带的是cookie
                // cookie -- testKey=testValue; lastTime=1519610074200
                if("cookie".equals(entry.getKey())){
                    String cookiesStr = entry.getValue();
                    if(!TextUtils.isEmpty(cookiesStr)){
                        String[] cookies = cookiesStr.split("; ");
                        for (String cookie:cookies){
                            try {
                                String[] split = cookie.split("=");
                                request.injectCookie(new Cookie(split[0],split[1]));
                                Log.e(TAG, "createRequest: "+cookie+" -- "+split[0]+" -- "+split[1]);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }else {
                    request.injectHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        if(parms != null){
            for (Map.Entry<String, String> entry : parms.entrySet()) {
                request.injectParamter(entry.getKey(),entry.getValue());
            }
        }

        request.injectReqUri(uri);
        request.injectReqMethod(method.name());

        // 注入cookie


        return request;
    }
}
