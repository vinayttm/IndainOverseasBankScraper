package com.example.IndainOverseasBankScraper.Services;

import static com.example.IndainOverseasBankScraper.Utils.AccessibilityUtil.*;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.example.IndainOverseasBankScraper.MainActivity;
import com.example.IndainOverseasBankScraper.Repository.QueryUPIStatus;
import com.example.IndainOverseasBankScraper.Repository.SaveBankTransaction;
import com.example.IndainOverseasBankScraper.Repository.UpdateDateForScrapper;
import com.example.IndainOverseasBankScraper.Utils.AES;
import com.example.IndainOverseasBankScraper.Utils.CaptureTicker;
import com.example.IndainOverseasBankScraper.Utils.Config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IOBRecorderService extends AccessibilityService {

    int appNotOpenCounter = 0;
    final CaptureTicker ticker = new CaptureTicker(this::processTickerEvent);

    boolean isTransaction = false;
    boolean isLogin = false;

    @Override
    protected void onServiceConnected() {
        ticker.startChecking();
        super.onServiceConnected();
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }


    private void processTickerEvent() {
        Log.d("Ticker", "Processing Event");
        Log.d("Flags", printAllFlags());
        ticker.setNotIdle();
        if (!MainActivity.isAccessibilityServiceEnabled(this, this.getClass())) {
            return;
        }

        AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
        if (rootNode != null) {
            if (findNodeByPackageName(rootNode, Config.packageName) == null) {
                if (appNotOpenCounter > 4) {
                    Log.d("App Status", "Not Found");
                    relaunchApp();
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    appNotOpenCounter = 0;
                    return;
                }
                appNotOpenCounter++;
            } else {
                Log.d("App Status", "Found");
                rootNode.refresh();
                checkForSessionExpiry();
                listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
                enterPin();
                viewStatements();
                transactions();
                numberOfTransaction();
                backingProcess();
                readTransactions();
                rootNode.refresh();
            }
            rootNode.recycle();
        }
    }


    private void relaunchApp() {
        if (MainActivity.isAccessibilityServiceEnabled(this, this.getClass())) {
            new QueryUPIStatus(() -> {
                Intent intent = getPackageManager().getLaunchIntentForPackage(Config.packageName);
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }, () -> {
                Toast.makeText(this, "Scrapper inactive", Toast.LENGTH_SHORT).show();
            }).evaluate();
        }
    }


    private void enterPin() {
        String loginPin = Config.loginPin;
        if (!loginPin.isEmpty())
            if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Enter App Passcode")) {
                AccessibilityNodeInfo editTextNode = findNodeByClassName(getTopMostParentNode(getRootInActiveWindow()), "android.widget.EditText");
                if (editTextNode != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, loginPin);
                    editTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    AccessibilityNodeInfo loginButton = findOneText(getTopMostParentNode(getRootInActiveWindow()), "Login");
                    if (loginButton != null) {
                        Rect outBounds = new Rect();
                        loginButton.getBoundsInScreen(outBounds);
                        performTap(outBounds.centerX(), outBounds.centerY(), 100);
                        editTextNode.recycle();
                    }
                }
            }
    }

    private void viewStatements() {
        AccessibilityNodeInfo PayBills = findOneText(getTopMostParentNode(getRootInActiveWindow()), "Pay Bills");
        if (PayBills == null) {
            AccessibilityNodeInfo viewStatements = findOneText(getTopMostParentNode(getRootInActiveWindow()), "View Statements");
            if (viewStatements != null) {
                Rect outBounds = new Rect();
                viewStatements.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY(), 100);
                viewStatements.recycle();
                ticker.setNotIdle();
            }
        }
    }


    private AccessibilityNodeInfo findSecondTransactionsNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.getText() != null && node.getText().toString().equals("TRANSACTIONS")) {
            if (node.isClickable()) {
                if (node.getParent() != null) {
                    int count = 0;
                    for (int i = 0; i < node.getParent().getChildCount(); i++) {
                        AccessibilityNodeInfo siblingNode = node.getParent().getChild(i);
                        if (siblingNode != null && "TRANSACTIONS".contentEquals(siblingNode.getText())) {
                            count++;
                            if (count == 2) {
                                return siblingNode;
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            AccessibilityNodeInfo result = findSecondTransactionsNode(childNode);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private void transactions() {
        AccessibilityNodeInfo transactions = findSecondTransactionsNode(getTopMostParentNode(getRootInActiveWindow()));
        if (transactions != null) {
            checkForSessionExpiry();
            Rect outBounds = new Rect();
            transactions.getBoundsInScreen(outBounds);
            performTap(outBounds.centerX(), outBounds.centerY(), 100);

        }
    }

    private void numberOfTransaction() {
        AccessibilityNodeInfo numberOfTransaction = findOneText(getTopMostParentNode(getRootInActiveWindow()), "No. of Transaction");
        if (numberOfTransaction != null) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            checkForSessionExpiry();
            AccessibilityNodeInfo editTextNode = findNodeByClassName(getTopMostParentNode(getRootInActiveWindow()), "android.widget.EditText");
            if (editTextNode != null) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, Config.transactionNumber.isEmpty() ? "15" : Config.transactionNumber);
                editTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                numberOfTransaction.recycle();
                editTextNode.refresh();
                AccessibilityNodeInfo View = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "View", true, false);
                if (View != null) {
                    Rect outBounds = new Rect();
                    View.getBoundsInScreen(outBounds);
                    performTap(outBounds.centerX(), outBounds.centerY(), 100);
                    View.recycle();
                }
            }
        }
    }

    private void backingProcess() {
        ticker.setNotIdle();
        AccessibilityNodeInfo description = findOneText(getTopMostParentNode(getRootInActiveWindow()), "Description");
        if (description != null) {
            if (isTransaction) {
                AccessibilityNodeInfo secondButtonNode = findFirstButtonNode(getTopMostParentNode(getRootInActiveWindow()));
                if (secondButtonNode != null) {
                    Rect outBounds = new Rect();
                    secondButtonNode.getBoundsInScreen(outBounds);
                    boolean isClicked = performTap(outBounds.centerX(), outBounds.centerY(), 100);
                    if (isClicked) {
                        isTransaction = false;
                        secondButtonNode.recycle();
                        description.recycle();
                    }
                }

            }
        }
    }

    private AccessibilityNodeInfo findFirstButtonNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if ("android.widget.Button".equals(node.getClassName())) {
            if (node.isClickable()) {
                if (node.getParent() != null) {
                    for (int i = 0; i < node.getParent().getChildCount(); i++) {
                        AccessibilityNodeInfo siblingNode = node.getParent().getChild(i);
                        if (siblingNode != null && "android.widget.Button".equals(siblingNode.getClassName())) {
                            return siblingNode;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            AccessibilityNodeInfo result = findFirstButtonNode(childNode);
            if (result != null) {
                return result;
            }
        }

        return null;
    }


    private String printAllFlags() {
        StringBuilder result = new StringBuilder();
        // Get the fields of the class
        Field[] fields = getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            try {
                Object value = field.get(this);
                result.append(fieldName).append(": ").append(value).append("\n");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    public boolean performTap(int x, int y, int duration) {
        Log.d("Accessibility", "Tapping " + x + " and " + y);
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(p, 0, duration));
        GestureDescription gestureDescription = gestureBuilder.build();
        boolean dispatchResult = false;
        dispatchResult = dispatchGesture(gestureDescription, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
        Log.d("Dispatch Result", String.valueOf(dispatchResult));
        return dispatchResult;
    }


    public static String getUPIId(String description) {
        try {
            if (!description.contains("@"))
                return "";
            String[] split = description.split("/");
            String value = null;
            value = Arrays.stream(split).filter(x -> x.contains("@")).findFirst().orElse(null);
            return value != null ? value : "";
        } catch (Exception ex) {
            Log.d("Exception", ex.getMessage());
            return "";
        }
    }

    private String extractUTRFromDesc(String description) {
        try {
            String[] split = description.split("/");
            String value = null;
            value = Arrays.stream(split).filter(x -> x.length() == 12).findFirst().orElse(null);
            if (value != null) {
                return value + " " + description;
            }
            return description;
        } catch (Exception e) {
            return description;
        }
    }

    int staringPoint = -1;

    public static List<String> removeDatePattern(List<String> list) {
        List<String> cleanedList = new ArrayList<>();
        for (String entry : list) {
            // Remove the pattern "(28-Feb-2024)" from each entry
            String cleanedEntry = entry.replaceAll("\\(\\d{2}-[a-zA-Z]{3}-\\d{4}\\)", "");
            cleanedList.add(cleanedEntry);
        }
        return cleanedList;
    }

    public void readTransactions() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ticker.setNotIdle();
        checkForSessionExpiry();
        JSONArray output = new JSONArray();
        List<String> allString = listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
        if (allString.contains("Description") && allString.contains("Date (Value Date)") && allString.contains("Balance (₹)") && allString.contains("Debit / Credit (₹)")) {
            for (int i = 0; i < allString.size(); i++) {
                if (allString.get(i).equals("Balance (₹)")) {
                    staringPoint = i;
                    System.out.println("Balance Index =  " + staringPoint);
                    break;
                }
            }
            List<String> unFilterList = allString.subList(staringPoint, allString.size());
            unFilterList.removeIf(String::isEmpty);
            unFilterList.remove(0);
            List<String> filterList = removeDatePattern(unFilterList);
            String amount = "";
            System.out.println("filterList " + filterList);
            for (int i = 0; i < filterList.size(); i += 4) {
                JSONObject entry = new JSONObject();
                String date = filterList.get(i);
                String description = filterList.get(i + 1);
                String totalBalance = filterList.get(i + 3);
                if (description.contains("UPI")) {
                    amount = filterList.get(i + 2);
                } else {
                    amount = filterList.get(i + 2);
                    amount = "-" + amount;
                }
                try {
                    entry.put("Amount", amount);
                    entry.put("RefNumber", extractUTRFromDesc(description));
                    entry.put("Description", extractUTRFromDesc(description));
                    entry.put("AccountBalance", totalBalance);
                    entry.put("CreatedDate", date.trim());
                    entry.put("BankName", Config.bankName + Config.bankLoginId);
                    entry.put("BankLoginId", Config.bankLoginId);
                    entry.put("UPIId", getUPIId(description));
                    output.put(entry);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            Log.d("Final Json Output", output.toString());
            Log.d("API BODY", output.toString());
            Log.d("API BODY Length", String.valueOf(output.length()));
            if (output.length() > 0) {
                Log.d("Final Json Output", output.toString());
                Log.d("API BODY", output.toString());
                Log.d("API BODY Length", String.valueOf(output.length()));
                JSONObject result = new JSONObject();
                try {
                    result.put("Result", AES.encrypt(output.toString()));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                new QueryUPIStatus(() -> {
                    new SaveBankTransaction(() -> {
                    }, () -> {

                    }).evaluate(result.toString());
                    new UpdateDateForScrapper().evaluate();
                }, () -> {
                }).evaluate();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                isTransaction = true;
            }
        }


    }


    public void checkForSessionExpiry() {
        ticker.setNotIdle();
        AccessibilityNodeInfo targetNode1 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Do you want to set / Generate mPIN to enable the fund transfer for mobile banking service ?", true, false);
        AccessibilityNodeInfo targetNode2 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "our session will expire in next 2 mins, press extent for session extension of another 5 mins or press cancel", true, false);
        AccessibilityNodeInfo targetNode3 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "End of active session time is reached, please do re-login with PIN to do more transactions", true, false);
        AccessibilityNodeInfo targetNode4 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "You are connected via Wifi network which may be unsecure. Kindly make sure of connection security before proceed", true, false);

        if (targetNode1 != null) {
            AccessibilityNodeInfo requestNode = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "CANCEL", true, false);
            if (requestNode != null) {
                Rect outBounds = new Rect();
                requestNode.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY(), 100);
                requestNode.recycle();
                targetNode1.recycle();
                ticker.setNotIdle();
            }
        }
        if (targetNode2 != null) {
            AccessibilityNodeInfo extend = findOneText(getTopMostParentNode(getRootInActiveWindow()), "EXTEND");
            if (extend != null) {
                Rect outBounds = new Rect();
                extend.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY(), 100);
                ticker.setNotIdle();
            }
        }
        if (targetNode3 != null) {
            AccessibilityNodeInfo ok = findOneText(getTopMostParentNode(getRootInActiveWindow()), "OK");
            if (ok != null) {
                Rect outBounds = new Rect();
                ok.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY(), 150);
                ticker.setNotIdle();
            }
        }
        if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Connection Timeout")) {
            AccessibilityNodeInfo ok = findOneText(getTopMostParentNode(getRootInActiveWindow()), "OK");
            if (ok != null) {
                Rect outBounds = new Rect();
                ok.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY(), 150);
                ticker.setNotIdle();
            }
        }
        if (targetNode4 != null) {
            AccessibilityNodeInfo ok = findOneText(getTopMostParentNode(getRootInActiveWindow()), "OK");
            if (ok != null) {
                Rect outBounds = new Rect();
                ok.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY(), 150);
                ticker.setNotIdle();
            }
        }
    }
}
