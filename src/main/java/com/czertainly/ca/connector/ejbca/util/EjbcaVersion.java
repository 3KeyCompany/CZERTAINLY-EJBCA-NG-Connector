package com.czertainly.ca.connector.ejbca.util;

public class EjbcaVersion {

    private int techVersion;
    private int majorVersion;
    private int minorVersion;
    private String version;

    public EjbcaVersion(String ejbcaVersion) {
        readVersionNumbers(ejbcaVersion);
    }

    private void readVersionNumbers(String ejbcaVersion) {
        String[] parts = ejbcaVersion.split(" ");
        String[] version = parts[1].split("\\.");

        this.techVersion = Integer.valueOf(version[0]);
        try { // if there is not major version number, defaults to 0
            this.majorVersion = Integer.valueOf(version[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            this.majorVersion = 0;
        }
        // TODO: is this really needed? do we need to work with minor version?
        try { // some EJBCA versions do not have a minor version number, defaults to 0
            this.minorVersion = Integer.valueOf(version[2]);
        } catch (ArrayIndexOutOfBoundsException e) {
            this.minorVersion = 0;
        }
        this.version = parts[2];
    }

    public int getTechVersion() {
        return techVersion;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "EJBCA " + techVersion + "." + majorVersion + "." + minorVersion + " " + version;
    }
}
