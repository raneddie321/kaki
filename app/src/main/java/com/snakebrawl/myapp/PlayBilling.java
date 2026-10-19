package com.snakebrawl.myapp;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Play Billing for the coin packs, written against the Play Billing Library (versions 7 and
 * 8) through reflection. The library is only in the app when build.sh finds its AAR in libs/;
 * without it {@link #start()} reports "not available" and the game hides the coin store.
 *
 * Flow: connect, query the coin products (for local prices), launch a purchase when the player
 * taps a pack, grant the coins once Google Play reports it PURCHASED, then consume it so it can be
 * bought again. Unconsumed purchases (app killed mid-purchase) are finished on the next start.
 *
 * Plain Java on purpose (context and activity are Objects), so the desktop tests can run it
 * against stand-in billing classes.
 */
final class PlayBilling {
    /** Results go back to the game. All calls arrive on the thread the library uses (the UI thread). */
    interface Listener {
        void onAvailable(boolean available);

        void onPrice(String productId, String formattedPrice);

        /**
         * A purchase completed. Grant {@code quantity} units of the product unless this purchase
         * token was granted before (keep granted tokens saved), and return true once it is saved.
         */
        boolean grantOnce(String purchaseToken, String productId, int quantity);

        void onPurchaseFailed(String productId, boolean cancelled);

        /** Whether a subscription is active on this Google account (checked on start and after a purchase). */
        void onSubscription(String productId, boolean active);
    }

    private static final String P = "com.android.billingclient.api.";
    private static final int OK = 0, USER_CANCELED = 1, ITEM_ALREADY_OWNED = 7;
    private static final int STATE_PURCHASED = 1;

    private final Object context, activity;
    private final String[] productIds, subscriptionIds;
    private final Listener listener;
    private Object client;
    private final List<Object> details = new ArrayList<Object>();
    private final List<Object> subDetails = new ArrayList<Object>();
    private boolean ready;
    private String pendingProduct;

    PlayBilling(Object context, Object activity, String[] productIds, String[] subscriptionIds, Listener listener) {
        this.context = context;
        this.activity = activity;
        this.productIds = productIds;
        this.subscriptionIds = subscriptionIds;
        this.listener = listener;
    }

    static boolean libraryPresent() {
        try {
            Class.forName(P + "BillingClient");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Connects to Google Play. Safe to call when the library is missing. */
    void start() {
        if (!libraryPresent()) {
            listener.onAvailable(false);
            return;
        }
        try {
            Class<?> bc = Class.forName(P + "BillingClient");
            Object builder = bc.getMethod("newBuilder", Class.forName("android.content.Context")).invoke(null, context);
            Object purchasesListener = proxy(P + "PurchasesUpdatedListener", new InvocationHandler() {
                @Override
                public Object invoke(Object o, Method m, Object[] a) {
                    if (m.getName().equals("onPurchasesUpdated")) onPurchasesUpdated(a[0], a[1]);
                    return defaultFor(m, o, a);
                }
            });
            builder = call(builder, "setListener", purchasesListener);
            // Pending purchases (e.g. pay in a shop later) are required for one-time products
            Object pp = call(Class.forName(P + "PendingPurchasesParams").getMethod("newBuilder").invoke(null), "enableOneTimeProducts");
            builder = call(builder, "enablePendingPurchases", call(pp, "build"));
            try {
                builder = call(builder, "enableAutoServiceReconnection"); // Billing Library 8+
            } catch (Exception ignored) {
                // older library: reconnect by hand below
            }
            client = call(builder, "build");
            connect();
        } catch (Throwable t) {
            listener.onAvailable(false);
        }
    }

    private void connect() throws Exception {
        Object stateListener = proxy(P + "BillingClientStateListener", new InvocationHandler() {
            @Override
            public Object invoke(Object o, Method m, Object[] a) {
                if (m.getName().equals("onBillingSetupFinished")) {
                    if (code(a[0]) == OK) {
                        queryProducts();
                        querySubscriptions();
                        restorePurchases();
                        checkSubscriptions();
                    } else {
                        listener.onAvailable(false);
                    }
                } else if (m.getName().equals("onBillingServiceDisconnected")) {
                    ready = false;
                }
                return defaultFor(m, o, a);
            }
        });
        call(client, "startConnection", stateListener);
    }

    private void queryProducts() {
        try {
            Class<?> prodCls = Class.forName(P + "QueryProductDetailsParams$Product");
            List<Object> products = new ArrayList<Object>();
            for (String id : productIds) {
                Object b = prodCls.getMethod("newBuilder").invoke(null);
                b = call(b, "setProductId", id);
                b = call(b, "setProductType", "inapp");
                products.add(call(b, "build"));
            }
            Object qb = Class.forName(P + "QueryProductDetailsParams").getMethod("newBuilder").invoke(null);
            qb = callTyped(qb, "setProductList", List.class, products);
            Object params = call(qb, "build");
            Object responseListener = proxy(P + "ProductDetailsResponseListener", new InvocationHandler() {
                @Override
                public Object invoke(Object o, Method m, Object[] a) {
                    if (m.getName().equals("onProductDetailsResponse")) onProductDetails(a[0], a[1]);
                    return defaultFor(m, o, a);
                }
            });
            call(client, "queryProductDetailsAsync", params, responseListener);
        } catch (Throwable t) {
            listener.onAvailable(false);
        }
    }

    @SuppressWarnings("unchecked")
    private void onProductDetails(Object result, Object payload) {
        try {
            if (code(result) != OK) {
                listener.onAvailable(false);
                return;
            }
            // Billing Library 7 passes a List; 8 passes a QueryProductDetailsResult
            List<Object> list = payload instanceof List ? (List<Object>) payload : (List<Object>) call(payload, "getProductDetailsList");
            details.clear();
            if (list != null) details.addAll(list);
            for (Object d : details) {
                String id = (String) call(d, "getProductId");
                Object offer = call(d, "getOneTimePurchaseOfferDetails");
                if (offer != null) listener.onPrice(id, (String) call(offer, "getFormattedPrice"));
            }
            ready = !details.isEmpty();
            listener.onAvailable(ready);
        } catch (Throwable t) {
            listener.onAvailable(false);
        }
    }

    /** Opens Google Play's purchase sheet. Returns false when billing isn't ready. */
    boolean launch(String productId) {
        try {
            Object pd = null;
            String offerToken = null;
            for (Object d : details) if (productId.equals(call(d, "getProductId"))) pd = d;
            if (pd == null) {
                for (Object d : subDetails) if (productId.equals(call(d, "getProductId"))) pd = d;
                if (pd == null) return false;
                offerToken = subscriptionOffer(pd);
                if (offerToken == null) return false;
            }
            Object pdpb = Class.forName(P + "BillingFlowParams$ProductDetailsParams").getMethod("newBuilder").invoke(null);
            pdpb = call(pdpb, "setProductDetails", pd);
            if (offerToken != null) pdpb = call(pdpb, "setOfferToken", offerToken);
            Object pdp = call(pdpb, "build");
            Object fb = Class.forName(P + "BillingFlowParams").getMethod("newBuilder").invoke(null);
            fb = callTyped(fb, "setProductDetailsParamsList", List.class, Collections.singletonList(pdp));
            Object flowParams = call(fb, "build");
            pendingProduct = productId;
            Object result = callTyped2(client, "launchBillingFlow", Class.forName("android.app.Activity"), activity,
                    Class.forName(P + "BillingFlowParams"), flowParams);
            return code(result) == OK;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void onPurchasesUpdated(Object result, Object purchases) {
        int c;
        try {
            c = code(result);
        } catch (Throwable t) {
            return;
        }
        if (c == OK && purchases != null) {
            for (Object p : (List<Object>) purchases) handlePurchase(p);
        } else if (c == ITEM_ALREADY_OWNED) {
            restorePurchases(); // an earlier purchase was never consumed: finish it now
            checkSubscriptions();
        } else if (pendingProduct != null) {
            listener.onPurchaseFailed(pendingProduct, c == USER_CANCELED);
        }
        pendingProduct = null;
    }

    /** Grants and consumes one completed purchase. Pending ones are finished when they complete. */
    @SuppressWarnings("unchecked")
    private void handlePurchase(final Object purchase) {
        try {
            int state = (Integer) call(purchase, "getPurchaseState");
            if (state != STATE_PURCHASED) return;
            final List<String> products = (List<String>) call(purchase, "getProducts");
            final int quantity = Math.max(1, (Integer) call(purchase, "getQuantity"));
            String token = (String) call(purchase, "getPurchaseToken");
            if (isSubscription(products)) {
                for (String id : products) listener.onSubscription(id, true);
                if (!(Boolean) call(purchase, "isAcknowledged")) acknowledge(token);
                return;
            }
            // Grant first (saved with the token, so a retry can't grant twice), then consume
            for (String id : products) {
                if (!listener.grantOnce(token, id, quantity)) return;
            }
            Object cb = Class.forName(P + "ConsumeParams").getMethod("newBuilder").invoke(null);
            cb = call(cb, "setPurchaseToken", token);
            Object consumeListener = proxy(P + "ConsumeResponseListener", new InvocationHandler() {
                @Override
                public Object invoke(Object o, Method m, Object[] a) {
                    return defaultFor(m, o, a); // a failed consume is retried by restorePurchases()
                }
            });
            call(client, "consumeAsync", call(cb, "build"), consumeListener);
        } catch (Throwable ignored) {
            // leave it unconsumed; restorePurchases() retries on the next start
        }
    }

    /** Finishes purchases that were paid but not consumed (for example the app closed mid-purchase). */
    private void restorePurchases() {
        try {
            Object qb = Class.forName(P + "QueryPurchasesParams").getMethod("newBuilder").invoke(null);
            qb = call(qb, "setProductType", "inapp");
            Object responseListener = proxy(P + "PurchasesResponseListener", new InvocationHandler() {
                @Override
                @SuppressWarnings("unchecked")
                public Object invoke(Object o, Method m, Object[] a) {
                    if (m.getName().equals("onQueryPurchasesResponse") && code(a[0]) == OK && a[1] != null) {
                        for (Object p : (List<Object>) a[1]) handlePurchase(p);
                    }
                    return defaultFor(m, o, a);
                }
            });
            call(client, "queryPurchasesAsync", call(qb, "build"), responseListener);
        } catch (Throwable ignored) {
            // nothing to restore
        }
    }

    private boolean isSubscription(List<String> products) {
        for (String id : products) for (String s : subscriptionIds) if (s.equals(id)) return true;
        return false;
    }

    private void querySubscriptions() {
        if (subscriptionIds.length == 0) return;
        try {
            Class<?> prodCls = Class.forName(P + "QueryProductDetailsParams$Product");
            List<Object> products = new ArrayList<Object>();
            for (String id : subscriptionIds) {
                Object b = prodCls.getMethod("newBuilder").invoke(null);
                b = call(b, "setProductId", id);
                b = call(b, "setProductType", "subs");
                products.add(call(b, "build"));
            }
            Object qb = Class.forName(P + "QueryProductDetailsParams").getMethod("newBuilder").invoke(null);
            qb = callTyped(qb, "setProductList", List.class, products);
            Object responseListener = proxy(P + "ProductDetailsResponseListener", new InvocationHandler() {
                @Override
                @SuppressWarnings("unchecked")
                public Object invoke(Object o, Method m, Object[] a) {
                    if (m.getName().equals("onProductDetailsResponse") && code(a[0]) == OK) {
                        try {
                            List<Object> list = a[1] instanceof List ? (List<Object>) a[1] : (List<Object>) call(a[1], "getProductDetailsList");
                            subDetails.clear();
                            if (list != null) subDetails.addAll(list);
                            for (Object d : subDetails) {
                                String price = subscriptionPrice(d);
                                if (price != null) listener.onPrice((String) call(d, "getProductId"), price);
                            }
                        } catch (Throwable ignored) {
                            // no subscription for sale
                        }
                    }
                    return defaultFor(m, o, a);
                }
            });
            call(client, "queryProductDetailsAsync", call(qb, "build"), responseListener);
        } catch (Throwable ignored) {
            // subscriptions unavailable; coin packs still work
        }
    }

    /** The offer to buy: a free-trial / intro offer if Google Play has one for this player, else the base plan. */
    @SuppressWarnings("unchecked")
    private static Object firstOffer(Object pd) throws Exception {
        List<Object> offers = (List<Object>) call(pd, "getSubscriptionOfferDetails");
        if (offers == null || offers.isEmpty()) return null;
        for (Object offer : offers) if (call(offer, "getOfferId") != null) return offer;
        return offers.get(0);
    }

    private static String subscriptionOffer(Object pd) throws Exception {
        Object offer = firstOffer(pd);
        return offer == null ? null : (String) call(offer, "getOfferToken");
    }

    /** The recurring price (the last pricing phase, after any trial). */
    @SuppressWarnings("unchecked")
    private static String subscriptionPrice(Object pd) throws Exception {
        List<Object> offers = (List<Object>) call(pd, "getSubscriptionOfferDetails");
        if (offers == null || offers.isEmpty()) return null;
        List<Object> phases = (List<Object>) call(call(offers.get(0), "getPricingPhases"), "getPricingPhaseList");
        if (phases == null || phases.isEmpty()) return null;
        return (String) call(phases.get(phases.size() - 1), "getFormattedPrice");
    }

    private void acknowledge(String token) {
        try {
            Object ab = Class.forName(P + "AcknowledgePurchaseParams").getMethod("newBuilder").invoke(null);
            ab = call(ab, "setPurchaseToken", token);
            Object ackListener = proxy(P + "AcknowledgePurchaseResponseListener", new InvocationHandler() {
                @Override
                public Object invoke(Object o, Method m, Object[] a) {
                    return defaultFor(m, o, a); // not acknowledged: retried by checkSubscriptions() next start
                }
            });
            call(client, "acknowledgePurchase", call(ab, "build"), ackListener);
        } catch (Throwable ignored) {
            // retried on the next start
        }
    }

    /** Asks Google Play which subscriptions are active now (expired or cancelled ones are not listed). */
    private void checkSubscriptions() {
        if (subscriptionIds.length == 0) return;
        try {
            Object qb = Class.forName(P + "QueryPurchasesParams").getMethod("newBuilder").invoke(null);
            qb = call(qb, "setProductType", "subs");
            Object responseListener = proxy(P + "PurchasesResponseListener", new InvocationHandler() {
                @Override
                @SuppressWarnings("unchecked")
                public Object invoke(Object o, Method m, Object[] a) {
                    if (m.getName().equals("onQueryPurchasesResponse") && code(a[0]) == OK) {
                        List<String> active = new ArrayList<String>();
                        if (a[1] != null) {
                            for (Object p : (List<Object>) a[1]) {
                                try {
                                    if ((Integer) call(p, "getPurchaseState") != STATE_PURCHASED) continue;
                                    active.addAll((List<String>) call(p, "getProducts"));
                                } catch (Throwable ignored) {
                                    // skip it
                                }
                                handlePurchase(p);
                            }
                        }
                        for (String id : subscriptionIds) listener.onSubscription(id, active.contains(id));
                    }
                    return defaultFor(m, o, a);
                }
            });
            call(client, "queryPurchasesAsync", call(qb, "build"), responseListener);
        } catch (Throwable ignored) {
            // keep the last known state
        }
    }

    /** Re-checks subscriptions (for example when the game comes back to the front). */
    void refreshSubscriptions() {
        if (client != null) checkSubscriptions();
    }

    void destroy() {
        try {
            if (client != null) call(client, "endConnection");
        } catch (Throwable ignored) {
            // already closed
        }
    }

    // ------------------------------------------------------------------ reflection helpers

    private static int code(Object billingResult) {
        try {
            return (Integer) call(billingResult, "getResponseCode");
        } catch (Exception e) {
            return -1;
        }
    }

    private static Object proxy(String iface, InvocationHandler h) throws ClassNotFoundException {
        Class<?> c = Class.forName(iface);
        return Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[]{c}, h);
    }

    /** Handles Object methods on proxies so they behave sensibly in logs and collections. */
    private static Object defaultFor(Method m, Object self, Object[] a) {
        String n = m.getName();
        if (n.equals("hashCode")) return System.identityHashCode(self);
        if (n.equals("equals")) return a != null && a.length == 1 && a[0] == self;
        if (n.equals("toString")) return "PlayBillingListener";
        return null;
    }

    /** Calls a public method by name, matching arguments by assignability. */
    static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
            Class<?>[] pt = m.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < pt.length && fits; i++) fits = args[i] == null || box(pt[i]).isInstance(args[i]);
            if (fits) return accessible(m).invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }

    private static Object callTyped(Object target, String name, Class<?> type, Object arg) throws Exception {
        return accessible(target.getClass().getMethod(name, type)).invoke(target, arg);
    }

    private static Object callTyped2(Object target, String name, Class<?> t1, Object a1, Class<?> t2, Object a2) throws Exception {
        return accessible(target.getClass().getMethod(name, t1, t2)).invoke(target, a1, a2);
    }

    /**
     * The same method as declared by a public class. The library's objects are often of hidden
     * classes (BillingClient is really a package-private BillingClientImpl), whose methods can't be
     * invoked from outside their package even when public; the public parent's declaration can.
     */
    private static Method accessible(Method m) {
        if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) return m;
        for (Class<?> c = m.getDeclaringClass().getSuperclass(); c != null; c = c.getSuperclass()) {
            if (!Modifier.isPublic(c.getModifiers())) continue;
            try {
                return c.getMethod(m.getName(), m.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // not declared here; keep looking
            }
        }
        for (Class<?> i : m.getDeclaringClass().getInterfaces()) {
            if (!Modifier.isPublic(i.getModifiers())) continue;
            try {
                return i.getMethod(m.getName(), m.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // not in this interface
            }
        }
        m.setAccessible(true);
        return m;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        if (c == long.class) return Long.class;
        return c;
    }
}
