package edu.puc.firebasetest.app.model;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

/**
 * Created by jose on 10/20/15.
 */
public class FirebaseModel {
    public static final String USERS = "users";
    public static final String CONTACTS = "contacts";
    public static final String NAME = "name";
    public static final String ENABLED = "enabled";
    public static final String TOKEN = "token";

    public static final String ROOMS = "rooms";
    public static final String PARTICIPANTS = "participants";
    public static final String MESSAGES = "messages";

    private static String sAccountName;
    private static String sUsername;

    private static void loadAccount(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        Account[] accounts = accountManager.getAccountsByType("com.google");
        String accountName;
        if (accounts.length > 0) {
            accountName = accounts[0].name;
        } else {
            accountName = null;
        }

        sAccountName = accountName;
        sUsername = escapeCharacters(accountName);
    }

    public static String getUser(Context context) {
        if (sUsername == null) {
            loadAccount(context);
        }

        return sUsername;
    }

    public static String getAccountName(Context context) {
        if (sAccountName == null) {
            loadAccount(context);
        }

        return sAccountName;
    }

    public static String escapeCharacters(String path) {
        return path.replaceAll("\\.|\\#|\\$|\\[|\\]", "-");
    }
}
