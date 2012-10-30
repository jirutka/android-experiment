/*
 * Copyright (c) 2012 Jakub Jirutka <jakub@jirutka.cz>
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package cz.cvut.jirutjak.fastimport.droid.utils;

import android.util.Log;
import java.io.IOException;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.http.conn.ssl.SSLSocketFactory;

/**
 * Allows you to trust certificates from additional KeyStores in addition to the
 * default KeyStore
 * 
 * @author Michael Burton <m1@niskala.org>
 * @see http://stackoverflow.com/questions/2642777/trusting-all-certificates-using-httpclient-over-https/6378872#6378872
 */
public class AdditionalKeyStoresSSLSocketFactory extends SSLSocketFactory {
    
    private static final String TAG = AdditionalKeyStoresSSLSocketFactory.class.getSimpleName();

    protected SSLContext sslContext = SSLContext.getInstance("TLS");
    

    public AdditionalKeyStoresSSLSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        super(null, null, null, null, null, null);
        sslContext.init(null, new TrustManager[]{new AdditionalKeyStoresTrustManager(keyStore)}, null);
    }

    
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
        return sslContext.getSocketFactory().createSocket();
    }

    
    
    /**
     * Based on
     * http://download.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html#X509TrustManager
     */
    private static class AdditionalKeyStoresTrustManager implements X509TrustManager {

        protected ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();
        

        protected AdditionalKeyStoresTrustManager(KeyStore... additionalkeyStores) {
            final ArrayList<TrustManagerFactory> factories = new ArrayList<TrustManagerFactory>();

            try {
                // The default Trustmanager with default keystore
                final TrustManagerFactory original = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                original.init((KeyStore) null);
                factories.add(original);

                for (KeyStore keyStore : additionalkeyStores) {
                    Log.d(TAG, "Creating TrustManagerFactory with additional certificate key store");
                    final TrustManagerFactory additionalCerts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    additionalCerts.init(keyStore);
                    factories.add(additionalCerts);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            /*
             * Iterate over the returned trustmanagers, and hold on to any that
             * are X509TrustManagers
             */
            for (TrustManagerFactory tmf : factories) {
                for (TrustManager tm : tmf.getTrustManagers()) {
                    if (tm instanceof X509TrustManager) {
                        x509TrustManagers.add((X509TrustManager) tm);
                    }
                }
            }

            if (x509TrustManagers.isEmpty()) {
                throw new RuntimeException("Couldn't find any X509TrustManagers");
            }

        }

        /*
         * Delegate to the default trust manager.
         */
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            final X509TrustManager defaultX509TrustManager = x509TrustManagers.get(0);
            defaultX509TrustManager.checkClientTrusted(chain, authType);
        }

        /*
         * Loop over the trustmanagers until we find one that accepts our server
         */
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {           
            for (X509TrustManager tm : x509TrustManagers) {
                try {
                    tm.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    // ignore
                }
            }
            throw new CertificateException();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            final ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
            for (X509TrustManager tm : x509TrustManagers) {
                list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
            }
            return list.toArray(new X509Certificate[list.size()]);
        }
    }
}