/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trivialdrivesample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.example.android.trivialdrivesample.util.IabBroadcastReceiver;
import com.example.android.trivialdrivesample.util.IabHelper;
import com.example.android.trivialdrivesample.util.IabResult;
import com.example.android.trivialdrivesample.util.Inventory;
import com.example.android.trivialdrivesample.util.Purchase;

/**
 * Example game using in-app billing version 3.
 * <p>
 * Before attempting to run this sample, please read the README file. It
 * contains important information on how to set up this project.
 * <p>
 * All the game-specific logic is implemented here in MainActivity, while the
 * general-purpose boilerplate that can be reused in any app is provided in the
 * classes in the util/ subdirectory. When implementing your own application,
 * you can copy over util/*.java to make use of those utility classes.
 * <p>
 * This game is a simple "driving" game where the player can buy gas
 * and drive. The car has a tank which stores gas. When the player purchases
 * gas, the tank fills up (1/4 tank at a time). When the player drives, the gas
 * in the tank diminishes (also 1/4 tank at a time).
 * <p>
 * The user can also purchase a "premium upgrade" that gives them a red car
 * instead of the standard blue one (exciting!).
 * <p>
 * The user can also purchase a subscription ("infinite gas") that allows them
 * to drive without using up any gas while that subscription is active.
 * <p>
 * It's important to note the consumption mechanics for each item.
 * <p>
 * PREMIUM: the item is purchased and NEVER consumed. So, after the original
 * purchase, the player will always own that item. The application knows to
 * display the red car instead of the blue one because it queries whether
 * the premium "item" is owned or not.
 * <p>
 * INFINITE GAS: this is a subscription, and subscriptions can't be consumed.
 * <p>
 * GAS: when gas is purchased, the "gas" item is then owned. We consume it
 * when we apply that item's effects to our app's world, which to us means
 * filling up 1/4 of the tank. This happens immediately after purchase!
 * It's at this point (and not when the user drives) that the "gas"
 * item is CONSUMED. Consumption should always happen when your game
 * world was safely updated to apply the effect of the purchase. So,
 * in an example scenario:
 * <p>
 * BEFORE:      tank at 1/2
 * ON PURCHASE: tank at 1/2, "gas" item is owned
 * IMMEDIATELY: "gas" is consumed, tank goes to 3/4
 * AFTER:       tank at 3/4, "gas" item NOT owned any more
 * <p>
 * Another important point to notice is that it may so happen that
 * the application crashed (or anything else happened) after the user
 * purchased the "gas" item, but before it was consumed. That's why,
 * on startup, we check if we own the "gas" item, and, if so,
 * we have to apply its effects to our world and consume it. This
 * is also very important!
 */
public class MainActivity extends Activity implements IabBroadcastReceiver.IabBroadcastListener {
  // Debug tag, for logging
  static final String TAG = "TrivialDrive";
  // SKUs for our products: the premium upgrade (non-consumable) and gas (consumable)
  static final String SKU_PREMIUM = "skuHugeGas12200";
  static final String SKU_GAS = "skuGas2020Test";
  // (arbitrary) request code for the purchase flow
  static final int RC_REQUEST = 10001;
  // How many units (1/4 tank is our unit) fill in the tank.
  static final int TANK_MAX = 4;
  // Graphics for the gas gauge
  static int[] TANK_RES_IDS = {R.drawable.gas0, R.drawable.gas1, R.drawable.gas2,
    R.drawable.gas3, R.drawable.gas4};
  // Does the user have the premium upgrade?
  boolean mIsPremium = false;
  // Current amount of gas in tank, in units
  int mTank;

  // The helper object
  IabHelper mHelper;

  // Provides purchase notification while this app is running
  IabBroadcastReceiver mBroadcastReceiver;

