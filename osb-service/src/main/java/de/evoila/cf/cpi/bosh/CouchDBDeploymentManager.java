package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;

import java.util.HashMap;
import java.util.Map;

public class CouchDBDeploymentManager extends DeploymentManager {

    public static final String INSTANCE_GROUP = "couchdb";

    private RandomString randomStringUsername = new RandomString(10);

    private RandomString randomStringPassword = new RandomString(15);

    public CouchDBDeploymentManager(BoshProperties boshProperties) {
        super(boshProperties);
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan, Map<String, Object> customParameters) {
        HashMap<String, Object> properties = new HashMap<>();
        if (customParameters != null && !customParameters.isEmpty())
            properties.putAll(customParameters);

        log.debug("Updating Deployment Manifest, replacing parameters");

        Map<String, Object> manifestProperties = manifest.getInstanceGroups()
                .stream()
                .filter(i -> i.getName().equals(INSTANCE_GROUP))
                .findAny().get().getProperties();

        String username = randomStringUsername.nextString();
        String password = randomStringPassword.nextString();

        Map<String, Object> auth = (Map<String, Object>) manifestProperties.get("auth");
        auth.put("username", username);
        auth.put("password", password);

        serviceInstance.setUsername(username);
        serviceInstance.setPassword(password);

        this.updateInstanceGroupConfiguration(manifest, plan);
    }
}
