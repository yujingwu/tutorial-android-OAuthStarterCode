/**
 * Sample AppAuth implementation for Logging into ARTIK Cloud.
 * https://github.com/openid/AppAuth-Android
 *
 * IMPORTANT:  This sample application, for demonstration purposes embeds the client
 * secret to perform a 'code' exchange.  The code exchange should occur on your
 * protected server protecting your client secret.
 *
 * Please follow IETF best practice for Oauth2.0
 * https://tools.ietf.org/html/rfc7636#section-4.4
 *
 */
package cloud.artik.example.oauth;

import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.TokenResponse;

import java.util.HashMap;
import java.util.Map;

import static cloud.artik.example.oauth.AuthHelper.INTENT_ARTIKCLOUD_AUTHORIZATION_RESPONSE;
import static cloud.artik.example.oauth.AuthHelper.USED_INTENT;


public class LoginActivity extends AppCompatActivity {

    public static String accessToken = "";
    public static String refreshToken = "";
    public static String expiresIn = "";

    Button buttonSignIn;

    public static final String LOG_TAG = "LoginActivity";

    AuthorizationService authorizationService;
    AuthStateDAL authStateDAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LoginActivity.LOG_TAG, "Entering onCreate ...");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authorizationService = new AuthorizationService(this);
        authStateDAL = new AuthStateDAL(this);
        buttonSignIn = (Button) findViewById(R.id.btnSignIn);
        buttonSignIn.setOnClickListener(new Button.OnClickListener() {

            @Override
            public void onClick(View view) {
                doAuth();
            }

        });

    }

    // File OAuth call with Authorization Code method
    // https://developer.artik.cloud/documentation/getting-started/authentication.html#authorization-code-method
    private void doAuth() {
        AuthorizationRequest authorizationRequest = AuthHelper.createAuthorizationRequest();

        PendingIntent authorizationIntent = PendingIntent.getActivity(
                this,
                authorizationRequest.hashCode(),
                new Intent(INTENT_ARTIKCLOUD_AUTHORIZATION_RESPONSE, null, this, LoginActivity.class),
                0);

                /* request sample with custom tabs */
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();

        authorizationService.performAuthorizationRequest(authorizationRequest, authorizationIntent, customTabsIntent);

    }

    @Override
    protected void onStart() {
        Log.d(LoginActivity.LOG_TAG, "Entering onStart ...");
        super.onStart();
        checkIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        checkIntent(intent);
    }

    private void checkIntent(@Nullable Intent intent) {

        Log.d(LoginActivity.LOG_TAG, "Entering checkIntent ...");
        if (intent != null) {
            String action = intent.getAction();
            switch (action) {
                case INTENT_ARTIKCLOUD_AUTHORIZATION_RESPONSE:
                    Log.d(LoginActivity.LOG_TAG, "checkIntent action = " + action
                            + " intent.hasExtra(USED_INTENT) = " + intent.hasExtra(USED_INTENT));
                    if (!intent.hasExtra(USED_INTENT)) {
                        handleAuthorizationResponse(intent);
                        intent.putExtra(USED_INTENT, true);
                    }
                    break;
                default:
                    Log.w(LoginActivity.LOG_TAG, "checkIntent action = " + action);
                    // do nothing
            }
        } else {
            Log.w(LoginActivity.LOG_TAG, "checkIntent intent is null!");
        }
    }

    private void handleAuthorizationResponse(@NonNull Intent intent) {

        AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
        AuthorizationException error = AuthorizationException.fromIntent(intent);
        Log.i(LoginActivity.LOG_TAG, "Entering handleAuthorizationResponse with response from Intent = " + response.jsonSerialize().toString());

        if (response != null) {

            if (response.authorizationCode != null ) { // Authorization Code method: succeeded to get code

                final AuthState authState = new AuthState(response, error);
                Log.i(LoginActivity.LOG_TAG, "Received code = " + response.authorizationCode + "\n make another call to get token ...");

                Map<String, String> params = new HashMap<String, String>();
                params.put("client_secret", Config.CLIENT_SECRET);

                // file 2nd call in Authorization Code method to get the token
                authorizationService.performTokenRequest(response.createTokenExchangeRequest(params), new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(@Nullable TokenResponse tokenResponse, @Nullable AuthorizationException exception) {
                        if (tokenResponse != null) {
                            authState.update(tokenResponse, exception);
                            authStateDAL.writeAuthState(authState);
                            String text = String.format("Token Response [ Access Token: %s ]", tokenResponse.accessToken);
                            Log.i(LoginActivity.LOG_TAG, text);
                            accessToken = tokenResponse.accessToken;
                            expiresIn = tokenResponse.accessTokenExpirationTime.toString();
                            refreshToken = tokenResponse.refreshToken;
                            showAuthInfo();
                        } else {
                            Context context = getApplicationContext();
                            Log.w(LoginActivity.LOG_TAG, "Token Exchange failed", exception);
                            CharSequence text = "Token Exchange failed";
                            int duration = Toast.LENGTH_LONG;
                            Toast toast = Toast.makeText(context, text, duration);
                            toast.show();
                        }
                    }
                });
            } else { // come here w/o authorization code. For example, signup finish and user clicks "Back to login"
                Log.d(LoginActivity.LOG_TAG, "additionalParameter = " + response.additionalParameters.toString());

                if (response.additionalParameters.get("status").equalsIgnoreCase("login_request")) {
                    // ARTIK Cloud instructs the app to display a sign-in form
                    doAuth();
                } else {
                    Log.d(LoginActivity.LOG_TAG, response.jsonSerialize().toString());
                }
            }

        } else {
            Log.w(LoginActivity.LOG_TAG, "Authorization Response is null ");
            Log.d(LoginActivity.LOG_TAG, "Authorization Exception = " + error);
        }
    }

    public void showAuthInfo() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("accessToken = " + LoginActivity.accessToken + "\n" + "refreshToken = " + LoginActivity.refreshToken + "\n" + "expiresIn = " + LoginActivity.expiresIn + "\n").setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                }).show();


    }
}

