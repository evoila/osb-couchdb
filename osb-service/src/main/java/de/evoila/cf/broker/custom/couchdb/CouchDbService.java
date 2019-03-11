/**
 *
 */
package de.evoila.cf.broker.custom.couchdb;

import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.catalog.ServerAddress;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbProperties;

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

    private ServerAddress serverAddress;

	private CouchDbClient couchDbClient;

	public void createConnection(String username, String password, String database, List<ServerAddress> serverAddresses) throws PlatformException {
		this.serverAddress = serverAddresses.get(0);

		config = new CouchDbProperties();
		config.setDbName(database);
		config.setCreateDbIfNotExist(false);
		config.setProtocol("http");
		config.setHost(serverAddress.getIp());
		config.setPort(serverAddress.getPort());
		config.setUsername(username);
		config.setPassword(password);
		try {
			couchDbClient = new CouchDbClient(config);
		} catch (Exception e) {
			throw new PlatformException("connection am arsch");
		}
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
