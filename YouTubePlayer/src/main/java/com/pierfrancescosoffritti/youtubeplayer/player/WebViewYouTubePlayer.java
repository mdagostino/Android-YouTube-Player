package com.pierfrancescosoffritti.youtubeplayer.player;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.pierfrancescosoffritti.youtubeplayer.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * WebView implementing the actual YouTube Player
 */
class WebViewYouTubePlayer extends WebView implements YouTubePlayer {

    @NonNull private final Set<YouTubePlayerListener> youTubePlayerListeners;
    @NonNull private final Handler mainThreadHandler;

    private YouTubePlayerInitListener youTubePlayerInitListener;
    @Nullable private PlayerStateTracker playerStateTracker;

    protected WebViewYouTubePlayer(Context context) {
        this(context, null);
    }

    protected WebViewYouTubePlayer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    protected WebViewYouTubePlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mainThreadHandler = new Handler(Looper.getMainLooper());
        youTubePlayerListeners = new HashSet<>();
    }

    protected void initialize(@NonNull YouTubePlayerInitListener initListener) {
        youTubePlayerListeners.clear();

        youTubePlayerInitListener = initListener;

        playerStateTracker = new PlayerStateTracker();
        youTubePlayerListeners.add(playerStateTracker);

        initWebView();
    }

    protected void onYouTubeIframeAPIReady() {
        youTubePlayerInitListener.onInitSuccess(this);
    }

    @Override
    public void loadVideo(final String videoId, final float startSeconds) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:loadVideo('" +videoId +"', " +startSeconds +")");
            }
        });
    }

    @Override
    public void cueVideo(final String videoId, final float startSeconds) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:cueVideo('" +videoId +"', " +startSeconds +")");
            }
        });
    }

    @Override
    public void play() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:playVideo()");
            }
        });
    }

    @Override
    public void pause() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:pauseVideo()");
            }
        });
    }

    @Override
    public void mute() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:mute()");
            }
        });
    }

    @Override
    public void unMute() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:unMute()");
            }
        });
    }

    @Override
    public void seekTo(final int time) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                loadUrl("javascript:seekTo(" +time +")");
            }
        });
    }

    @Override
    @PlayerConstants.PlayerState.State
    public int getCurrentState() {
        if(playerStateTracker == null)
            throw new RuntimeException("Player not initialized.");

        return playerStateTracker.currentState;
    }

    @Override
    public void destroy() {
        mainThreadHandler.removeCallbacksAndMessages(null);
        super.destroy();
    }

    @NonNull
    protected Set<YouTubePlayerListener> getListeners() {
        return youTubePlayerListeners;
    }

    @Override
    public boolean addListener(YouTubePlayerListener listener) {
        return youTubePlayerListeners.add(listener);
    }

    @Override
    public boolean removeListener(YouTubePlayerListener listener) {
        return youTubePlayerListeners.remove(listener);
    }

    private void initWebView() {
        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMediaPlaybackRequiresUserGesture(false);
        this.addJavascriptInterface(new YouTubePlayerBridge(this), "YouTubePlayerBridge");
//        this.loadDataWithBaseURL("", readYouTubePlayerHTMLFromFile(), "text/html", "utf-8", "");
//        this.loadData(readYouTubePlayerHTMLFromFile(), "text/html; charset=utf-8", "UTF-8");
        this.loadDataWithBaseURL("https://www.youtube.com", readYouTubePlayerHTMLFromFile(), "text/html", "utf-8", null);
    }

    private String readYouTubePlayerHTMLFromFile() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.youtube_player);

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String read;
            StringBuilder sb = new StringBuilder();

            while ( ( read = bufferedReader.readLine() ) != null )
                sb.append(read).append("\n");
            inputStream.close();

            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Can't parse HTML file containing the player.");
        }
    }

    private class PlayerStateTracker extends AbstractYouTubePlayerListener {
        @PlayerConstants.PlayerState.State private int currentState;

        @Override
        public void onStateChange(@PlayerConstants.PlayerState.State int state) {
            this.currentState = state;
        }
    }

    private class SSLTolerentWebViewClient extends WebViewClient {
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            AlertDialog alertDialog = builder.create();
            String message = "SSL Certificate error.";
            switch (error.getPrimaryError()) {
                case SslError.SSL_UNTRUSTED:
                    message = "The certificate authority is not trusted.";
                    break;
                case SslError.SSL_EXPIRED:
                    message = "The certificate has expired.";
                    break;
                case SslError.SSL_IDMISMATCH:
                    message = "The certificate Hostname mismatch.";
                    break;
                case SslError.SSL_NOTYETVALID:
                    message = "The certificate is not yet valid.";
                    break;
            }

            message += " Do you want to continue anyway?";
            alertDialog.setTitle("SSL Certificate Error");
            alertDialog.setMessage(message);
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Ignore SSL certificate errors
                    handler.proceed();
                }
            });

            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    handler.cancel();
                }
            });
            alertDialog.show();
        }
    }
}
