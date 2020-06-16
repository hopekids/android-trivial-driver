/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trivialdrivesample.util;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app billing purchase.
 */
public class Purchase {
  String mItemType;  // ITEM_TYPE_INAPP or ITEM_TYPE_SUBS
  String mOrderId;
  String mPackageName;
  String mSku;
  long mPurchaseTime;
  int mPurchaseState;
  String mDeveloperPayload;
  String mToken;
  String mOriginalJson;
  String mSignature;
  boolean isSubscription;
  boolean mIsAutoRenewing;
  private String startTimeOfSession;

  public Purchase(String itemType, String jsonPurchaseInfo, String signature) throws JSONException {
    mItemType = itemType;
    mOriginalJson = jsonPurchaseInfo;
    JSONObject o = new JSONObject(mOriginalJson);
    mOrderId = o.optString("orderId");
    mPackageName = o.optString("packageName");
    mSku = o.optString("productId");
    mPurchaseTime = o.optLong("purchaseTime");
    mPurchaseState = o.optInt("purchaseState");
    mDeveloperPayload = o.optString("developerPayload");
    mToken = o.optString("token", o.optString("purchaseToken"));
    mIsAutoRenewing = o.optBoolean("autoRenewing");
    isSubscription = o.optBoolean("isSubscription");
    startTimeOfSession = o.optString("startTime");
    mSignature = signature;

  }


  public final String getItemType() {
    return mItemType;
  }

  public final String getOrderId() {
    return mOrderId;
  }

  public final String getPackageName() {
    return mPackageName;
  }

  public final String getSku() {
    return mSku;
  }

  public final long getPurchaseTime() {
    return mPurchaseTime;
  }

  public final int getPurchaseState() {
    return mPurchaseState;
  }

  public final String getDeveloperPayload() {
    return mDeveloperPayload;
  }

  public final String getToken() {
    return mToken;
  }

  public final String getOriginalJson() {
    return mOriginalJson;
  }

  public final String getSignature() {
    return mSignature;
  }

  public final boolean isAutoRenewing() {
    return mIsAutoRenewing;
  }

  final boolean isSubscription() {
    return isSubscription;
  }

  public final String getStartTimeOfSession() {
    return startTimeOfSession;
  }

  @Override
  public String toString() {
    return "PurchaseInfo(type:" + mItemType + "):" + mOriginalJson;
  }
}