package de.evoila.cf.broker.custom.couchdb;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import de.evoila.cf.broker.custom.couchdb.NamesAndRoles;

/**
 * @author Marco Di Martino
 */

public class SecurityDocument {

    @Expose
    @SerializedName("admins")
    private NamesAndRoles admins;

    @Expose
    @SerializedName("members")
    private NamesAndRoles members;

    public SecurityDocument(NamesAndRoles admins, NamesAndRoles members) {
        setAdmins(admins);
        setMembers(members);
    }

    public SecurityDocument(NamesAndRoles admins) {
        setAdmins(admins);
    }

    public NamesAndRoles getAdmins() {
        return admins;
    }

    public void setAdmins(NamesAndRoles admins) {
        this.admins = admins;
    }

    public NamesAndRoles getMembers() {
        return members;
    }

    public void setMembers(NamesAndRoles members) {
        this.members = members;
    }



}