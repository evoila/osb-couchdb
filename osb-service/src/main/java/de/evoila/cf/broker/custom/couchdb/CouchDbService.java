/**
 *
 */
package de.evoila.cf.broker.custom.couchdb;

import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbProperties;

import java.net.UnknownHostException;
import java.util.List;

/**
 * @author Johannes Hiemer
 * @author Marco Di Martino
 * This class contains a specific implementation for the access to the
 * underlying service. For a database for example it contains the access
 * to the connection, the update and create commands etc.
 *
 * This class should not have any dependencies to Spring or other large
 * Frameworks but instead work with the Drivers directly against the native
 * API.
 */
public class CouchDbService {

	private boolean initialized;
	private CouchDbProperties config;

	private CouchDbClient couchDbClient;

	public void createConnection(String host, int port, String database, String  username, String  password) {
       	config = new CouchDbProperties();
        config.setDbName(database);
        config.setCreateDbIfNotExist(true);
        config.setProtocol("http");
        config.setHost(host);
        config.setPort(port);
        config.setUsername(username);
        config.setPassword(password);

		couchDbClient = new CouchDbClient(config);
		setInitialized(true);
	}

	public boolean isConnected() {
		return couchDbClient != null && this.initialized;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public CouchDbClient getCouchDbClient() {
		return couchDbClient;
	}

	public void setConfig(CouchDbProperties config) {
		this.config = config;
	}

	public CouchDbProperties getConfig() {
		return config;
	}


}
