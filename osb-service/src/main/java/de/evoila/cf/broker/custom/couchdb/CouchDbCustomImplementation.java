/**
 * 
 */
package de.evoila.cf.broker.custom.couchdb;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.evoila.cf.broker.bean.impl.ExistingEndpointBeanImpl;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.ServerAddress;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Johannes Hiemer
 * @author Marco Di Martino
 */

@Service
public class CouchDbCustomImplementation {

    private static final String APPLICATION_JSON = "application/json";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String PREFIX_ID = "org.couchdb.user:";

	private CouchDbService service;

	private static final Logger log = LoggerFactory.getLogger(CouchDbCustomImplementation.class);

	@Autowired
	private ExistingEndpointBeanImpl endpointBean;

	public CouchDbService connection(String username, String password, String database, List<ServerAddress> hosts) throws Exception {
		ServerAddress serverAddress = hosts.get(0);


		service = new CouchDbService();

		try{
            service.createConnection(serverAddress.getIp(), endpointBean.getPort(), database, username, password);
        }catch(CouchDbException e ){
            throw new ServiceBrokerException("Could not establish connection", e);
        }

		return service;
	}

	public static void bindRoleToDatabaseWithPassword(CouchDbService connection, String database,
					 String username, String password, boolean isAdmin, ArrayList<Object> adminClient) throws Exception {
		/*
		* if isAdmin is true, set new user in the _security as admin with "adminRole"
		* else set new user in the _security as member with "memberRole"
		* */
		String role = null;
		if (isAdmin){
			role = database + "_admin";
		}else{
			role = database + "_member";
		}

        //String match_role = database + "_admin";
		String id = PREFIX_ID+username;
        /*creation of the user in the _user database
		* only server admin can access this database */

        ArrayList<String> user_roles = new ArrayList<>();
        user_roles.add(role);

        UserDocument userDoc = new UserDocument(id, username, password, user_roles, "user");
        Gson gson = new Gson();
        JsonObject js = (JsonObject)gson.toJsonTree(userDoc);
        connection.getCouchDbClient().save(js);

		/* ** Security document **
		* limit access to the database only for the created user
		* Need to connect to the database as server admin to make changes to the _security document
		* Cannot retrieve admin password from the configuration client (connection.getConfig().getPassword()==null)
		* cannot retrieve endpointBean from static-context.
		* Need to have variables from parameters */

        JsonObject main = ((CouchDbClient) adminClient.get(0)).find(JsonObject.class, "_security");
        SecurityDocument sec_doc = null;

        if (main.size() == 0) {
			/* create document, there are no specific database users
			   at document creation, only a database admin is created
			   and no user as "member" is added (it will be part of the bindingService)
			 */
            ArrayList<String> admin_names = new ArrayList<>();
            admin_names.add(username);
            ArrayList<String> admin_roles = new ArrayList<>();
            admin_roles.add(role);

            String memberRole = database + "_member";
			ArrayList<String> member_roles = new ArrayList<>();
			member_roles.add(memberRole);
            NamesAndRoles adm = new NamesAndRoles(admin_names, admin_roles);
			NamesAndRoles mem = new NamesAndRoles(admin_names, member_roles);
         	sec_doc = new SecurityDocument(adm, mem);

        } else {
            //update document
            sec_doc = gson.fromJson(main, SecurityDocument.class);
            if (isAdmin){
				sec_doc.getAdmins().addName(username);
				sec_doc.getAdmins().getRoles().add(role);
			}else{
				sec_doc.getMembers().addName(username);
				sec_doc.getMembers().getRoles().add(role);
            }
        }
        JsonObject security = (JsonObject)gson.toJsonTree(sec_doc);

        send_put(connection, database, connection.getConfig().getUsername(),
                adminClient.get(1).toString(), security.toString());
    }

    /* must implement a Preemptive Basic Authentication */
	public static void send_put(CouchDbService connection, String database, String username,
                                   String password, String file_security) throws Exception {

		String host = connection.getConfig().getHost();
		String baseUri = connection.getCouchDbClient().getBaseUri().toString();
		String credentials = username+":"+password;
        String http = baseUri.substring(0,7);
		baseUri = baseUri.substring(7);

		String uri = http+credentials+"@"+baseUri+database+"/_security";


		HttpHost targetHost = new HttpHost(host, connection.getConfig().getPort(), "http");
		AuthCache authCache = new BasicAuthCache();
		authCache.put(targetHost, new BasicScheme());

		CredentialsProvider provider = new BasicCredentialsProvider();
		provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

		final HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(provider);
		context.setAuthCache(authCache);

		HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
		StringEntity params = new StringEntity(file_security, "UTF-8");
		HttpPut putRequest = new HttpPut(new URI(uri));

		params.setContentType(APPLICATION_JSON);
		putRequest.addHeader(CONTENT_TYPE,APPLICATION_JSON);
		putRequest.addHeader("Accept", APPLICATION_JSON);
		putRequest.setEntity(params);

		HttpResponse response = client.execute(putRequest, context);
        if (response.getStatusLine().getStatusCode() != 200){
            throw new Exception("Error while updating _security document");
        }
	}


	public void bindRoleToInstanceWithPassword(CouchDbService connection, String database,
			String username, String password) throws Exception {

			ArrayList<Object> adminClient = createAdminClient(database);
			this.bindRoleToDatabaseWithPassword((CouchDbService) connection, database, username, password, false, adminClient);

	}

	/* give access to the database "database" as server admin */
	private ArrayList<Object> createAdminClient (String database) throws Exception {

		CouchDbService client = new CouchDbService();
		client.createConnection(
				endpointBean.getHosts().get(0).getIp(),
				endpointBean.getPort(),
				database,
				endpointBean.getUsername(),
				endpointBean.getPassword()
		);

		return new ArrayList<Object>(){{
			add(client.getCouchDbClient());
			add(endpointBean.getPassword());
		}};
	}

    public CouchDbService getService() {
		return service;
	}
}