  // Called when consumption is complete
  IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
    public void onConsumeFinished(Purchase purchase, IabResult result) {
      Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

      // if we were disposed of in the meantime, quit.
      if (mHelper == null) return;

      // We know this is the "gas" sku because it's the only one we consume,
      // so we don't check which sku was consumed. If you have more than one
      // sku, you probably should check...
      if (result.isSuccess()) {
        // successfully consumed, so we apply the effects of the item in our
        // game world's logic, which in our case means filling the gas tank a bit
        Log.d(TAG, "Consumption successful. Provisioning.");
        mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
        saveData();
        alert("You filled the tank. Your tank is now " + String.valueOf(mTank) + "/4 full!");
      } else {
        complain("Error while consuming: " + result);
      }
      updateUi();
      setWaitScreen(false);
      Log.d(TAG, "End consumption flow.");
    }
  };

  // Listener that's called when we finish querying the items and subscriptions we own
  IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
    public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
      Log.d(TAG, "Query inventory finished.");

      // Have we been disposed of in the meantime? If so, quit.
      if (mHelper == null) return;

      // Is it a failure?
      if (result.isFailure()) {
        complain("Failed to query inventory: " + result);
        return;
      }

      Log.d(TAG, "Query inventory was successful.");

      /*
       * Check for items we own. Notice that for each purchase, we check
       * the developer payload to see if it's correct! See
       * verifyDeveloperPayload().
       */

      // Do we have the premium upgrade?
      Purchase premiumPurchase = inventory.getPurchase(SKU_PREMIUM);
      mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
      Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

      // Check for gas delivery -- if we own gas, we should fill up the tank immediately
      Purchase gasPurchase = inventory.getPurchase(SKU_GAS);
      if (gasPurchase != null && verifyDeveloperPayload(gasPurchase)) {
        // It seems you have some items in the inventory objects even about subscription items
        Log.d(TAG, "We have gas. Consuming it.");
        try {
          mHelper.consumeAsync(inventory.getPurchase(SKU_GAS), mConsumeFinishedListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
          complain("Error consuming gas. Another async operation in progress.");
        }
        return;
      }

      updateUi();
      setWaitScreen(false);
      Log.d(TAG, "Initial inventory query finished; enabling main UI.");
    }
  };
  // Callback for when a purchase is finished
  IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
    public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
      Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

      // if we were disposed of in the meantime, quit.
      if (mHelper == null) return;

      if (result.isFailure()) {
        complain("Error purchasing: " + result);
        setWaitScreen(false);
        return;
      }
      if (!verifyDeveloperPayload(purchase)) {
        complain("Error purchasing. Authenticity verification failed.");
        setWaitScreen(false);
        return;
      }

      Log.d(TAG, "Purchase successful.");
      if (purchase.getSku().equals(SKU_GAS)) {
        // bought 1/4 tank of gas. So consume it.
        Log.d(TAG, "Purchase is gas. Starting gas consumption. > > " + purchase);
        try {
          mHelper.consumeAsync(purchase, mConsumeFinishedListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
          complain("Error consuming gas. Another async operation in progress.");
          setWaitScreen(false);
          return;
        }
      } else if (purchase.getSku().equals(SKU_PREMIUM)) {
        // bought the premium upgrade!
        Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
        alert("Thank you for upgrading to premium!");
        mIsPremium = true;
        updateUi();
        setWaitScreen(false);
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    // load game data
    loadData();

    /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
     * (that you got from the Google Play developer console). This is not your
     * developer public key, it's the *app-specific* public key.
     *
     * Instead of just storing the entire literal string here embedded in the
     * program,  construct the key at runtime from pieces or
     * use bit manipulation (for example, XOR with some other string) to hide
     * the actual key.  The key itself is not secret information, but we don't
     * want to make it easy for an attacker to replace the public key with one
     * of their own and then fake messages from the server.
     */
    String base64EncodedPublicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDMbwTACStPKI2X1k0TeVUcw64ccJKGlYqQcGGF19FLiUBD2NB6xugpZrKISTZYvozeRg98vea0MzfPj9l92sEuPoZq/37cU7+LyviIWRvWdjm6Vz4tkxLQwhy7l87wVcYn/QkMteGfY34u+M/bV6WUA8TnhZiVk0fw301k7xXbwQIDAQAB";

    // Some sanity checks to see if the developer (that's you!) really followed the
    // instructions to run this sample (don't put these checks on your app!)
        /*if (base64EncodedPublicKey.contains("CONSTRUCT_YOUR")) {
            throw new RuntimeException("Please put your app's public key in MainActivity.java. See README.");
        }
        if (getPackageName().startsWith("com.example")) {
            throw new RuntimeException("Please change the sample's package name! See README.");
        }*/

    // Create the helper, passing it our context and the public key to verify signatures with
    Log.d(TAG, "Creating IAB helper.");
    mHelper = new IabHelper(this, base64EncodedPublicKey);

    // enable debug logging (for a production application, you should set this to false).
    mHelper.enableDebugLogging(true);
    // Start setup. This is asynchronous and the specified listener
    // will be called once setup completes.
    Log.d(TAG, "Starting setup.");
    mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
      public void onIabSetupFinished(IabResult result) {
        Log.d(TAG, "Setup finished.");
        Log.d(TAG, "result value:[" + result.toString() + "]");
        // Just in case we're not able to find the Hope Application on the device, so definitely we're gonna do that forcibly :|
        if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE) {
          showDownloadDialog();
          return;
        } else if (!result.isSuccess()) {
          // Oh noes, there was a problem.
          complain("Problem setting up in-app billing: " + result);
          return;
        } else if (result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_OK) {
          Log.d(TAG, "User has subscription");
        }

        // Have we been disposed of in the meantime? If so, quit.
        if (mHelper == null) return;

        // Important: Dynamically register for broadcast messages about updated purchases.
        // We register the receiver here instead of as a <receiver> in the Manifest
        // because we always call getPurchases() at startup, so therefore we can ignore
        // any broadcasts sent while the app isn't running.
        // Note: registering this listener in an Activity is a bad idea, but is done here
        // because this is a SAMPLE. Regardless, the receiver must be registered after
        // IabHelper is setup, but before first call to getPurchases().
        mBroadcastReceiver = new IabBroadcastReceiver(MainActivity.this);
        IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
        registerReceiver(mBroadcastReceiver, broadcastFilter);

        // IAB is fully set up. Now, let's get an inventory of stuff we own.
        Log.d(TAG, "Setup successful. Querying inventory.");
        try {
          mHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
          complain("Error querying inventory. Another async operation in progress.");
        }
      }
    });

  }

  /**
   * To show a dialog with download button & the URL of the latest release of Hope application.
   */
  private void showDownloadDialog() {
    new Builder(MainActivity.this)
      .setTitle("هپ")
      .setMessage("برای استفاده از تمام امکانات، باید اپلیکیشن هپ را دانلود و نصب نمایید.")
      .setPositiveButton("دانلود",
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            startActivity(
              new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://bmi.ir/landing/hope")));
          }
        })
      .create()
      .show()
    ;
  }

  @Override
  public void receivedBroadcast() {
    // Received a broadcast notification that the inventory of items has changed
    Log.d(TAG, "Received broadcast notification. Querying inventory.");
    try {
      mHelper.queryInventoryAsync(mGotInventoryListener);
    } catch (IabHelper.IabAsyncInProgressException e) {
      complain("Error querying inventory. Another async operation in progress.");
    }
  }

  @NonNull
  private String getPayloadString() {
    return "Sample New Developer Payload at : " + System.nanoTime();
  }

  // User clicked the "Upgrade to Premium" button.
  public void onUpgradeAppButtonClicked(View arg0) {

    try {
      mHelper.launchPurchaseFlow(this, SKU_GAS, RC_REQUEST, mPurchaseFinishedListener, "somePayload");
    } catch (IabHelper.IabAsyncInProgressException e) {
      complain("Error launching purchase flow. Another async operation in progress.");
      setWaitScreen(false);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
    if (mHelper == null) return;

    // Pass on the activity result to the helper for handling
    if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
      // not handled, so handle it ourselves (here's where you'd
      // perform any handling of activity results not related to in-app
      // billing...
      super.onActivityResult(requestCode, resultCode, data);
    } else {
      Log.d(TAG, "onActivityResult handled by IABUtil.");
    }
  }

  /**
   * Verifies the developer payload of a purchase.
   */
  boolean verifyDeveloperPayload(Purchase p) {
    String payload = p.getDeveloperPayload();
    Log.d(TAG, "verifyDeveloperPayload: >>>>>> payload : " + payload);
    /*
     * TODO: verify that the developer payload of the purchase is correct. It will be
     * the same one that you sent when initiating the purchase.
     *
     * WARNING: Locally generating a random string when starting a purchase and
     * verifying it here might seem like a good approach, but this will fail in the
     * case where the user purchases an item on one device and then uses your app on
     * a different device, because on the other device you will not have access to the
     * random string you originally generated.
     *
     * So a good developer payload has these characteristics:
     *
     * 1. If two different users purchase an item, the payload is different between them,
     *    so that one user's purchase can't be replayed to another user.
     *
     * 2. The payload must be such that you can verify it even when the app wasn't the
     *    one who initiated the purchase flow (so that items purchased by the user on
     *    one device work on other devices owned by the user).
     *
     * Using your own server to store and verify developer payloads across app
     * installations is recommended.
     */

    return true;
  }


  // We're being destroyed. It's important to dispose of the helper here!
  @Override
  public void onDestroy() {
    super.onDestroy();

    try {
      // very important:
      if (mBroadcastReceiver != null) {
        unregisterReceiver(mBroadcastReceiver);
      }

      // very important:
      if (mHelper != null) {
        mHelper.disposeWhenFinished();
        mHelper = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // updates UI to reflect model
  public void updateUi() {
    // update the car color to reflect premium status or lack thereof
    ((ImageView) findViewById(R.id.free_or_premium)).setImageResource(mIsPremium ? R.drawable.premium : R.drawable.free);

    // "Upgrade" button is only visible if the user is not premium
    findViewById(R.id.upgrade_button).setVisibility(mIsPremium ? View.GONE : View.VISIBLE);

    // update gas gauge to reflect tank status
    int index = mTank >= TANK_RES_IDS.length ? TANK_RES_IDS.length - 1 : mTank;
    ((ImageView) findViewById(R.id.gas_gauge)).setImageResource(TANK_RES_IDS[index]);
  }

  // Enables or disables the "please wait" screen.
  void setWaitScreen(boolean set) {
    findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
    findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
  }

  void complain(String message) {
    Log.e(TAG, "**** TrivialDrive Error: " + message);
    alert("Error: " + message);
  }

  void alert(String message) {
    AlertDialog.Builder bld = new AlertDialog.Builder(this);
    bld.setMessage(message);
    bld.setNeutralButton("OK", null);
    Log.d(TAG, "Showing alert dialog: " + message);
    bld.create().show();
  }

  void saveData() {

    /*
     * WARNING: on a real application, we recommend you save data in a secure way to
     * prevent tampering. For simplicity in this sample, we simply store the data using a
     * SharedPreferences.
     */

    SharedPreferences.Editor spe = getPreferences(MODE_PRIVATE).edit();
    spe.putInt("tank", mTank);
    spe.apply();
    Log.d(TAG, "Saved data: tank = " + String.valueOf(mTank));
  }

  void loadData() {
    SharedPreferences sp = getPreferences(MODE_PRIVATE);
    mTank = sp.getInt("tank", 2);
    Log.d(TAG, "Loaded data: tank = " + String.valueOf(mTank));
  }


}
