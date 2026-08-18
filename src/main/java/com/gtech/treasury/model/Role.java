package com.gtech.treasury.model;

/**
 * Bir rolü temsil eder. role tablosundaki bir satıra karşılık gelir.
 */
public class Role {

    private int roleId;
    private String roleName;
    private String roleDescription;
    private String roleType;      // USER / CUSTOMER

    public Role(int roleId, String roleName, String roleDescription, String roleType) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleDescription = roleDescription;
        this.roleType = roleType;
    }

    public int getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getRoleDescription() {
        return roleDescription;
    }

    public String getRoleType() {
        return roleType;
    }

    /** JList / JComboBox'ta güzel görünmesi için. */
    @Override
    public String toString() {
        return roleName + (roleDescription != null ? "  —  " + roleDescription : "");
    }
}
